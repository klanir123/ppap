package com.msg.ppap.approval.application;

import com.msg.ppap.approval.application.ExcelPreview.ElementRow;
import com.msg.ppap.approval.application.ExcelPreview.HeaderCell;
import com.msg.ppap.approval.application.ExcelPreview.Message;
import com.msg.ppap.approval.application.ExcelPreview.RowStatus;
import com.msg.ppap.approval.application.port.out.FormTemplateRepository;
import com.msg.ppap.approval.application.port.out.FormWorkbookReader;
import com.msg.ppap.approval.config.FormImportProperties;
import com.msg.ppap.approval.domain.Actor;
import com.msg.ppap.approval.domain.ApprovalCase;
import com.msg.ppap.approval.domain.ApprovalErrors;
import com.msg.ppap.approval.domain.CaseAction;
import com.msg.ppap.approval.domain.CaseStateMachine;
import com.msg.ppap.approval.domain.ElementEvidence;
import com.msg.ppap.approval.domain.FormTemplate;
import com.msg.ppap.approval.domain.SelfAssessment;
import com.msg.ppap.approval.domain.Submission;
import com.msg.ppap.approval.domain.ValueValidator;
import com.msg.ppap.documents.api.Documents;
import com.msg.ppap.platform.domain.NotFoundException;
import com.msg.ppap.platform.domain.PpapException;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Excel form import (UC-05 A1): recognizes the template by its marker cell, previews header and element rows against
 * the draft and applies the selected rows; the original file is stored as document and attached to the cover sheet
 * element ({@code ppap.approval.form-import.attach-to-element}).
 */
@Service
@Transactional
public class ExcelImportService {

    public static final String UNKNOWN_FORM_TEMPLATE = "UNKNOWN_FORM_TEMPLATE";

    private final ApprovalContext context;
    private final FormTemplateRepository templates;
    private final FormWorkbookReader reader;
    private final Documents documents;
    private final SubmissionService submissions;
    private final ValueValidator validator;
    private final FormImportProperties properties;

    public ExcelImportService(
            ApprovalContext context,
            FormTemplateRepository templates,
            FormWorkbookReader reader,
            Documents documents,
            SubmissionService submissions,
            ValueValidator validator,
            FormImportProperties properties) {
        this.context = context;
        this.templates = templates;
        this.reader = reader;
        this.documents = documents;
        this.submissions = submissions;
        this.validator = validator;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public ExcelPreview preview(UUID caseId, byte[] content, String templateCode) {
        ApprovalCase c = context.load(caseId);
        checkEditable(c);
        return read(c, content, templateCode);
    }

    /** Applies the selected rows to the draft and attaches the original file. */
    public SubmissionView apply(
            UUID caseId,
            String fileName,
            byte[] content,
            String templateCode,
            ExcelPreview.Selection selection,
            Long expectedVersion) {
        Actor actor = context.actor();
        ApprovalCase c = context.load(caseId);
        c.checkVersion(expectedVersion);
        checkEditable(c);
        ExcelPreview preview = read(c, content, templateCode);
        Set<String> selected = new LinkedHashSet<>(selection.elementCodes());
        Submission draft = c.draftSubmission();
        for (ElementRow row : preview.elements()) {
            if (row.status() == RowStatus.ERROR || (!selected.isEmpty() && !selected.contains(row.code()))) {
                continue;
            }
            ElementEvidence current = draft.evidence(row.code()).orElse(null);
            boolean hasContent = current != null && (current.selfAssessment() != null || current.comment() != null);
            if (hasContent && !selection.overwrite()) {
                continue;
            }
            if (row.selfAssessment() == null && row.comment() == null) {
                continue;
            }
            c.updateEvidence(
                    row.code(),
                    row.selfAssessment() != null
                            ? row.selfAssessment()
                            : current == null ? null : current.selfAssessment(),
                    current == null ? null : current.values(),
                    row.comment() != null ? row.comment() : current == null ? null : current.comment(),
                    actor,
                    context.now());
        }
        String attachTo = properties.attachToElement();
        if (attachTo != null && c.request().element(attachTo).isPresent()) {
            var info = documents.upload(fileName, new ByteArrayInputStream(content));
            c.attachEvidenceDocument(attachTo, DocumentRefs.of(info), actor, context.now());
        }
        ApprovalCase saved = context.save(c);
        return submissions.view(saved, saved.draftSubmission());
    }

    ExcelPreview read(ApprovalCase c, byte[] content, String templateCode) {
        if (content == null || content.length == 0) {
            throw new PpapException(PpapException.Kind.INVALID, "EMPTY_FILE", "File is empty");
        }
        if (content.length > properties.maxFileSize().toBytes()) {
            throw new PpapException(
                    PpapException.Kind.TOO_LARGE,
                    "FILE_TOO_LARGE",
                    "Form exceeds the maximum size of " + properties.maxFileSize());
        }
        try (FormWorkbookReader.Workbook workbook = reader.open(content)) {
            FormTemplate template = template(workbook, templateCode);
            Map<String, HeaderCell> header = new LinkedHashMap<>();
            for (FormTemplate.HeaderField field : template.header()) {
                header.put(
                        field.field(),
                        new HeaderCell(workbook.cell(field.sheet(), field.cell()), field.sheet() + "!" + field.cell()));
            }
            List<Message> warnings = new ArrayList<>();
            compare(warnings, header, "supplierPartNumber", c.supplierPartNumber());
            compare(warnings, header, "customerPartNumber", c.customerPartNumber());
            return rows(c, workbook, template, header, warnings);
        }
    }

    private ExcelPreview rows(
            ApprovalCase c,
            FormWorkbookReader.Workbook workbook,
            FormTemplate template,
            Map<String, HeaderCell> header,
            List<Message> warnings) {
        FormTemplate.ElementTable table = template.elementTable();
        int last = table.lastRow() != null
                ? table.lastRow()
                : Math.min(workbook.lastRow(table.sheet()), table.firstRow() + properties.maxRows() - 1);
        List<ElementRow> rows = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int row = table.firstRow(); row <= last; row++) {
            String codeCell = table.cell(FormTemplate.ElementTable.CODE, row);
            String code = workbook.cell(table.sheet(), codeCell);
            if (code == null) {
                continue;
            }
            if (c.request().element(code).isEmpty()) {
                unknown.add(code);
                continue;
            }
            if (!seen.add(code)) {
                warnings.add(new Message("DUPLICATE_ELEMENT", "element " + code + " appears more than once"));
                continue;
            }
            rows.add(row(template, workbook, table, row, code, table.sheet() + "!" + codeCell));
        }
        return new ExcelPreview(template.code(), header, rows, unknown, warnings);
    }

