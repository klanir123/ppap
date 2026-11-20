package com.msg.ppap.exchange.application;

import com.msg.ppap.approval.DocumentTransferStatus;
import com.msg.ppap.approval.application.port.in.PartnerSharingUseCase;
import com.msg.ppap.approval.application.port.in.PartnerSharingUseCase.PartnerDocument;
import com.msg.ppap.documents.api.DocumentInfo;
import com.msg.ppap.documents.api.Documents;
import com.msg.ppap.exchange.application.PullFailure.Outcome;
import com.msg.ppap.exchange.application.PullJobs.Steps;
import com.msg.ppap.exchange.application.port.out.DataPlaneStream;
import com.msg.ppap.exchange.application.port.out.DataspaceConnector;
import com.msg.ppap.exchange.application.port.out.DocumentTransferStore;
import com.msg.ppap.exchange.application.port.out.EndpointDataReference;
import com.msg.ppap.exchange.application.port.out.ExchangeMessageStore.NewMessage;
import com.msg.ppap.exchange.application.port.out.ExchangeMessageStore.StoredMessage;
import com.msg.ppap.exchange.application.port.out.PartnerEndpoint;
import com.msg.ppap.exchange.domain.Direction;
import com.msg.ppap.exchange.domain.ExchangeRecord;
import com.msg.ppap.exchange.domain.MessageStatus;
import com.msg.ppap.exchange.domain.RecordTypes;
import com.msg.ppap.exchange.domain.StepName;
import com.msg.ppap.exchange.domain.StepTarget;
import com.msg.ppap.platform.domain.Ids;
import com.msg.ppap.platform.domain.NotFoundException;
import com.msg.ppap.platform.domain.PpapException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Document transfer in all exchange modes (docs/api/pull-api.md, Ziel 2): after a submission (customer) or a request
 * (supplier) was received, every referenced partner document is fetched via the partner's asset {@code
 * ppap-documents-<own BPNL>} ({@code GET /<sha256>}), streamed into the documents module, which verifies the SHA-256
 * before storing ({@code DOCUMENT_HASH_MISMATCH}) and stores it content-addressed. Implements {@link
 * DocumentTransferStatus} for the approval module.
 */
@Service
public class DocumentTransferService implements DocumentTransferStatus {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentTransferService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    public static final String PENDING = "PENDING";
    public static final String FETCHED = "FETCHED";
    public static final String FAILED = "FAILED";
    public static final String NOT_AVAILABLE = "NOT_AVAILABLE";

    private final ExchangeContext context;
    private final DocumentTransferStore transfers;
    private final PartnerSharingUseCase sharing;
    private final PartnerDirectory partners;
    private final DataspaceConnector connector;
    private final Documents documents;

    public DocumentTransferService(
            ExchangeContext context,
            DocumentTransferStore transfers,
            PartnerSharingUseCase sharing,
            PartnerDirectory partners,
            DataspaceConnector connector,
            Documents documents) {
        this.context = context;
        this.transfers = transfers;
        this.sharing = sharing;
        this.partners = partners;
        this.connector = connector;
        this.documents = documents;
    }

    @Override
    public Map<String, DocumentTransferStatus.Transfer> transfers(UUID caseId) {
        var result = new LinkedHashMap<String, DocumentTransferStatus.Transfer>();
        for (DocumentTransferStore.Transfer t : transfers.findByCase(caseId)) {
            result.put(
                    t.sha256(),
                    new DocumentTransferStatus.Transfer(
                            t.status(), t.documentId(), t.hashVerified(), t.errorCode(), t.lastError(), t.updatedAt()));
        }
        return result;
    }

    /**
     * Queues the transfer of all partner documents of the case that are not available yet (idempotent).
     *
     * @return number of new transfers
     */
    public int scheduleForCase(UUID caseId, String correlationId) {
        int scheduled = 0;
        for (PartnerDocument d : sharing.partnerDocuments(caseId)) {
            Instant now = context.now();
            DocumentTransferStore.Transfer transfer = new DocumentTransferStore.Transfer(
                    Ids.newId(),
                    d.caseId(),
                    d.exchangeId(),
                    d.partnerBpnl(),
                    d.sha256(),
                    d.fileName(),
                    d.mediaType(),
                    d.size(),
                    PENDING,
                    null,
                    false,
                    null,
                    null,
                    null,
                    now);
            if (transfers.insertIfAbsent(transfer)) {
                queue(transfer, d.caseNumber(), correlationId);
                scheduled++;
            }
        }
        if (scheduled > 0) {
            LOG.info("{} document transfer(s) queued for case {}", scheduled, caseId);
        }
        return scheduled;
    }

    /**
     * Manual re-fetch ({@code POST …/documents/{sha256}/fetch}): queues a new attempt unless the document is already
     * fetched or a transfer is pending.
     *
     * @throws NotFoundException if the case references no such partner document
     */
    public DocumentTransferStore.Transfer refetch(UUID caseId, String sha256) {
        Optional<DocumentTransferStore.Transfer> existing = transfers.find(caseId, sha256);
        if (existing.isEmpty()) {
            scheduleForCase(caseId, ExchangeContext.correlationId());
            return transfers
                    .find(caseId, sha256)
                    .orElseThrow(() -> new NotFoundException("Partner document of case " + caseId, sha256));
        }
        DocumentTransferStore.Transfer t = existing.get();
        if (FETCHED.equals(t.status()) || PENDING.equals(t.status())) {
            return t;
        }
        Instant now = context.now();
        transfers.update(t.id(), PENDING, null, false, null, null, now);
        queue(t, null, ExchangeContext.correlationId());
        return transfers.find(caseId, sha256).orElseThrow();
    }

