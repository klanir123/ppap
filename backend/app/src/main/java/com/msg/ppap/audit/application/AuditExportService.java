package com.msg.ppap.audit.application;

import com.msg.ppap.approval.application.port.in.CaseExportUseCase;
import com.msg.ppap.audit.application.port.out.AuditLog;
import com.msg.ppap.audit.domain.AuditRecord;
import com.msg.ppap.exchange.api.CaseExchangeRecords;
import com.msg.ppap.identity.ActorProvider;
import com.msg.ppap.identity.PayloadSignature;
import com.msg.ppap.identity.PayloadSigner;
import com.msg.ppap.identity.TenantContext;
import com.msg.ppap.platform.domain.NotFoundException;
import com.msg.ppap.semantics.api.CanonicalJson;
import com.msg.ppap.semantics.api.PayloadHash;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Audit export of an approval case (UC-11, docs/api/cascade-transparency-api.md §4): case, timeline, submissions (hash,
 * signature), decisions, exchange records with steps and the hash-chained audit events, serialized as RFC 8785
 * canonical JSON and signed as detached JWS (ES256, {@code b64:false}) with the tenant key – verifiable with {@code
 * /.well-known/jwks.json} like every exchanged payload (ADR-0008).
 */
@Service
public class AuditExportService {

    /** Format of the export (bumped on incompatible changes). */
    public static final String FORMAT = "msg-ppap-audit-export/1";

    private final CaseExportUseCase cases;
    private final CaseExchangeRecords exchange;
    private final AuditLog log;
    private final PayloadSigner signer;
    private final TenantContext tenant;
    private final ActorProvider actors;
    private final Clock clock;

    @SuppressWarnings("java:S107")
    public AuditExportService(
            CaseExportUseCase cases,
            CaseExchangeRecords exchange,
            AuditLog log,
            PayloadSigner signer,
            TenantContext tenant,
            ActorProvider actors,
            Clock clock) {
        this.cases = cases;
        this.exchange = exchange;
        this.log = log;
        this.signer = signer;
        this.tenant = tenant;
        this.actors = actors;
        this.clock = clock;
    }

    /** Builds and signs the export of a case. */
    @Transactional(readOnly = true)
    public SignedExport export(UUID caseId) {
        Map<String, Object> snapshot =
                cases.export(caseId).orElseThrow(() -> new NotFoundException("Approval case", caseId));
        List<AuditRecord> chain = log.chain(tenant.tenantId(), "APPROVAL_CASE", caseId.toString());
        List<Map<String, Object>> events = new ArrayList<>();
        boolean chainValid = true;
        String previous = null;
        for (AuditRecord record : chain) {
            boolean valid = record.hashValid() && Objects.equals(previous, record.prevHash());
            chainValid &= valid;
            previous = record.hash();
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("id", record.id().toString());
            event.put("occurredAt", record.occurredAt().toString());
            event.put("actor", record.actor());
            event.put("action", record.action());
            event.put("objectType", record.objectType());
            event.put("objectId", record.objectId());
            event.put("details", record.details());
            event.put("prevHash", record.prevHash());
            event.put("hash", record.hash());
            events.add(event);
        }
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("format", FORMAT);
        export.put("tenant", Map.of("bpnl", tenant.tenantId(), "name", tenant.tenantName()));
        export.put("case", snapshot.get("case"));
        export.put("timeline", snapshot.get("timeline"));
        export.put("submissions", snapshot.get("submissions"));
        export.put("decisions", snapshot.get("decisions"));
        export.put("exchangeRecords", exchange.recordsOfCase(caseId));
        export.put("auditEvents", events);
        export.put("auditChainValid", chainValid);
        export.put("exportedAt", clock.instant().truncatedTo(ChronoUnit.MILLIS).toString());
        export.put("exportedBy", actors.currentActor());
        byte[] canonical = CanonicalJson.canonicalize(export);
        PayloadSignature signature = signer.sign(canonical);
        return new SignedExport(
                String.valueOf(snapshot.get("caseNumber")),
                canonical,
                "sha256:" + PayloadHash.sha256Hex(canonical),
                signature.jws(),
                signature.kid());
    }

    /**
     * A signed export.
     *
     * @param content RFC 8785 canonical JSON (UTF-8), exactly the signed bytes
     * @param jws detached JWS over {@code content}
     */
    public record SignedExport(String caseNumber, byte[] content, String payloadHash, String jws, String kid) {

        public SignedExport {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }

        /** Content as text. */
        public String text() {
            return new String(content, StandardCharsets.UTF_8);
        }
    }
}
