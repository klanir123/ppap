package com.msg.ppap.masterdata.application.imports;

import com.msg.ppap.masterdata.application.ChangeAction;
import com.msg.ppap.masterdata.application.PartCommand;
import com.msg.ppap.masterdata.application.PartMappingCommand;
import com.msg.ppap.masterdata.application.PartMappingService;
import com.msg.ppap.masterdata.application.PartService;
import com.msg.ppap.masterdata.application.PartnerCommand;
import com.msg.ppap.masterdata.application.PartnerService;
import com.msg.ppap.masterdata.application.PlanResult;
import com.msg.ppap.masterdata.application.RelationCommand;
import com.msg.ppap.masterdata.application.RelationService;
import com.msg.ppap.masterdata.application.SiteCommand;
import com.msg.ppap.masterdata.application.SiteService;
import com.msg.ppap.masterdata.application.port.out.Table;
import com.msg.ppap.masterdata.application.port.out.TabularFileReader;
import com.msg.ppap.masterdata.config.ImportProperties;
import com.msg.ppap.masterdata.domain.Relation;
import com.msg.ppap.platform.domain.DuplicateKeyException;
import com.msg.ppap.platform.domain.FieldViolation;
import com.msg.ppap.platform.domain.NotFoundException;
import com.msg.ppap.platform.domain.PpapException;
import com.msg.ppap.platform.domain.Validation;
import com.msg.ppap.platform.domain.ValidationException;
import com.msg.ppap.platform.domain.ViolationCodes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Two-step, stateless master data import (UC-01 A1): {@link #preview} validates without writing, {@link #execute}
 * upserts all rows by business key in one transaction (all-or-nothing unless {@code skipErrors}). Rows are
 * validated and applied through the same application services as the REST API.
 */
@Service
public class ImportService {

    private final TabularFileReader reader;
    private final ColumnMapper columnMapper;
    private final ImportProperties properties;
    private final PartService parts;
    private final PartnerService partners;
    private final SiteService sites;
    private final RelationService relations;
    private final PartMappingService mappings;

    public ImportService(
            TabularFileReader reader,
            ColumnMapper columnMapper,
            ImportProperties properties,
            PartService parts,
            PartnerService partners,
            SiteService sites,
            RelationService relations,
            PartMappingService mappings) {
        this.reader = reader;
        this.columnMapper = columnMapper;
        this.properties = properties;
        this.parts = parts;
        this.partners = partners;
        this.sites = sites;
        this.relations = relations;
        this.mappings = mappings;
    }

    /** Validates the file and shows what an import would do. Never writes. */
    @Transactional(readOnly = true)
    public ImportPreview preview(ImportEntity entity, String fileName, byte[] content, Map<String, String> mapping) {
        Evaluation evaluation = evaluate(entity, fileName, content, mapping);
        List<ImportRow> rows = evaluation.rows();
        int ok = count(rows, RowStatus.OK);
        int warnings = count(rows, RowStatus.WARNING);
        int errors = count(rows, RowStatus.ERROR);
        var summary = new ImportSummary(
                rows.size(),
                ok,
                warnings,
                errors,
                countAction(rows, ChangeAction.CREATED),
                countAction(rows, ChangeAction.UPDATED),
                countAction(rows, ChangeAction.UNCHANGED));
        return new ImportPreview(
                entity,
                fileName,
                evaluation.table().sheet(),
                evaluation.table().delimiter(),
                evaluation.table().columns(),
                evaluation.mapping(),
                rows.stream().limit(properties.previewRows()).toList(),
                summary);
    }

    /**
     * Imports the file.
     *
     * @param skipErrors import valid rows even if other rows fail
     * @throws ImportFailedException if rows fail and {@code skipErrors} is not set (nothing is imported)
     */
    @Transactional
    public ImportResult execute(
            ImportEntity entity, String fileName, byte[] content, Map<String, String> mapping, boolean skipErrors) {
        Evaluation evaluation = evaluate(entity, fileName, content, mapping);
        List<ImportFailure> failures = new ArrayList<>();
        evaluation.rows().stream()
                .filter(row -> row.status() == RowStatus.ERROR)
                .forEach(row -> failures.add(new ImportFailure(row.rowNumber(), errorsOf(row))));
        if (!failures.isEmpty() && !skipErrors) {
            throw new ImportFailedException(new ImportResult(entity.pathValue(), 0, 0, 0, failures.size(), failures));
        }
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        for (ImportRow row : evaluation.rows()) {
            if (row.status() == RowStatus.ERROR) {
                continue;
            }
            try {
                switch (apply(entity, row.values())) {
                    case CREATED -> created++;
                    case UPDATED -> updated++;
                    case UNCHANGED -> unchanged++;
                }
            } catch (PpapException ex) {
                failures.add(new ImportFailure(row.rowNumber(), messagesOf(ex)));
                if (!skipErrors) {
                    throw new ImportFailedException(
                            new ImportResult(entity.pathValue(), 0, 0, 0, failures.size(), failures));
                }
            }
        }
        failures.sort((a, b) -> Integer.compare(a.rowNumber(), b.rowNumber()));
        return new ImportResult(entity.pathValue(), created, updated, unchanged, failures.size(), failures);
    }

    /** All rows with errors (for the CSV error report), plus the file's columns. */
    @Transactional(readOnly = true)
    public ErrorReport errorReport(ImportEntity entity, String fileName, byte[] content, Map<String, String> mapping) {
        Evaluation evaluation = evaluate(entity, fileName, content, mapping);
        return new ErrorReport(
                evaluation.table().columns(),
                evaluation.rows().stream()
                        .filter(row -> row.status() == RowStatus.ERROR)
                        .toList());
    }

    /**
     * Erroneous rows of an import file.
     *
     * @param columns original columns
     * @param rows rows with status {@code ERROR}
     */
    public record ErrorReport(List<String> columns, List<ImportRow> rows) {}

    private Evaluation evaluate(ImportEntity entity, String fileName, byte[] content, Map<String, String> mapping) {
        if (content.length > properties.maxFileSize().toBytes()) {
            throw new PpapException(
                    PpapException.Kind.TOO_LARGE,
                    "FILE_TOO_LARGE",
                    "File exceeds " + properties.maxFileSize().toMegabytes() + " MB");
        }
        Table table = reader.read(fileName, content, entity.sheetNames(), properties.maxRows());
        Map<String, String> effective = mapping == null
                ? columnMapper.suggest(entity, table.columns())
                : checkMapping(entity, table.columns(), mapping);
        List<ImportRow> rows = new ArrayList<>(table.rows().size());
        Map<String, Integer> seenKeys = new HashMap<>();
        Map<String, Integer> seenSecondaryKeys = new HashMap<>();
        for (Table.Row row : table.rows()) {
            Map<String, String> values = values(table.columns(), row.cells(), effective);
            List<ImportMessage> messages = new ArrayList<>();
            ChangeAction action = null;
            boolean error = checkDuplicate(entity, values, row.rowNumber(), seenKeys, seenSecondaryKeys, messages);
            if (!error) {
                try {
                    PlanResult plan = plan(entity, values);
                    action = plan.action();
                    plan.warnings().forEach(w -> messages.add(toMessage(w)));
                } catch (PpapException ex) {
                    messages.addAll(messagesOf(ex));
                    error = true;
                }
            }
            RowStatus status = error ? RowStatus.ERROR : messages.isEmpty() ? RowStatus.OK : RowStatus.WARNING;
            rows.add(new ImportRow(row.rowNumber(), values, row.cells(), status, error ? null : action, messages));
        }
        return new Evaluation(table, effective, rows);
    }

    private static Map<String, String> checkMapping(
            ImportEntity entity, List<String> columns, Map<String, String> mapping) {
        var violations = new ArrayList<FieldViolation>();
        Set<String> used = new HashSet<>();
        Map<String, String> effective = new LinkedHashMap<>();
        for (String column : columns) {
            String field = Validation.trimToNull(mapping.get(column));
            if (field != null && !entity.hasField(field)) {
                violations.add(new FieldViolation(
                        "mapping." + column, ViolationCodes.INVALID_VALUE, "unknown target field '" + field + "'"));
            } else if (field != null && !used.add(field)) {
                violations.add(new FieldViolation(
                        "mapping." + column, ViolationCodes.DUPLICATE_KEY, "field '" + field + "' mapped twice"));
            }
            effective.put(column, field);
        }
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
        return effective;
    }

    private static Map<String, String> values(List<String> columns, List<String> cells, Map<String, String> mapping) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            String field = mapping.get(columns.get(i));
            if (field != null) {
                values.put(field, i < cells.size() ? cells.get(i) : "");
            }
        }
        return values;
    }

    private static boolean checkDuplicate(
            ImportEntity entity,
            Map<String, String> values,
            int rowNumber,
            Map<String, Integer> seenKeys,
            Map<String, Integer> seenSecondaryKeys,
            List<ImportMessage> messages) {
        String key = key(values, entity.keyFields());
        if (key != null) {
            Integer first = seenKeys.putIfAbsent(key, rowNumber);
            if (first != null) {
                messages.add(new ImportMessage(
                        entity.keyFields().getLast(),
                        ViolationCodes.DUPLICATE_KEY,
                        "duplicate key in file (first in row " + first + ")"));
                return true;
            }
        }
        if (entity == ImportEntity.PART_MAPPINGS) {
            String secondary = key(values, List.of("partnerBpnl", "partnerRole", "partnerPartNumber"));
            if (secondary != null) {
                Integer first = seenSecondaryKeys.putIfAbsent(secondary, rowNumber);
                if (first != null) {
                    messages.add(new ImportMessage(
                            "partnerPartNumber",
                            ViolationCodes.DUPLICATE_KEY,
                            "duplicate partner part number in file (first in row " + first + ")"));
                    return true;
                }
            }
        }
        return false;
    }

    private static String key(Map<String, String> values, List<String> fields) {
        List<String> parts = new ArrayList<>();
        for (String field : fields) {
            String value = Validation.trimToNull(values.get(field));
            if (value == null) {
                return null; // missing key fields are reported by validation
            }
            parts.add(value.toUpperCase(Locale.ROOT));
        }
        return String.join("\u0000", parts);
    }

    private PlanResult plan(ImportEntity entity, Map<String, String> v) {
        return switch (entity) {
            case PARTS -> parts.plan(partCommand(v));
            case PARTNERS -> partners.plan(partnerCommand(v));
            case SITES -> sites.plan(siteCommand(v));
            case RELATIONS -> relations.plan(relationCommand(v));
            case PART_MAPPINGS -> mappings.plan(relationOf(v).id(), mappingCommand(v));
        };
    }

    private ChangeAction apply(ImportEntity entity, Map<String, String> v) {
        return switch (entity) {
            case PARTS -> parts.upsert(partCommand(v)).action();
            case PARTNERS -> partners.upsert(partnerCommand(v)).action();
            case SITES -> sites.upsert(siteCommand(v)).action();
            case RELATIONS -> relations.upsert(relationCommand(v)).action();
            case PART_MAPPINGS ->
                mappings.upsert(relationOf(v).id(), mappingCommand(v)).action();
        };
    }

    private static PartCommand partCommand(Map<String, String> v) {
        return new PartCommand(
                v.get("partNumber"),
                v.get("nameDe"),
                v.get("nameEn"),
                v.get("drawingNumber"),
                v.get("drawingRevision"),
                v.get("material"),
                v.get("catenaXId"),
                null);
    }

    private static PartnerCommand partnerCommand(Map<String, String> v) {
        String roles = v.get("roles");
        List<String> roleList = roles == null
                ? List.of()
                : Arrays.stream(roles.split("[|,;/]")).map(String::strip).toList();
        return new PartnerCommand(
                v.get("bpnl"),
                v.get("name"),
                roleList,
                v.get("dspEndpoint"),
                v.get("did"),
                v.get("contactEmail"),
                null);
    }

    private static SiteCommand siteCommand(Map<String, String> v) {
        return new SiteCommand(
                v.get("bpns"),
                v.get("name"),
                v.get("ownerBpnl"),
                v.get("street"),
                v.get("postalCode"),
                v.get("city"),
                v.get("country"),
                null);
    }

    private static RelationCommand relationCommand(Map<String, String> v) {
        return new RelationCommand(v.get("partnerBpnl"), v.get("partnerRole"), v.get("exchangeMode"), null, null);
    }

    private static PartMappingCommand mappingCommand(Map<String, String> v) {
        return new PartMappingCommand(
                null, v.get("partNumber"), v.get("partnerPartNumber"), v.get("ownSiteBpns"), v.get("partnerSiteBpns"));
    }

    private Relation relationOf(Map<String, String> v) {
        try {
            return relations.findByPartner(v.get("partnerBpnl"), v.get("partnerRole"));
        } catch (NotFoundException ex) {
            throw ValidationException.of(
                    "partnerBpnl", ViolationCodes.UNKNOWN_REFERENCE, "no relation for this partner and role");
        }
    }

    private static List<ImportMessage> messagesOf(PpapException ex) {
        if (ex instanceof ValidationException validation) {
            return validation.violations().stream()
                    .map(ImportService::toMessage)
                    .toList();
        }
        if (ex instanceof DuplicateKeyException duplicate) {
            return List.of(new ImportMessage(duplicate.field(), duplicate.code(), duplicate.getMessage()));
        }
        return List.of(new ImportMessage(null, ex.code(), ex.getMessage()));
    }

    private static ImportMessage toMessage(FieldViolation violation) {
        return new ImportMessage(violation.field(), violation.code(), violation.message());
    }

    private static List<ImportMessage> errorsOf(ImportRow row) {
        return row.messages();
    }

    private static int count(List<ImportRow> rows, RowStatus status) {
        return (int) rows.stream().filter(row -> row.status() == status).count();
    }

    private static int countAction(List<ImportRow> rows, ChangeAction action) {
        return (int) rows.stream().filter(row -> row.action() == action).count();
    }

    private record Evaluation(Table table, Map<String, String> mapping, List<ImportRow> rows) {}
}
