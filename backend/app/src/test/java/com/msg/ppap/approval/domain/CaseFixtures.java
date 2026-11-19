package com.msg.ppap.approval.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Builders for approval domain tests. */
final class CaseFixtures {

    static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");
    static final LocalDate TODAY = LocalDate.parse("2026-11-19");

    static final Actor CQE = Actor.user("oem.quality", UserRole.CUSTOMER_QUALITY_ENGINEER);
    static final Actor SQM = Actor.user("tier1.quality", UserRole.SUPPLIER_QUALITY_MANAGER);
    static final Actor SI = new Actor("client:erp", java.util.Set.of(UserRole.SYSTEM_INTEGRATION), true, null);

    static final Map<String, Object> SCHEMA = Map.of("type", "object", "required", List.of("quantity"));

    /** Validator stub: values must be a map with key "quantity". */
    static final ValueValidator VALIDATOR =
            (schema, values) -> values instanceof Map<?, ?> map && map.containsKey("quantity")
                    ? List.of()
                    : List.of(new ValueValidator.Violation("/quantity", "required property missing"));

    private CaseFixtures() {}

    static List<RequestedElement> elements() {
        return List.of(
                new RequestedElement("PPF-01", Requirement.REQUIRED, null, snapshot(EvidenceType.DOCUMENT, null)),
                new RequestedElement("PPF-17", Requirement.REQUIRED, "5 pcs", snapshot(EvidenceType.VALUES, SCHEMA)),
                new RequestedElement(
                        "PPF-11", Requirement.ON_REQUEST, null, snapshot(EvidenceType.DOCUMENT_AND_VALUES, SCHEMA)));
    }

    static ElementSnapshot snapshot(EvidenceType type, Map<String, Object> schema) {
        return new ElementSnapshot("Name", "Name", type, schema);
    }

    static CaseHeader header(LocalDate due) {
        return new CaseHeader(
                new PartnerRef("BPNL000000TIER01", "Tier1"),
                UUID.randomUUID(),
                new PartRef(UUID.randomUUID(), "OEM-4711", "Teil", "Part"),
                "T1-4711",
                new CatalogRef("NEW_PART", "Neuteil", "New part"),
                new CatalogRef("VDA-2", "Stufe 2", "Level 2"),
                due,
                "remarks",
                null);
    }

    static ApprovalCase draft() {
        return ApprovalCase.createDraft("PPF-2026-00001", header(TODAY.plusDays(10)), elements(), CQE, NOW);
    }

    static ApprovalCase manual() {
        return ApprovalCase.captureManual("PPF-2026-00002", header(TODAY.plusDays(10)), elements(), SQM, NOW);
    }

    static DocumentRef document(String name) {
        return new DocumentRef(UUID.randomUUID(), name, "application/pdf", 10, "a".repeat(64), NOW, "x");
    }

    /** Supplier case IN_PROGRESS with a complete draft. */
    static ApprovalCase completeSupplierCase() {
        ApprovalCase c = manual();
        c.startWork(SQM, NOW);
        c.updateEvidence("PPF-01", SelfAssessment.OK, null, null, SQM, NOW);
        c.attachEvidenceDocument("PPF-01", document("deckblatt.pdf"), SQM, NOW);
        c.updateEvidence("PPF-17", SelfAssessment.OK, Map.of("quantity", 5), null, SQM, NOW);
        return c;
    }

    static Seal seal(String payload, SignatureStatus status) {
        return new Seal(payload, hash(payload), new SignatureInfo("ES256", "kid", "h..s", status));
    }

    static String hash(String payload) {
        try {
            return "sha256:"
                    + HexFormat.of()
                            .formatHex(MessageDigest.getInstance("SHA-256")
                                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Customer case SUBMITTED with a received revision 1. */
    static ApprovalCase submittedCustomerCase() {
        ApprovalCase c = draft();
        c.send(true, CQE, NOW, TODAY);
        List<ElementEvidence> evidence = List.of(
                new ElementEvidence("PPF-01", Requirement.REQUIRED, SelfAssessment.OK, null, null, List.of()),
                new ElementEvidence(
                        "PPF-17", Requirement.REQUIRED, SelfAssessment.OK, Map.of("quantity", 1), null, List.of()));
        c.receiveSubmission(
                1, evidence, NOW, "tier1.quality", seal("{\"r\":1}", SignatureStatus.VALID), Actor.exchange(), NOW);
        return c;
    }

    static ApprovalCase inReviewCase() {
        ApprovalCase c = submittedCustomerCase();
        c.startReview(seal -> SignatureStatus.VALID, CQE, NOW);
        return c;
    }

    static ApprovalCase.DecisionSealer decisionSealer() {
        return (c, s, d) -> seal("{\"decision\":\"" + d.result() + "\"}", SignatureStatus.VALID);
    }
}