    private static ElementRow row(
            FormTemplate template,
            FormWorkbookReader.Workbook workbook,
            FormTemplate.ElementTable table,
            int row,
            String code,
            String cell) {
        List<Message> messages = new ArrayList<>();
        String rawAssessment = cellOf(workbook, table, FormTemplate.ElementTable.SELF_ASSESSMENT, row);
        String comment = cellOf(workbook, table, FormTemplate.ElementTable.COMMENT, row);
        SelfAssessment assessment = null;
        RowStatus status = RowStatus.OK;
        try {
            assessment = template.selfAssessment(rawAssessment).orElse(null);
        } catch (IllegalArgumentException ex) {
            messages.add(new Message("UNKNOWN_SELF_ASSESSMENT", "unknown self assessment '" + rawAssessment + "'"));
            status = RowStatus.ERROR;
        }
        if (status != RowStatus.ERROR) {
            if (assessment == null) {
                messages.add(new Message("MISSING_SELF_ASSESSMENT", "no self assessment"));
                status = RowStatus.WARNING;
            } else if (assessment != SelfAssessment.OK && comment == null) {
                messages.add(new Message("COMMENT_REQUIRED", "comment required for " + assessment));
                status = RowStatus.WARNING;
            }
        }
        return new ElementRow(code, assessment, comment, null, cell, status, messages);
    }

    private static String cellOf(
            FormWorkbookReader.Workbook workbook, FormTemplate.ElementTable table, String column, int row) {
        String reference = table.cell(column, row);
        return reference == null ? null : workbook.cell(table.sheet(), reference);
    }

    private FormTemplate template(FormWorkbookReader.Workbook workbook, String templateCode) {
        if (templateCode != null && !templateCode.isBlank()) {
            return templates
                    .findByCode(templateCode.strip())
                    .orElseThrow(() -> new NotFoundException("Form template", templateCode));
        }
        return templates.findAll().stream()
                .filter(FormTemplate::active)
                .filter(t -> t.marker()
                        .matches(workbook.cell(t.marker().sheet(), t.marker().cell())))
                .findFirst()
                .orElseThrow(() -> ApprovalErrors.unprocessable(
                        UNKNOWN_FORM_TEMPLATE, "The workbook does not match any form template"));
    }

    private static void compare(List<Message> warnings, Map<String, HeaderCell> header, String field, String expected) {
        HeaderCell cell = header.get(field);
        if (cell != null && cell.value() != null && expected != null && !Objects.equals(cell.value(), expected)) {
            warnings.add(new Message(
                    "PART_NUMBER_MISMATCH",
                    field + " in the form (" + cell.value() + ") differs from the case (" + expected + ")"));
        }
    }

    private void checkEditable(ApprovalCase c) {
        CaseStateMachine.check(
                CaseAction.EDIT_SUBMISSION, c.ownRole(), c.source(), c.status(), context.actor(), context.policy());
    }
}
