package com.msg.ppap.approval.application.seed;

import com.msg.ppap.approval.application.ApprovalContext;
import com.msg.ppap.approval.application.ApprovalEventPublisher;
import com.msg.ppap.approval.application.DocumentRefs;
import com.msg.ppap.approval.application.FormTemplateService;
import com.msg.ppap.approval.application.PayloadSealing;
import com.msg.ppap.approval.application.RequestElementFactory;
import com.msg.ppap.approval.application.port.out.ApprovalCaseRepository;
import com.msg.ppap.approval.application.port.out.CaseNumberGenerator;
import com.msg.ppap.approval.application.port.out.FormTemplateRepository.UpsertOutcome;
import com.msg.ppap.approval.domain.ApprovalCase;
import com.msg.ppap.approval.domain.ApprovalRequest;
import com.msg.ppap.approval.domain.CaseAction;
import com.msg.ppap.approval.domain.CaseHeader;
import com.msg.ppap.approval.domain.CaseRole;
import com.msg.ppap.approval.domain.CaseSource;
import com.msg.ppap.approval.domain.CaseStatus;
import com.msg.ppap.approval.domain.CatalogRef;
import com.msg.ppap.approval.domain.Decision;
import com.msg.ppap.approval.domain.DecisionResult;
import com.msg.ppap.approval.domain.DocumentRef;
import com.msg.ppap.approval.domain.ElementEvidence;
import com.msg.ppap.approval.domain.ElementFinding;
import com.msg.ppap.approval.domain.FindingResult;
import com.msg.ppap.approval.domain.FormTemplate;
import com.msg.ppap.approval.domain.PartRef;
import com.msg.ppap.approval.domain.PartnerRef;
import com.msg.ppap.approval.domain.RequestedElement;
import com.msg.ppap.approval.domain.Requirement;
import com.msg.ppap.approval.domain.SelfAssessment;
import com.msg.ppap.approval.domain.Submission;
import com.msg.ppap.approval.domain.SubmissionStatus;
import com.msg.ppap.approval.domain.TimelineEntry;
import com.msg.ppap.documents.api.Documents;
import com.msg.ppap.identity.ActorProvider;
import com.msg.ppap.masterdata.api.MasterdataQueries;
import com.msg.ppap.platform.domain.Ids;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Imports seed data: form templates (upsert by code) and approval cases (idempotent by tenant, exchange id and own
 * role; existing cases are skipped). Snapshots are built from the already loaded catalogue; documents are uploaded
 * through the documents module. Submitted revisions are only allowed for legacy imports and stay unsigned ({@code
 * NOT_SIGNED}). Each case is imported in its own transaction; failures are counted and logged.
 */
@Service
public class ApprovalSeedService {

    private static final Logger LOG = LoggerFactory.getLogger(ApprovalSeedService.class);

    private final ApprovalContext context;
    private final ApprovalCaseRepository repository;
    private final ApprovalEventPublisher events;
    private final MasterdataQueries masterdata;
    private final RequestElementFactory elements;
    private final CaseNumberGenerator caseNumbers;
    private final PayloadSealing sealing;
    private final Documents documents;
    private final FormTemplateService templates;
    private final TransactionTemplate transactions;

    public ApprovalSeedService(
            ApprovalContext context,
            ApprovalCaseRepository repository,
            ApprovalEventPublisher events,
            MasterdataQueries masterdata,
            RequestElementFactory elements,
            CaseNumberGenerator caseNumbers,
            PayloadSealing sealing,
            Documents documents,
            FormTemplateService templates,
            TransactionTemplate transactions) {
        this.context = context;
        this.repository = repository;
        this.events = events;
        this.masterdata = masterdata;
        this.elements = elements;
        this.caseNumbers = caseNumbers;
        this.sealing = sealing;
        this.documents = documents;
        this.templates = templates;
        this.transactions = transactions;
    }

    /** Upserts form templates; returns counts. */
    public Map<String, Object> importTemplates(List<FormTemplate> list) {
        var counts = new Counts();
        for (FormTemplate template : list) {
            try {
                UpsertOutcome outcome = transactions.execute(status -> templates.upsert(template));
                if (outcome == UpsertOutcome.CREATED) {
                    counts.created++;
                } else if (outcome == UpsertOutcome.UPDATED) {
                    counts.updated++;
                } else {
                    counts.unchanged++;
                }
            } catch (RuntimeException ex) {
                LOG.warn("Seed form template {} rejected: {}", template.code(), ex.getMessage());
                counts.failed++;
            }
        }
        return counts.toMap();
    }