    private void queue(DocumentTransferStore.Transfer t, String caseNumber, String correlationId) {
        Instant now = context.now();
        UUID id = Ids.newId();
        UUID messageId = UUID.randomUUID();
        var envelope = new LinkedHashMap<String, Object>();
        envelope.put("transferId", t.id().toString());
        envelope.put("caseId", t.caseId().toString());
        envelope.put("sha256", t.sha256());
        envelope.put("fileName", t.fileName());
        envelope.put("mediaType", t.mediaType());
        envelope.put("size", t.size());
        context.store()
                .insertInbound(
                        new NewMessage(
                                id,
                                messageId,
                                Direction.INBOUND,
                                RecordTypes.PULL_DOCUMENT,
                                t.partnerBpnl(),
                                partners.name(t.partnerBpnl()).orElse(null),
                                t.caseId(),
                                caseNumber,
                                t.exchangeId(),
                                "sha256:" + t.sha256(),
                                correlationId == null ? ExchangeContext.correlationId() : correlationId,
                                JSON.writeValueAsString(envelope),
                                null,
                                now),
                        MessageStatus.PENDING,
                        null,
                        null);
        transfers.linkMessage(t.id(), id, now);
        context.live().exchangeChanged(t.caseId(), messageId, "PENDING");
    }

    /** Runs a claimed document job. */
    PullJobs.Result execute(StoredMessage message, Steps all) {
        ExchangeRecord r = message.record();
        JsonNode job = JSON.readTree(message.envelope());
        UUID transferId = UUID.fromString(job.path("transferId").asString());
        UUID caseId = UUID.fromString(job.path("caseId").asString());
        String sha256 = job.path("sha256").asString();
        Steps steps = all.target(StepTarget.DOCUMENT, sha256);
        String fileName = job.path("fileName").asString();
        String own = context.tenantBpnl();
        PartnerEndpoint partner = partners.endpoint(r.partnerBpnl(), StepName.CATALOG);
        String asset = ExchangeAssets.documentsAsset(own);
        EndpointDataReference edr = connector.obtainEndpoint(partner, asset, steps);
        long start = System.nanoTime();
        try (DataPlaneStream stream = connector.stream(edr, "/" + sha256, Map.of())) {
            var details = DtrAccess.details(
                    "assetId", asset,
                    "sha256", sha256,
                    "httpStatus", stream.status(),
                    "contentLength", stream.contentLength(),
                    "transferProcessId", edr.transferProcessId(),
                    "cachedEdr", edr.cached());
            if (!stream.successful()) {
                steps.failed(StepName.DOCUMENT_FETCH, elapsedMs(start), details);
                if (stream.status() == 404 || stream.status() == 403) {
                    transfers.update(
                            transferId,
                            NOT_AVAILABLE,
                            null,
                            false,
                            "DOCUMENT_NOT_AVAILABLE",
                            "Partner answered " + stream.status(),
                            context.now());
                    throw new PullFailure(
                            "DOCUMENT_NOT_AVAILABLE",
                            "Partner does not share document " + sha256 + " with us (" + stream.status() + ")",
                            Outcome.FAIL);
                }
                if (stream.status() == 401) {
                    connector.invalidateEndpoint(partner, asset);
                }
                throw new PullFailure(
                        "DOCUMENT_FETCH_FAILED", "Documents endpoint answered " + stream.status(), Outcome.RETRY);
            }
            DocumentInfo stored;
            try {
                stored = documents.importVerified(fileName, stream.body(), sha256, "partner:" + r.partnerBpnl());
            } catch (PpapException ex) {
                steps.ok(StepName.DOCUMENT_FETCH, elapsedMs(start), details);
                boolean mismatch = "DOCUMENT_HASH_MISMATCH".equals(ex.code());
                if (mismatch) {
                    steps.failed(StepName.HASH_CHECK, 0, Map.of("expected", sha256, "error", ex.getMessage()));
                }
                transfers.update(transferId, FAILED, null, false, ex.code(), ex.getMessage(), context.now());
                throw new PullFailure(ex.code(), ex.getMessage(), Outcome.REJECT, ex);
            }
            details.put("size", stored.size());
            steps.ok(StepName.DOCUMENT_FETCH, elapsedMs(start), details);
            steps.ok(
                    StepName.HASH_CHECK,
                    0,
                    DtrAccess.details(
                            "sha256", sha256, "documentId", stored.id().toString(), "mediaType", stored.mediaType()));
            transfers.update(transferId, FETCHED, stored.id(), true, null, null, context.now());
            LOG.info(
                    "Document {} of case {} fetched from {} ({} bytes)",
                    sha256,
                    caseId,
                    r.partnerBpnl(),
                    stored.size());
            return new PullJobs.Result(caseId, null);
        }
    }

    /** After a run: a job that will not be retried leaves its transfer in a final state. */
    void afterRun(UUID messageId) {
        context.store().findRecord(messageId).ifPresent(r -> {
            if (r.status() == MessageStatus.FAILED || r.status() == MessageStatus.REJECTED) {
                JsonNode job = context.store()
                        .find(messageId)
                        .map(m -> JSON.readTree(m.envelope()))
                        .orElse(null);
                if (job == null) {
                    return;
                }
                UUID transferId = UUID.fromString(job.path("transferId").asString());
                UUID caseId = UUID.fromString(job.path("caseId").asString());
                transfers
                        .find(caseId, job.path("sha256").asString())
                        .filter(t -> t.id().equals(transferId) && PENDING.equals(t.status()))
                        .ifPresent(t -> transfers.update(
                                t.id(), FAILED, null, false, r.errorCode(), r.lastError(), context.now()));
            }
        });
    }

    /** Transfers of a case (REST). */
    public List<DocumentTransferStore.Transfer> byCase(UUID caseId) {
        return transfers.findByCase(caseId);
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
