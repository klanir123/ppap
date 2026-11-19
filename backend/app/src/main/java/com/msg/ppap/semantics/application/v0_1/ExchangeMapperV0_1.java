package com.msg.ppap.semantics.application.v0_1;

import com.msg.ppap.semantics.api.DecisionMessage;
import com.msg.ppap.semantics.api.DocumentReference;
import com.msg.ppap.semantics.api.ExchangeModels;
import com.msg.ppap.semantics.api.RequestMessage;
import com.msg.ppap.semantics.api.SubmissionMessage;
import com.msg.ppap.semantics.application.ExchangeMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mapper of exchange format v0.1: version-neutral messages ↔ payload JSON trees. Blank optional strings are
 * omitted; timestamps are ISO-8601 UTC, dates ISO-8601.
 */
@Component
public class ExchangeMapperV0_1 implements ExchangeMapper {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Override
    public String version() {
        return ExchangeModels.VERSION;
    }

    @Override
    public JsonNode toPayload(RequestMessage m) {
        var payload = new PayloadsV0_1.Request(
                new PayloadsV0_1.RequestHeader(
                        str(m.exchangeId()),
                        m.caseNumber(),
                        m.customerBpnl(),
                        m.supplierBpnl(),
                        m.customerPartNumber(),
                        blankToNull(m.supplierPartNumber()),
                        blankToNull(m.partName())),
                entry(m.reason()),
                entry(m.level()),
                str(m.dueDate()),
                blankToNull(m.remarks()),
                str(m.requestedAt()),
                m.requestedBy(),
                map(
                        m.elements(),
                        e -> new PayloadsV0_1.RequestedElement(
                                e.code(),
                                e.requirement(),
                                blankToNull(e.note()),
                                e.snapshot() == null
                                        ? null
                                        : new PayloadsV0_1.Snapshot(
                                                e.snapshot().nameDe(),
                                                e.snapshot().nameEn(),
                                                e.snapshot().evidenceType(),
                                                e.snapshot().valueSchema()))),
                map(m.attachments(), ExchangeMapperV0_1::document));
        return MAPPER.valueToTree(payload);
    }

    @Override
    public RequestMessage toRequest(JsonNode node) {
        var p = MAPPER.treeToValue(node, PayloadsV0_1.Request.class);
        var h = p.header();
        return new RequestMessage(
                UUID.fromString(h.exchangeId()),
                h.caseNumber(),
                h.customerBpnl(),
                h.supplierBpnl(),
                h.customerPartNumber(),
                h.supplierPartNumber(),
                h.partName(),
                codeName(p.reason()),
                codeName(p.level()),
                LocalDate.parse(p.dueDate()),
                p.remarks(),
                Instant.parse(p.requestedAt()),
                p.requestedBy(),
                map(
                        p.elements(),
                        e -> new RequestMessage.Element(
                                e.code(),
                                e.requirement(),
                                e.note(),
                                new RequestMessage.Snapshot(
                                        e.snapshot().nameDe(),
                                        e.snapshot().nameEn(),
                                        e.snapshot().evidenceType(),
                                        e.snapshot().valueSchema()))),
                map(p.attachments(), ExchangeMapperV0_1::reference));
    }

    @Override
    public JsonNode toPayload(SubmissionMessage m) {
        var payload = new PayloadsV0_1.Submission(
                new PayloadsV0_1.SubmissionHeader(
                        str(m.exchangeId()),
                        m.caseNumber(),
                        m.revision(),
                        m.customerBpnl(),
                        m.supplierBpnl(),
                        m.customerPartNumber(),
                        m.supplierPartNumber(),
                        m.reasonCode(),
                        m.levelCode()),
                map(
                        m.elements(),
                        e -> new PayloadsV0_1.Element(
                                e.code(),
                                e.requirement(),
                                e.selfAssessment(),
                                e.values(),
                                blankToNull(e.comment()),
                                map(e.documents(), ExchangeMapperV0_1::document))),
                map(
                        m.subSupplierReferences(),
                        r -> new PayloadsV0_1.SubSupplierReference(
                                r.subSupplierBpnl(),
                                r.partNumber(),
                                str(r.exchangeId()),
                                r.revision(),
                                r.result(),
                                r.hash())),
                str(m.submittedAt()),
                m.submittedBy());
        return MAPPER.valueToTree(payload);
    }