    /** Imports cases; returns counts. */
    public Map<String, Object> importCases(SeedCases file, Path documentsPath) {
        var counts = new Counts();
        Map<String, DocumentRef> uploaded = new HashMap<>();
        for (SeedCases.SeedCase seed : file.cases()) {
            try {
                Boolean created = transactions.execute(status -> importCase(seed, documentsPath, uploaded));
                if (Boolean.TRUE.equals(created)) {
                    counts.created++;
                } else {
                    counts.unchanged++;
                }
            } catch (RuntimeException ex) {
                LOG.warn(
                        "Seed case {} ({} {}) rejected: {}",
                        seed.caseNumber(),
                        seed.ownRole(),
                        seed.exchangeId(),
                        ex.getMessage());
                counts.failed++;
            }
        }
        return counts.toMap();
    }

    boolean importCase(SeedCases.SeedCase seed, Path documentsPath, Map<String, DocumentRef> uploaded) {
        CaseRole ownRole = CaseRole.valueOf(upper(seed.ownRole()));
        if (seed.exchangeId() == null) {
            throw new IllegalArgumentException("exchangeId missing");
        }
        if (repository.findByExchangeId(seed.exchangeId(), ownRole).isPresent()) {
            return false;
        }
        CaseSource source = CaseSource.valueOf(upper(seed.source()));
        CaseStatus status = CaseStatus.valueOf(upper(seed.status()));
        Instant now = context.now();
        LocalDate today = context.today();

        var partner = masterdata.findPartner(seed.partnerBpnl());
        var relation = masterdata.findRelation(
                seed.partnerBpnl(), ownRole.partnerRole().name());
        var part = masterdata
                .findPartByNumber(seed.partNumber())
                .orElseThrow(() -> new IllegalArgumentException("unknown part " + seed.partNumber()));
        var reason = masterdata
                .findReason(seed.reasonCode())
                .orElseThrow(() -> new IllegalArgumentException("unknown reason " + seed.reasonCode()));
        var level = masterdata
                .findLevel(seed.levelCode())
                .orElseThrow(() -> new IllegalArgumentException("unknown level " + seed.levelCode()));
        var header = new CaseHeader(
                new PartnerRef(
                        seed.partnerBpnl(),
                        partner.map(MasterdataQueries.PartnerInfo::name).orElse(seed.partnerBpnl())),
                relation.map(MasterdataQueries.RelationInfo::id).orElse(null),
                new PartRef(part.id(), part.partNumber(), part.nameDe(), part.nameEn()),
                seed.partnerPartNumber(),
                new CatalogRef(reason.code(), reason.nameDe(), reason.nameEn()),
                new CatalogRef(level.code(), level.nameDe(), level.nameEn()),
                SeedDates.date(seed.dueDate(), today),
                seed.remarks(),
                null);
        List<RequestedElement> requested = seed.elements().stream()
                .map(e -> new RequestedElement(
                        e.code(),
                        e.requirement() == null ? Requirement.REQUIRED : Requirement.valueOf(upper(e.requirement())),
                        e.note(),
                        elements.snapshot(e.code())))
                .toList();
        var request = status == CaseStatus.DRAFT
                ? new ApprovalRequest(null, null, requested, List.of())
                : new ApprovalRequest(
                        SeedDates.instant(seed.requestedAt(), now), seed.requestedBy(), requested, List.of());
        String caseNumber = seed.caseNumber() == null || seed.caseNumber().isBlank()
                ? caseNumbers.next(today.getYear())
                : seed.caseNumber().strip();
        if (repository.existsByCaseNumber(caseNumber)) {
            throw new IllegalArgumentException("case number " + caseNumber + " already used");
        }
        caseNumbers.reserve(caseNumber);
        List<TimelineEntry> timeline = seed.timeline().stream()
                .map(t -> new TimelineEntry(
                        Ids.newId(),
                        SeedDates.instant(t.at(), now),
                        t.actor() == null ? ActorProvider.SEED_ACTOR : t.actor(),
                        t.from() == null ? null : CaseStatus.valueOf(upper(t.from())),
                        CaseStatus.valueOf(upper(t.to())),
                        CaseAction.valueOf(upper(t.action())),
                        t.comment()))
                .toList();
        var id = Ids.newId();
        ApprovalCase shell = ApprovalCase.restore(
                id,
                caseNumber,
                seed.exchangeId(),
                ownRole,
                source,
                status,
                header,
                request,
                List.of(),
                List.of(),
                timeline,
                null);
        List<Submission> submissions = new ArrayList<>();
        for (SeedCases.SeedSubmission s : seed.submissions()) {
            submissions.add(submission(shell, s, requested, source, documentsPath, uploaded, now));
        }
        ApprovalCase full = ApprovalCase.restore(
                id,
                caseNumber,
                seed.exchangeId(),
                ownRole,
                source,
                status,
                header,
                request,
                submissions,
                List.of(),
                timeline,
                null);
        ApprovalCase saved = repository.save(full);
        events.publish(saved, "IMPORTED", ActorProvider.SEED_ACTOR, now);
        return true;
    }

