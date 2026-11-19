package com.msg.ppap.approval.adapter.out.jpa;

import com.msg.ppap.approval.application.port.out.ApprovalCaseRepository;
import com.msg.ppap.approval.application.port.out.CaseQuery;
import com.msg.ppap.approval.application.port.out.CaseStatistics;
import com.msg.ppap.approval.application.port.out.CaseSummary;
import com.msg.ppap.approval.domain.ApprovalCase;
import com.msg.ppap.approval.domain.ApprovalRequest;
import com.msg.ppap.approval.domain.CaseAction;
import com.msg.ppap.approval.domain.CaseHeader;
import com.msg.ppap.approval.domain.CaseMetadata;
import com.msg.ppap.approval.domain.CaseRole;
import com.msg.ppap.approval.domain.CaseSource;
import com.msg.ppap.approval.domain.CaseStatus;
import com.msg.ppap.approval.domain.CatalogRef;
import com.msg.ppap.approval.domain.Decision;
import com.msg.ppap.approval.domain.DecisionResult;
import com.msg.ppap.approval.domain.PartRef;
import com.msg.ppap.approval.domain.PartnerRef;
import com.msg.ppap.approval.domain.Seal;
import com.msg.ppap.approval.domain.SignatureInfo;
import com.msg.ppap.approval.domain.SignatureStatus;
import com.msg.ppap.approval.domain.Submission;
import com.msg.ppap.approval.domain.SubmissionStatus;
import com.msg.ppap.approval.domain.TimelineEntry;
import com.msg.ppap.identity.ActorProvider;
import com.msg.ppap.identity.TenantContext;
import com.msg.ppap.platform.domain.Ids;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JPA/JDBC persistence of approval cases. Submitted revisions and decisions are inserted once and never updated
 * (except the signature verification status of a revision); timeline entries are appended.
 */
@Repository
class JpaApprovalCaseRepository implements ApprovalCaseRepository {

    private static final List<String> OPEN =
            CaseStatus.OPEN.stream().map(Enum::name).sorted().toList();

    private final ApCaseJpaRepository cases;
    private final ApSubmissionJpaRepository submissions;
    private final ApDecisionJpaRepository decisions;
    private final ApTimelineJpaRepository timeline;
    private final NamedParameterJdbcTemplate jdbc;
    private final TenantContext tenant;
    private final ActorProvider actors;
    private final Clock clock;

    JpaApprovalCaseRepository(
            ApCaseJpaRepository cases,
            ApSubmissionJpaRepository submissions,
            ApDecisionJpaRepository decisions,
            ApTimelineJpaRepository timeline,
            NamedParameterJdbcTemplate jdbc,
            TenantContext tenant,
            ActorProvider actors,
            Clock clock) {
        this.cases = cases;
        this.submissions = submissions;
        this.decisions = decisions;
        this.timeline = timeline;
        this.jdbc = jdbc;
        this.tenant = tenant;
        this.actors = actors;
        this.clock = clock;
    }

    @Override
    public Optional<ApprovalCase> findById(UUID id) {
        return cases.findByTenantIdAndId(tenant.tenantId(), id).map(this::toDomain);
    }

    @Override
    public Optional<ApprovalCase> findByExchangeId(UUID exchangeId, CaseRole ownRole) {
        return cases.findByTenantIdAndExchangeIdAndOwnRole(tenant.tenantId(), exchangeId, ownRole.name())
                .map(this::toDomain);
    }

    @Override
    public boolean existsByCaseNumber(String caseNumber) {
        return cases.existsByTenantIdAndCaseNumber(tenant.tenantId(), caseNumber);
    }