    @Override
    public SubmissionMessage toSubmission(JsonNode node) {
        var p = MAPPER.treeToValue(node, PayloadsV0_1.Submission.class);
        var h = p.header();
        return new SubmissionMessage(
                UUID.fromString(h.exchangeId()),
                h.caseNumber(),
                h.revision(),
                h.customerBpnl(),
                h.supplierBpnl(),
                h.customerPartNumber(),
                h.supplierPartNumber(),
                h.reasonCode(),
                h.levelCode(),
                map(
                        p.elements(),
                        e -> new SubmissionMessage.Element(
                                e.code(),
                                e.requirement(),
                                e.selfAssessment(),
                                e.values(),
                                e.comment(),
                                map(e.documents(), ExchangeMapperV0_1::reference))),
                map(
                        p.subSupplierReferences(),
                        r -> new SubmissionMessage.SubSupplierReference(
                                r.subSupplierBpnl(),
                                r.partNumber(),
                                UUID.fromString(r.exchangeId()),
                                r.revision(),
                                r.result(),
                                r.hash())),
                Instant.parse(p.submittedAt()),
                p.submittedBy());
    }

    @Override
    public JsonNode toPayload(DecisionMessage m) {
        var payload = new PayloadsV0_1.Decision(
                str(m.exchangeId()),
                m.caseNumber(),
                m.revision(),
                m.submissionHash(),
                m.result(),
                map(m.conditions(), PayloadsV0_1.Condition::new),
                str(m.conditionDueDate()),
                blankToNull(m.comment()),
                map(m.elementFindings(), f -> new PayloadsV0_1.Finding(f.code(), f.result(), blankToNull(f.comment()))),
                str(m.decidedAt()),
                m.decidedBy());
        return MAPPER.valueToTree(payload);
    }

    @Override
    public DecisionMessage toDecision(JsonNode node) {
        var p = MAPPER.treeToValue(node, PayloadsV0_1.Decision.class);
        return new DecisionMessage(
                UUID.fromString(p.exchangeId()),
                p.caseNumber(),
                p.revision(),
                p.submissionHash(),
                p.result(),
                map(p.conditions(), PayloadsV0_1.Condition::text),
                p.conditionDueDate() == null ? null : LocalDate.parse(p.conditionDueDate()),
                p.comment(),
                map(p.elementFindings(), f -> new DecisionMessage.Finding(f.code(), f.result(), f.comment())),
                Instant.parse(p.decidedAt()),
                p.decidedBy());
    }

    private static PayloadsV0_1.CatalogEntry entry(RequestMessage.CodeName codeName) {
        return codeName == null
                ? null
                : new PayloadsV0_1.CatalogEntry(codeName.code(), codeName.nameDe(), codeName.nameEn());
    }

    private static RequestMessage.CodeName codeName(PayloadsV0_1.CatalogEntry entry) {
        return new RequestMessage.CodeName(entry.code(), entry.nameDe(), entry.nameEn());
    }

    private static PayloadsV0_1.Document document(DocumentReference ref) {
        return new PayloadsV0_1.Document(ref.fileName(), ref.mediaType(), ref.size(), ref.sha256());
    }

    private static DocumentReference reference(PayloadsV0_1.Document doc) {
        return new DocumentReference(doc.fileName(), doc.mediaType(), doc.size(), doc.sha256());
    }

    private static <S, T> List<T> map(List<S> source, Function<S, T> mapper) {
        return source == null ? List.of() : source.stream().map(mapper).toList();
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
