package com.msg.ppap.approval.application;

import com.msg.ppap.approval.application.port.in.CaseExportUseCase;
import com.msg.ppap.approval.application.port.in.RevisionFactsUseCase;
import com.msg.ppap.approval.application.port.in.SignatureVerificationUseCase;
import com.msg.ppap.approval.domain.Actor;
import com.msg.ppap.approval.domain.ApprovalCase;
import com.msg.ppap.approval.domain.Decision;
import com.msg.ppap.approval.domain.Seal;
import com.msg.ppap.approval.domain.SignatureStatus;
import com.msg.ppap.approval.domain.SubSupplierReference;
import com.msg.ppap.approval.domain.Submission;
import com.msg.ppap.approval.domain.TimelineEntry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Facts of approval cases for other modules: re-verification of {@code UNVERIFIED} signatures (exchange), submitted
 * revisions (usage counters) and the case snapshot of the audit export (audit).
 */
@Service
@Transactional(readOnly = true)
public class CaseFactsService implements SignatureVerificationUseCase, RevisionFactsUseCase, CaseExportUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(CaseFactsService.class);

    private final ApprovalContext context;
    private final PayloadSealing sealing;
    private final CascadeQueries cascade;

    public CaseFactsService(ApprovalContext context, PayloadSealing sealing, CascadeQueries cascade) {
        this.context = context;
        this.sealing = sealing;
        this.cascade = cascade;
    }

    @Override
    @Transactional
    public int reverifyUnverified(String partnerBpnl) {
        int changed = 0;
        for (UUID caseId : context.repository().findCasesWithUnverifiedSignatures(partnerBpnl)) {
            ApprovalCase c = context.load(caseId);
            for (Submission s : c.submissions()) {
                if (s.seal() != null && s.seal().signature().status() == SignatureStatus.UNVERIFIED) {
                    SignatureStatus status = c.reverifySignature(
                            s.revision(), seal -> sealing.verify(seal, partnerBpnl), Actor.exchange(), context.now());
                    if (status != SignatureStatus.UNVERIFIED) {
                        changed++;
                        LOG.info(
                                "Signature of {} revision {} re-verified after key fetch: {}",
                                c.caseNumber(),
                                s.revision(),
                                status);
                    }
                }
            }
            context.save(c);
        }
        return changed;
    }

    @Override
    public List<RevisionFact> submittedRevisions() {
        return context.repository().submittedRevisions().stream()
                .map(r -> new RevisionFact(
                        r.caseId(), r.exchangeId(), r.ownRole(), r.source(), r.revision(), r.submittedAt()))
                .toList();
    }

    @Override
    public Optional<RevisionFact> submittedRevision(UUID caseId, int revision) {
        return context.repository()
                .findById(caseId)
                .flatMap(c -> c.submissions().stream()
                        .filter(s -> s.revision() == revision && !s.draft() && s.submittedAt() != null)
                        .findFirst()
                        .map(s -> new RevisionFact(
                                c.id(),
                                c.exchangeId(),
                                c.ownRole().name(),
                                c.source().name(),
                                s.revision(),
                                s.submittedAt())));
    }

    @Override
    public Optional<Map<String, Object>> export(UUID caseId) {
        return context.repository().findById(caseId).map(this::snapshot);
    }

    private Map<String, Object> snapshot(ApprovalCase c) {
        var h = c.header();
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("id", c.id().toString());
        header.put("caseNumber", c.caseNumber());
        header.put("exchangeId", c.exchangeId().toString());
        header.put("ownRole", c.ownRole().name());
        header.put("source", c.source().name());
        header.put("status", c.status().name());
        header.put(
                "partner",
                Map.of("bpnl", h.partner().bpnl(), "name", h.partner().name()));
        put(header, "partNumber", h.part().partNumber());
        put(header, "partName", h.part().nameDe());
        put(header, "customerPartNumber", c.customerPartNumber());
        put(header, "supplierPartNumber", c.supplierPartNumber());
        header.put("reason", h.reason().code());
        header.put("level", h.level().code());
        header.put("dueDate", h.dueDate().toString());
        put(header, "requestedAt", str(c.request().requestedAt()));
        put(header, "requestedBy", c.request().requestedBy());
        put(header, "parentCaseId", str(c.parentCaseId()));
        header.put(
                "childCases",
                cascade.children(c).stream()
                        .map(child -> Map.of(
                                "id", child.id().toString(),
                                "caseNumber", child.caseNumber(),
                                "status", child.status().name()))
                        .toList());
        put(header, "createdAt", c.metadata() == null ? null : str(c.metadata().createdAt()));

        List<Map<String, Object>> timeline = new ArrayList<>();
        for (TimelineEntry t : c.timeline()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("at", t.at().toString());
            entry.put("actor", t.actor());
            entry.put("actorDisplay", cascade.display(t.actor(), h.partner()));
            entry.put("action", t.action().name());
            put(entry, "from", t.from() == null ? null : t.from().name());
            entry.put("to", t.to().name());
            put(entry, "comment", t.comment());
            timeline.add(entry);
        }

        List<Map<String, Object>> submissions = new ArrayList<>();
        List<Map<String, Object>> decisions = new ArrayList<>();
        for (Submission s : c.submissions()) {
            if (s.draft()) {
                continue;
            }
            Map<String, Object> revision = new LinkedHashMap<>();
            revision.put("revision", s.revision());
            put(revision, "submittedAt", str(s.submittedAt()));
            put(revision, "submittedBy", s.submittedBy());
            put(
                    revision,
                    "exchangeMode",
                    s.exchangeMode() == null ? null : s.exchangeMode().name());
            seal(revision, s.seal());
            revision.put(
                    "documents",
                    s.elements().stream()
                            .flatMap(e -> e.documents().stream()
                                    .map(d -> Map.<String, Object>of(
                                            "element", e.code(), "fileName", d.fileName(), "sha256", d.sha256())))
                            .toList());
            revision.put(
                    "subSupplierReferences",
                    s.subSupplierReferences().stream()
                            .map(CaseFactsService::reference)
                            .toList());
            submissions.add(revision);
            s.decision().ifPresent(d -> decisions.add(decision(d)));
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("caseNumber", c.caseNumber());
        snapshot.put("case", header);
        snapshot.put("timeline", timeline);
        snapshot.put("submissions", submissions);
        snapshot.put("decisions", decisions);
        return snapshot;
    }

    private static Map<String, Object> decision(Decision d) {
        Map<String, Object> decision = new LinkedHashMap<>();
        decision.put("revision", d.revision());
        decision.put("result", d.result().name());
        decision.put("conditions", d.conditions());
        put(decision, "conditionDueDate", str(d.conditionDueDate()));
        put(decision, "comment", d.comment());
        decision.put("decidedAt", d.decidedAt().toString());
        decision.put("decidedBy", d.decidedBy());
        seal(decision, d.seal());
        return decision;
    }

    private static Map<String, Object> reference(SubSupplierReference r) {
        Map<String, Object> reference = new LinkedHashMap<>();
        reference.put("subSupplierBpnl", r.subSupplierBpnl());
        put(reference, "subSupplierName", r.subSupplierName());
        reference.put("exchangeId", r.exchangeId().toString());
        reference.put("caseNumber", r.caseNumber());
        reference.put("partNumber", r.partNumber());
        reference.put("revision", r.revision());
        reference.put("decisionResult", r.decisionResult().name());
        reference.put("decidedAt", r.decidedAt().toString());
        reference.put("decisionHash", r.decisionHash());
        return reference;
    }

    private static void seal(Map<String, Object> target, Seal seal) {
        if (seal == null) {
            return;
        }
        target.put("payloadHash", seal.payloadHash());
        Map<String, Object> signature = new LinkedHashMap<>();
        put(signature, "alg", seal.signature().alg());
        put(signature, "kid", seal.signature().kid());
        put(signature, "jws", seal.signature().jws());
        signature.put("status", seal.signature().status().name());
        target.put("signature", signature);
    }

    private static void put(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }
}