    @Override
    public ApprovalCase save(ApprovalCase c) {
        String t = tenant.tenantId();
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        String actor = actors.currentActor();
        ApCaseEntity e = cases.findByTenantIdAndId(t, c.id()).orElseGet(() -> {
            var created = new ApCaseEntity();
            created.id = c.id();
            created.tenantId = t;
            created.createdAt = now;
            created.createdBy = actor;
            created.caseNumber = c.caseNumber();
            created.exchangeId = c.exchangeId();
            created.ownRole = c.ownRole().name();
            created.source = c.source().name();
            return created;
        });
        apply(e, c);
        e.updatedAt = now;
        e.updatedBy = actor;
        cases.saveAndFlush(e);

        Map<Integer, ApSubmissionEntity> existing =
                submissions.findByTenantIdAndCaseIdOrderByRevision(t, c.id()).stream()
                        .collect(Collectors.toMap(s -> s.revision, Function.identity()));
        Set<Integer> decided = decisions.findByTenantIdAndCaseIdOrderByRevision(t, c.id()).stream()
                .map(d -> d.revision)
                .collect(Collectors.toSet());
        for (Submission s : c.submissions()) {
            ApSubmissionEntity row = existing.get(s.revision());
            if (row == null) {
                row = new ApSubmissionEntity();
                row.id = Ids.newId();
                row.tenantId = t;
                row.caseId = c.id();
                row.revision = s.revision();
                row.basedOnRevision = s.basedOnRevision();
                apply(row, s, now);
                submissions.save(row);
            } else if (SubmissionStatus.DRAFT.name().equals(row.status)) {
                apply(row, s, now);
            } else if (s.seal() != null && !s.seal().signature().status().name().equals(row.signatureStatus)) {
                row.signatureStatus = s.seal().signature().status().name();
                row.updatedAt = now;
            }
            if (s.decision().isPresent() && !decided.contains(s.revision())) {
                decisions.save(decisionRow(t, c.id(), s.decision().get()));
            }
        }
        Set<UUID> known = timeline.findByTenantIdAndCaseIdOrderBySeq(t, c.id()).stream()
                .map(r -> r.id)
                .collect(Collectors.toSet());
        for (TimelineEntry entry : c.timeline()) {
            if (!known.contains(entry.id())) {
                timeline.save(timelineRow(t, c.id(), entry));
            }
        }
        submissions.flush();
        decisions.flush();
        timeline.flush();
        return findById(c.id()).orElseThrow();
    }

    @Override
    public Page<CaseSummary> search(CaseQuery query, Pageable pageable) {
        List<String> statuses = query.statuses().stream().map(Enum::name).toList();
        return cases.search(
                        tenant.tenantId(),
                        statuses.isEmpty(),
                        statuses.isEmpty() ? List.of("-") : statuses,
                        query.ownRole() == null ? null : query.ownRole().name(),
                        blankToNull(query.partnerBpnl()),
                        query.overdue(),
                        OPEN,
                        query.today(),
                        like(query.text()),
                        pageable)
                .map(JpaApprovalCaseRepository::summary);
    }

    @Override
    public CaseStatistics statistics(CaseStatistics.Parameters p) {
        var params = new MapSqlParameterSource()
                .addValue("tenant", tenant.tenantId())
                .addValue("open", OPEN)
                .addValue("today", Date.valueOf(p.today()))
                .addValue("since", Timestamp.from(p.since()));
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (CaseStatus status : CaseStatus.values()) {
            byStatus.put(status.name(), 0L);
        }
        jdbc.query(
                "select status, count(*) as n from ap_case where tenant_id = :tenant group by status", params, rs -> {
                    byStatus.put(rs.getString("status"), rs.getLong("n"));
                });
        long open = OPEN.stream().mapToLong(s -> byStatus.getOrDefault(s, 0L)).sum();
        long overdue = jdbc.queryForObject(
                "select count(*) from ap_case where tenant_id = :tenant and status in (:open) and due_date < :today",
                params,
                Long.class);
        long approved = jdbc.queryForObject("""
                select count(*) from ap_decision
                where tenant_id = :tenant and result in ('APPROVED', 'CONDITIONALLY_APPROVED') and decided_at >= :since
                """, params, Long.class);
        Map<String, Object> rate = jdbc.queryForMap("""
                select count(*) as total,
                       count(*) filter (where result in ('APPROVED', 'CONDITIONALLY_APPROVED')) as approved
                from ap_decision where tenant_id = :tenant
                """, params);
        long total = ((Number) rate.get("total")).longValue();
        Double approvalRate = total == 0 ? null : ((Number) rate.get("approved")).doubleValue() / total;
        Double leadTime = jdbc.queryForObject("""
                select avg(extract(epoch from (decided_at - requested_at)) / 86400.0)
                from ap_case
                where tenant_id = :tenant and status in ('APPROVED', 'CONDITIONALLY_APPROVED')
                  and decided_at is not null and requested_at is not null
                """, params, Double.class);
        List<CaseStatistics.PartnerFigures> byPartner = jdbc.query(
                """
                select partner_bpnl, max(partner_name) as name,
                       count(*) filter (where status in (:open)) as open,
                       count(*) filter (where status in (:open) and due_date < :today) as overdue
                from ap_case where tenant_id = :tenant
                group by partner_bpnl
                having count(*) filter (where status in (:open)) > 0
                order by open desc, partner_bpnl
                """,
                params,
                (rs, i) -> new CaseStatistics.PartnerFigures(
                        rs.getString("partner_bpnl"), rs.getString("name"), rs.getLong("open"), rs.getLong("overdue")));
        return new CaseStatistics(
                open,
                overdue,
                byStatus.getOrDefault(CaseStatus.IN_REVIEW.name(), 0L),
                approved,
                leadTime == null ? null : Math.round(leadTime * 10) / 10.0,
                approvalRate,
                byStatus,
                byPartner);
    }