    private Submission submission(
            ApprovalCase shell,
            SeedCases.SeedSubmission s,
            List<RequestedElement> requested,
            CaseSource source,
            Path documentsPath,
            Map<String, DocumentRef> uploaded,
            Instant now) {
        SubmissionStatus status = SubmissionStatus.valueOf(upper(s.status()));
        Map<String, ElementEvidence> evidence = new LinkedHashMap<>();
        requested.forEach(r -> evidence.put(r.code(), ElementEvidence.empty(r)));
        for (SeedCases.Evidence e : s.elements()) {
            Requirement requirement = requested.stream()
                    .filter(r -> r.code().equals(e.code()))
                    .map(RequestedElement::requirement)
                    .findFirst()
                    .orElse(Requirement.ON_REQUEST);
            List<DocumentRef> docs = e.documents().stream()
                    .map(name -> upload(name, documentsPath, uploaded))
                    .toList();
            evidence.put(
                    e.code(),
                    new ElementEvidence(
                            e.code(),
                            requirement,
                            e.selfAssessment() == null ? null : SelfAssessment.valueOf(upper(e.selfAssessment())),
                            e.values(),
                            e.comment(),
                            docs));
        }
        List<ElementEvidence> list = List.copyOf(evidence.values());
        Integer basedOn = s.revision() > 1 ? s.revision() - 1 : null;
        if (status == SubmissionStatus.DRAFT) {
            if (s.decision() != null) {
                throw new IllegalArgumentException("draft revision " + s.revision() + " cannot have a decision");
            }
            return Submission.restore(s.revision(), status, basedOn, list, null, null, null, null);
        }
        if (source != CaseSource.LEGACY_IMPORT) {
            throw new IllegalArgumentException(
                    "submitted revisions are only allowed for LEGACY_IMPORT (signed revisions are created live)");
        }
        Instant submittedAt = SeedDates.instant(s.submittedAt(), now);
        String submittedBy = s.submittedBy() == null ? ActorProvider.SEED_ACTOR : s.submittedBy();
        Submission unsealed = Submission.restore(
                s.revision(), SubmissionStatus.SUBMITTED, basedOn, list, submittedAt, submittedBy, null, null);
        var seal = sealing.unsignedSubmission(shell, unsealed);
        Submission sealed = Submission.restore(
                s.revision(), SubmissionStatus.SUBMITTED, basedOn, list, submittedAt, submittedBy, seal, null);
        if (s.decision() == null) {
            return sealed;
        }
        SeedCases.SeedDecision d = s.decision();
        var decision = new Decision(
                s.revision(),
                DecisionResult.valueOf(upper(d.result())),
                d.conditions().stream().map(SeedCases.Condition::text).toList(),
                SeedDates.date(d.conditionDueDate(), context.today()),
                d.comment(),
                d.elementFindings().stream()
                        .map(f -> new ElementFinding(f.code(), FindingResult.valueOf(upper(f.result())), f.comment()))
                        .toList(),
                SeedDates.instant(d.decidedAt(), now),
                d.decidedBy() == null ? ActorProvider.SEED_ACTOR : d.decidedBy(),
                null);
        var decisionSeal = sealing.unsignedDecision(shell, sealed, decision);
        var sealedDecision = new Decision(
                decision.revision(),
                decision.result(),
                decision.conditions(),
                decision.conditionDueDate(),
                decision.comment(),
                decision.elementFindings(),
                decision.decidedAt(),
                decision.decidedBy(),
                decisionSeal);
        return Submission.restore(
                s.revision(),
                SubmissionStatus.SUBMITTED,
                basedOn,
                list,
                submittedAt,
                submittedBy,
                seal,
                sealedDecision);
    }

    private DocumentRef upload(String name, Path documentsPath, Map<String, DocumentRef> uploaded) {
        DocumentRef cached = uploaded.get(name);
        if (cached != null) {
            return cached;
        }
        Path file = Optional.ofNullable(documentsPath)
                .map(dir -> dir.resolve(name).normalize())
                .filter(p -> p.startsWith(documentsPath.normalize()))
                .filter(Files::isRegularFile)
                .orElseThrow(() -> new IllegalArgumentException("seed document " + name + " not found"));
        try (InputStream in = Files.newInputStream(file)) {
            DocumentRef ref = DocumentRefs.of(documents.upload(name, in));
            uploaded.put(name, ref);
            return ref;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static String upper(String value) {
        if (value == null) {
            throw new IllegalArgumentException("missing value");
        }
        return value.strip().toUpperCase(Locale.ROOT);
    }

    private static final class Counts {
        int created;
        int updated;
        int unchanged;
        int failed;

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("created", created);
            map.put("updated", updated);
            map.put("unchanged", unchanged);
            map.put("failed", failed);
            return map;
        }
    }
}