    @Override
    public boolean existsByPart(UUID partId) {
        return cases.existsByTenantIdAndPartId(tenant.tenantId(), partId);
    }

    @Override
    public boolean existsByPartner(String bpnl) {
        return cases.existsByTenantIdAndPartnerBpnl(tenant.tenantId(), bpnl);
    }

    @Override
    public boolean existsByRelation(UUID relationId) {
        return cases.existsByTenantIdAndRelationId(tenant.tenantId(), relationId);
    }

    @Override
    public boolean existsByLevel(String code) {
        return cases.existsByTenantIdAndLevelCode(tenant.tenantId(), code);
    }

    @Override
    public boolean existsByReason(String code) {
        return cases.existsByTenantIdAndReasonCode(tenant.tenantId(), code);
    }

    @Override
    public boolean existsByElement(String code) {
        Boolean exists = jdbc.queryForObject(
                "select exists(select 1 from ap_case where tenant_id = :tenant and request_elements @> cast(:probe as jsonb))",
                new MapSqlParameterSource()
                        .addValue("tenant", tenant.tenantId())
                        .addValue("probe", ApprovalJson.write(List.of(Map.of("code", code)))),
                Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    // ---------------------------------------------------------------------------------------------------------

    private static void apply(ApCaseEntity e, ApprovalCase c) {
        CaseHeader h = c.header();
        e.status = c.status().name();
        e.partnerBpnl = h.partner().bpnl();
        e.partnerName = h.partner().name();
        e.relationId = h.relationId();
        e.partId = h.part().id();
        e.partNumber = h.part().partNumber();
        e.partNameDe = h.part().nameDe();
        e.partNameEn = h.part().nameEn();
        e.partnerPartNumber = h.partnerPartNumber();
        e.reasonCode = h.reason().code();
        e.reasonNameDe = h.reason().nameDe();
        e.reasonNameEn = h.reason().nameEn();
        e.levelCode = h.level().code();
        e.levelNameDe = h.level().nameDe();
        e.levelNameEn = h.level().nameEn();
        e.dueDate = h.dueDate();
        e.remarks = h.remarks();
        e.externalReference = h.externalReference();
        e.requestedAt = c.request().requestedAt();
        e.requestedBy = c.request().requestedBy();
        e.requestElements = ApprovalJson.requestElements(c.request().elements());
        e.requestAttachments = ApprovalJson.documents(c.request().attachments());
        e.reviewFindings = ApprovalJson.findings(c.reviewFindings());
        e.currentRevision = c.currentSubmission().map(Submission::revision).orElse(null);
        e.submittedAt = c.submissions().stream()
                .map(Submission::submittedAt)
                .filter(java.util.Objects::nonNull)
                .reduce((a, b) -> b)
                .orElse(null);
        Optional<Decision> last = c.lastDecision();
        e.decidedAt = last.map(Decision::decidedAt).orElse(null);
        e.lastDecision = last.map(d -> d.result().name()).orElse(null);
    }

    private static void apply(ApSubmissionEntity row, Submission s, Instant now) {
        row.status = s.status().name();
        row.elements = ApprovalJson.evidence(s.elements());
        row.submittedAt = s.submittedAt();
        row.submittedBy = s.submittedBy();
        Seal seal = s.seal();
        if (seal != null) {
            row.payload = seal.payload();
            row.payloadHash = seal.payloadHash();
            row.signatureAlg = seal.signature().alg();
            row.signatureKid = seal.signature().kid();
            row.signatureJws = seal.signature().jws();
            row.signatureStatus = seal.signature().status().name();
        }
        row.updatedAt = now;
    }

    private static ApDecisionEntity decisionRow(String tenantId, UUID caseId, Decision d) {
        var row = new ApDecisionEntity();
        row.id = Ids.newId();
        row.tenantId = tenantId;
        row.caseId = caseId;
        row.revision = d.revision();
        row.result = d.result().name();
        row.conditions = ApprovalJson.strings(d.conditions());
        row.conditionDueDate = d.conditionDueDate();
        row.comment = d.comment();
        row.elementFindings = ApprovalJson.findings(d.elementFindings());
        row.decidedAt = d.decidedAt();
        row.decidedBy = d.decidedBy();
        row.payload = d.seal().payload();
        row.payloadHash = d.seal().payloadHash();
        row.signatureAlg = d.seal().signature().alg();
        row.signatureKid = d.seal().signature().kid();
        row.signatureJws = d.seal().signature().jws();
        row.signatureStatus = d.seal().signature().status().name();
        return row;
    }

    private static ApTimelineEntity timelineRow(String tenantId, UUID caseId, TimelineEntry entry) {
        var row = new ApTimelineEntity();
        row.id = entry.id();
        row.tenantId = tenantId;
        row.caseId = caseId;
        row.at = entry.at();
        row.actor = entry.actor();
        row.fromStatus = entry.from() == null ? null : entry.from().name();
        row.toStatus = entry.to().name();
        row.action = entry.action().name();
        row.comment = entry.comment();
        return row;
    }

    private ApprovalCase toDomain(ApCaseEntity e) {
        String t = tenant.tenantId();
        Map<Integer, Decision> decisionsByRevision = new HashMap<>();
        for (ApDecisionEntity d : decisions.findByTenantIdAndCaseIdOrderByRevision(t, e.id)) {
            decisionsByRevision.put(
                    d.revision,
                    new Decision(
                            d.revision,
                            DecisionResult.valueOf(d.result),
                            ApprovalJson.strings(d.conditions),
                            d.conditionDueDate,
                            d.comment,
                            ApprovalJson.findings(d.elementFindings),
                            d.decidedAt,
                            d.decidedBy,
                            new Seal(
                                    d.payload,
                                    d.payloadHash,
                                    signature(d.signatureAlg, d.signatureKid, d.signatureJws, d.signatureStatus))));
        }
        List<Submission> revisions = new ArrayList<>();
        for (ApSubmissionEntity s : submissions.findByTenantIdAndCaseIdOrderByRevision(t, e.id)) {
            Seal seal = s.payload == null
                    ? null
                    : new Seal(
                            s.payload,
                            s.payloadHash,
                            signature(s.signatureAlg, s.signatureKid, s.signatureJws, s.signatureStatus));
            revisions.add(Submission.restore(
                    s.revision,
                    SubmissionStatus.valueOf(s.status),
                    s.basedOnRevision,
                    ApprovalJson.evidence(s.elements),
                    s.submittedAt,
                    s.submittedBy,
                    seal,
                    decisionsByRevision.get(s.revision)));
        }
        List<TimelineEntry> entries = timeline.findByTenantIdAndCaseIdOrderBySeq(t, e.id).stream()
                .map(r -> new TimelineEntry(
                        r.id,
                        r.at,
                        r.actor,
                        r.fromStatus == null ? null : CaseStatus.valueOf(r.fromStatus),
                        CaseStatus.valueOf(r.toStatus),
                        CaseAction.valueOf(r.action),
                        r.comment))
                .toList();
        return ApprovalCase.restore(
                e.id,
                e.caseNumber,
                e.exchangeId,
                CaseRole.valueOf(e.ownRole),
                CaseSource.valueOf(e.source),
                CaseStatus.valueOf(e.status),
                header(e),
                new ApprovalRequest(
                        e.requestedAt,
                        e.requestedBy,
                        ApprovalJson.requestElements(e.requestElements),
                        ApprovalJson.documents(e.requestAttachments)),
                revisions,
                ApprovalJson.findings(e.reviewFindings),
                entries,
                new CaseMetadata(e.version, e.createdAt, e.createdBy, e.updatedAt, e.updatedBy));
    }

    private static CaseHeader header(ApCaseEntity e) {
        return new CaseHeader(
                new PartnerRef(e.partnerBpnl, e.partnerName),
                e.relationId,
                new PartRef(e.partId, e.partNumber, e.partNameDe, e.partNameEn),
                e.partnerPartNumber,
                new CatalogRef(e.reasonCode, e.reasonNameDe, e.reasonNameEn),
                new CatalogRef(e.levelCode, e.levelNameDe, e.levelNameEn),
                e.dueDate,
                e.remarks,
                e.externalReference);
    }

    private static CaseSummary summary(ApCaseEntity e) {
        CaseHeader h = header(e);
        return new CaseSummary(
                e.id,
                e.caseNumber,
                e.exchangeId,
                CaseRole.valueOf(e.ownRole),
                CaseSource.valueOf(e.source),
                CaseStatus.valueOf(e.status),
                h.partner(),
                e.relationId,
                h.part(),
                e.partnerPartNumber,
                h.reason(),
                h.level(),
                e.dueDate,
                e.remarks,
                e.currentRevision,
                e.requestedAt,
                e.submittedAt,
                e.decidedAt,
                e.lastDecision == null ? null : DecisionResult.valueOf(e.lastDecision),
                e.version,
                e.createdAt,
                e.updatedAt);
    }

    private static SignatureInfo signature(String alg, String kid, String jws, String status) {
        return new SignatureInfo(
                alg, kid, jws, status == null ? SignatureStatus.NOT_SIGNED : SignatureStatus.valueOf(status));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String like(String text) {
        String value = blankToNull(text);
        if (value == null) {
            return null;
        }
        String escaped = value.toLowerCase(java.util.Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
