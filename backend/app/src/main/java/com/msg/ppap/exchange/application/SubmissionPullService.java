package com.msg.ppap.exchange.application;

import com.msg.ppap.approval.application.port.in.InboundMessage;
import com.msg.ppap.approval.application.port.in.InboundResult;
import com.msg.ppap.approval.application.port.in.PartnerSharingUseCase;
import com.msg.ppap.approval.application.port.in.ReceiveSubmissionUseCase;
import com.msg.ppap.exchange.application.PullFailure.Outcome;
import com.msg.ppap.exchange.application.PullJobs.Steps;
import com.msg.ppap.exchange.application.port.out.DataPlaneResponse;
import com.msg.ppap.exchange.application.port.out.DataspaceConnector;
import com.msg.ppap.exchange.application.port.out.DataspaceException;
import com.msg.ppap.exchange.application.port.out.EndpointDataReference;
import com.msg.ppap.exchange.application.port.out.ExchangeMessageStore.NewMessage;
import com.msg.ppap.exchange.application.port.out.ExchangeMessageStore.StoredMessage;
import com.msg.ppap.exchange.application.port.out.PartnerEndpoint;
import com.msg.ppap.exchange.domain.Direction;
import com.msg.ppap.exchange.domain.ExchangeRecord;
import com.msg.ppap.exchange.domain.ExchangeStep;
import com.msg.ppap.exchange.domain.MessageStatus;
import com.msg.ppap.exchange.domain.RecordTypes;
import com.msg.ppap.exchange.domain.StepName;
import com.msg.ppap.exchange.domain.StepStatus;
import com.msg.ppap.exchange.domain.StepTarget;
import com.msg.ppap.identity.SignatureVerifier;
import com.msg.ppap.platform.domain.Ids;
import com.msg.ppap.platform.domain.PpapException;
import com.msg.ppap.semantics.api.AasDescriptors.ShellView;
import com.msg.ppap.semantics.api.AasDescriptors.SubmodelView;
import com.msg.ppap.semantics.api.AasDescriptors.SubprotocolBody;
import com.msg.ppap.semantics.api.DigitalTwinIds;
import com.msg.ppap.semantics.api.PayloadHash;
import com.msg.ppap.semantics.api.PayloadValidationException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Customer side pull of a submitted revision via the supplier's Digital Twin Registry (docs/api/pull-api.md,
 * „Kundenseitiger Abruf“): DTR offer (catalog by type) → EDR → lookup by {@code ppapExchangeId} → shell descriptor →
 * submodel descriptor of the revision → {@code subprotocolBody} → EDR for the submodel asset → {@code GET} → hash →
 * signature (partner JWKS) → {@link ReceiveSubmissionUseCase} → document transfer. Triggered by a {@code
 * PPAP_SUBMISSION_AVAILABLE} notification or by the poller (mode PULL).
 */
@Service
public class SubmissionPullService {

    private static final Logger LOG = LoggerFactory.getLogger(SubmissionPullService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Response headers of the submodel endpoint (the JWS is detached, the body is the canonical payload). */
    public static final String HASH_HEADER = "X-Ppap-Payload-Hash";

    public static final String SIGNATURE_HEADER = "X-Ppap-Signature";

    /** Trigger of a pull. */
    public enum Trigger {
        /** {@code PPAP_SUBMISSION_AVAILABLE} received (mode PUSH_NOTIFY_PULL_PAYLOAD). */
        NOTIFICATION,
        /** Found by polling the supplier's DTR (mode PULL). */
        POLL
    }

    private final ExchangeContext context;
    private final PartnerDirectory partners;
    private final DataspaceConnector connector;
    private final DtrAccess dtr;
    private final PartnerKeyService partnerKeys;
    private final SignatureVerifier verifier;
    private final ReceiveSubmissionUseCase receiver;
    private final PartnerSharingUseCase sharing;
    private final DocumentTransferService documents;
    private final TransactionTemplate transaction;

    @SuppressWarnings("java:S107")
    public SubmissionPullService(
            ExchangeContext context,
            PartnerDirectory partners,
            DataspaceConnector connector,
            DtrAccess dtr,
            PartnerKeyService partnerKeys,
            SignatureVerifier verifier,
            ReceiveSubmissionUseCase receiver,
            PartnerSharingUseCase sharing,
            DocumentTransferService documents,
            PlatformTransactionManager transactionManager) {
        this.context = context;
        this.partners = partners;
        this.connector = connector;
        this.dtr = dtr;
        this.partnerKeys = partnerKeys;
        this.verifier = verifier;
        this.receiver = receiver;
        this.sharing = sharing;
        this.documents = documents;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** Deterministic message id of the pull of a revision (one job per exchange id and revision). */
    public static UUID messageId(UUID exchangeId, int revision) {
        return UUID.nameUUIDFromBytes(
                ("ppap-pull-submission:" + exchangeId + ":" + revision).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Queues the pull of a revision (idempotent per exchange id and revision).
     *
     * @param payloadHash announced hash ({@code PPAP_SUBMISSION_AVAILABLE}), {@code null} when polled
     * @param discovery steps already performed (poller), recorded in the job
     * @return {@code true} if a new job was queued
     */
    public boolean schedule(
            String supplierBpnl,
            UUID exchangeId,
            int revision,
            String payloadHash,
            Trigger trigger,
            String correlationId,
            List<ExchangeStep> discovery) {
        Optional<PartnerSharingUseCase.CaseRef> own = sharing.findCase(exchangeId, "CUSTOMER");
        Instant now = context.now();
        UUID id = Ids.newId();
        var envelope = new java.util.LinkedHashMap<String, Object>();
        envelope.put("exchangeId", exchangeId.toString());
        envelope.put("revision", revision);
        if (payloadHash != null) {
            envelope.put("payloadHash", payloadHash);
        }
        envelope.put("trigger", trigger.name());
        boolean inserted = context.store()
                .insertInbound(
                        new NewMessage(
                                id,
                                messageId(exchangeId, revision),
                                Direction.INBOUND,
                                RecordTypes.PULL_SUBMISSION,
                                supplierBpnl,
                                partners.name(supplierBpnl).orElse(null),
                                own.map(PartnerSharingUseCase.CaseRef::caseId).orElse(null),
                                own.map(PartnerSharingUseCase.CaseRef::caseNumber)
                                        .orElse(null),
                                exchangeId,
                                payloadHash,
                                correlationId == null ? ExchangeContext.correlationId() : correlationId,
                                JSON.writeValueAsString(envelope),
                                null,
                                now),
                        MessageStatus.PENDING,
                        null,
                        null);
        if (inserted) {
            discovery.forEach(step -> context.store().addStep(id, step));
            context.live()
                    .exchangeChanged(
                            own.map(PartnerSharingUseCase.CaseRef::caseId).orElse(null), id, "PENDING");
            LOG.info("Pull of revision {} of {} from {} queued ({})", revision, exchangeId, supplierBpnl, trigger);
        }
        return inserted;
    }

    /** Runs a claimed pull job. */
    PullJobs.Result execute(StoredMessage message, Steps all) {
        ExchangeRecord r = message.record();
        JsonNode job = JSON.readTree(message.envelope());
        UUID exchangeId = UUID.fromString(job.path("exchangeId").asString());
        int revision = job.path("revision").asInt();
        String announcedHash =
                job.path("payloadHash").isString() ? job.path("payloadHash").asString() : null;
        Trigger trigger = Trigger.valueOf(job.path("trigger").asString(Trigger.NOTIFICATION.name()));
        PartnerEndpoint supplier = partners.endpoint(r.partnerBpnl(), StepName.CATALOG);

        Steps dtrSteps = all.target(StepTarget.DTR, null);
        // 1. DTR of the supplier: offer + EDR, lookup and shell descriptor
        Steps steps = dtrSteps;
        EndpointDataReference dtrEdr = dtr.endpoint(supplier, steps, r.attempts() > 1);
        String shellId = DigitalTwinIds.shellId(exchangeId);
        ShellView shell = readShell(supplier, dtrEdr, exchangeId, shellId, steps);
        String submodelId = DigitalTwinIds.submodelId(exchangeId, revision);
        SubmodelView submodel = shell.submodel(submodelId)
                .orElseThrow(() -> new PullFailure(
                        "SUBMODEL_NOT_REGISTERED",
                        "Shell " + shellId + " has no submodel descriptor " + submodelId + " (revision " + revision
                                + ")",
                        Outcome.RETRY));
        SubprotocolBody body = submodel.dsp()
                .orElseThrow(() -> new PullFailure(
                        "INVALID_DESCRIPTOR",
                        "Submodel " + submodelId + " has no DSP subprotocolBody: " + submodel.subprotocolBody(),
                        Outcome.REJECT));

        // 2. submodel via the asset named in subprotocolBody (counterparty identity = the partner's DID)
        steps = all.target(StepTarget.SUBMODEL, submodelId);
        PartnerEndpoint submodelProvider = new PartnerEndpoint(supplier.bpnl(), supplier.did(), body.dspEndpoint());
        EndpointDataReference edr = connector.obtainEndpoint(submodelProvider, body.assetId(), steps);
        long start = System.nanoTime();
        DataPlaneResponse response = connector.call(
                edr,
                "GET",
                submodelPath(submodel.href(), exchangeId, revision),
                null,
                null,
                Map.of("Accept", "application/json"));
        long fetchMs = elapsedMs(start);
        var fetchDetails = DtrAccess.details(
                "assetId", body.assetId(),
                "submodelId", submodelId,
                "httpStatus", response.status(),
                "size", response.body().length,
                "transferProcessId", edr.transferProcessId(),
                "cachedEdr", edr.cached());
        if (!response.successful()) {
            fetchDetails.put("error", response.bodySnippet());
            steps.failed(StepName.SUBMODEL_FETCH, fetchMs, fetchDetails);
            if (response.status() == 401 || response.status() == 403) {
                connector.invalidateEndpoint(submodelProvider, body.assetId());
            }
            throw new PullFailure(
                    "SUBMODEL_FETCH_FAILED", "Submodel endpoint answered " + response.status(), Outcome.RETRY);
        }
        steps.ok(StepName.SUBMODEL_FETCH, fetchMs, fetchDetails);
        byte[] payload = response.body();

        // 3. hash: over the received canonical bytes, equal to the announced and the served hash
        String actual = PayloadHash.of(payload);
        String served = response.header(HASH_HEADER);
        boolean hashOk =
                (announcedHash == null || announcedHash.equals(actual)) && (served == null || served.equals(actual));
        var hashDetails = DtrAccess.details("payloadHash", actual, "announced", announcedHash, "served", served);
        if (!hashOk) {
            steps.failed(StepName.HASH_CHECK, 0, hashDetails);
            throw new PullFailure(
                    "REJECTED_PAYLOAD_HASH_MISMATCH",
                    "Pulled payload has " + actual + ", announced " + announcedHash + ", served " + served,
                    Outcome.REJECT);
        }
        steps.ok(StepName.HASH_CHECK, 0, hashDetails);

        // 4. signature against the supplier's keys (JWKS via EDC if the kid is unknown); the data plane does not
        // forward response headers, so the detached JWS is read from the sibling resource …/signature
        String headerJws = response.header(SIGNATURE_HEADER);
        String jws = headerJws != null
                ? headerJws
                : fetchSignature(edr, submodel.href(), exchangeId, revision, actual, steps);
        checkSignature(supplier.bpnl(), payload, jws, steps, all.target(StepTarget.PUBLIC_KEYS, null));

        // 5. processing, then the documents of the revision
        String mode = trigger == Trigger.POLL ? "PULL" : "PUSH_NOTIFY_PULL_PAYLOAD";
        long processing = System.nanoTime();
        InboundResult result;
        try {
            result = transaction.execute(
                    status -> receiver.receiveSubmission(new InboundMessage(supplier.bpnl(), payload, jws, mode)));
        } catch (PayloadValidationException ex) {
            steps.failed(StepName.PROCESSING, elapsedMs(processing), Map.of("error", abbreviate(ex.getMessage())));
            throw new PullFailure("INVALID", ex.getMessage() + " " + ex.violations(), Outcome.REJECT, ex);
        } catch (PpapException ex) {
            steps.failed(
                    StepName.PROCESSING, elapsedMs(processing), Map.of("error", ex.code() + ": " + ex.getMessage()));
            throw new PullFailure(
                    com.msg.ppap.exchange.domain.InboundRejection.fromErrorCode(ex.code())
                            .name(),
                    ex.code() + ": " + ex.getMessage(),
                    Outcome.REJECT,
                    ex);
        }
        steps.onStep(
                StepName.PROCESSING,
                StepStatus.OK,
                java.time.Duration.ofNanos(System.nanoTime() - processing),
                DtrAccess.details(
                        "caseId", String.valueOf(result.caseId()), "applied", result.applied(), "mode", mode));
        if (result.caseId() != null) {
            documents.scheduleForCase(result.caseId(), r.correlationId());
        }
        return new PullJobs.Result(result.caseId(), result.caseNumber());
    }

    private String fetchSignature(
            EndpointDataReference edr, String href, UUID exchangeId, int revision, String hash, Steps steps) {
        long start = System.nanoTime();
        DataPlaneResponse response = connector.call(
                edr,
                "GET",
                submodelPath(href, exchangeId, revision) + "/signature",
                null,
                null,
                Map.of("Accept", "application/json"));
        if (!response.successful()) {
            steps.failed(
                    StepName.SIGNATURE_CHECK,
                    elapsedMs(start),
                    DtrAccess.details("httpStatus", response.status(), "error", response.bodySnippet()));
            throw new PullFailure(
                    "SIGNATURE_FETCH_FAILED", "Signature resource answered " + response.status(), Outcome.RETRY);
        }
        JsonNode body = JSON.readTree(response.body());
        String served = body.path("payloadHash").asString(null);
        if (served != null && !served.equals(hash)) {
            steps.failed(StepName.HASH_CHECK, 0, DtrAccess.details("payloadHash", hash, "signed", served));
            throw new PullFailure(
                    "REJECTED_PAYLOAD_HASH_MISMATCH",
                    "Signature refers to " + served + ", payload is " + hash,
                    Outcome.REJECT);
        }
        return body.path("signature").asString(null);
    }

    private ShellView readShell(
            PartnerEndpoint supplier, EndpointDataReference edr, UUID exchangeId, String shellId, Steps steps) {
        try {
            DtrAccess.Lookup<List<String>> lookup = dtr.lookup(edr, exchangeId);
            steps.ok(
                    StepName.DTR_LOOKUP,
                    lookup.durationMs(),
                    DtrAccess.details("httpStatus", lookup.httpStatus(), "shells", lookup.value()));
            if (!lookup.value().contains(shellId)) {
                throw new PullFailure(
                        "TWIN_NOT_FOUND",
                        "No twin " + shellId + " visible in the DTR of " + supplier.bpnl(),
                        Outcome.RETRY);
            }
            DtrAccess.Lookup<Optional<ShellView>> shell = dtr.shell(edr, shellId);
            if (shell.value().isEmpty()) {
                steps.failed(
                        StepName.SHELL_DESCRIPTOR,
                        shell.durationMs(),
                        DtrAccess.details("httpStatus", shell.httpStatus(), "shellId", shellId));
                throw new PullFailure("TWIN_NOT_FOUND", "Shell descriptor " + shellId + " not found", Outcome.RETRY);
            }
            steps.ok(
                    StepName.SHELL_DESCRIPTOR,
                    shell.durationMs(),
                    DtrAccess.details(
                            "httpStatus", shell.httpStatus(),
                            "shellId", shellId,
                            "submodels", shell.value().get().submodels().size()));
            return shell.value().get();
        } catch (DataspaceException ex) {
            steps.failed(
                    ex.step(),
                    0,
                    DtrAccess.details("error", abbreviate(ex.getMessage()), "httpStatus", ex.httpStatus()));
            if (ex.httpStatus() != null && (ex.httpStatus() == 401 || ex.httpStatus() == 403)) {
                dtr.invalidate(supplier);
            }
            throw ex;
        }
    }

    private void checkSignature(String supplierBpnl, byte[] payload, String jws, Steps steps, Steps keySteps) {
        long start = System.nanoTime();
        if (jws == null || jws.isBlank()) {
            steps.failed(StepName.SIGNATURE_CHECK, 0, Map.of("result", "MISSING"));
            throw new PullFailure(
                    "REJECTED_SIGNATURE_INVALID", "Submodel response carries no signature header", Outcome.REJECT);
        }
        String kid = verifier.keyId(jws);
        if (kid != null && partnerKeys.find(supplierBpnl, kid).isEmpty()) {
            try {
                partnerKeys.fetch(supplierBpnl, (step, status, duration, details) -> {
                    if (status == StepStatus.FAILED) {
                        keySteps.onStep(step, status, duration, details);
                    }
                });
            } catch (DataspaceException ex) {
                keySteps.failed(StepName.KEY_FETCH, 0, Map.of("kid", kid, "error", abbreviate(ex.getMessage())));
            }
        }
        SignatureVerifier.Result result = verifier.verify(payload, jws, supplierBpnl);
        var details = DtrAccess.details("kid", kid, "result", result.name());
        if (result == SignatureVerifier.Result.INVALID) {
            steps.failed(StepName.SIGNATURE_CHECK, elapsedMs(start), details);
            throw new PullFailure(
                    "REJECTED_SIGNATURE_INVALID", "Signature of the pulled submission is invalid", Outcome.REJECT);
        }
        // UNKNOWN_KEY: processed as UNVERIFIED like a pushed submission
        if (result == SignatureVerifier.Result.VALID) {
            steps.ok(StepName.SIGNATURE_CHECK, elapsedMs(start), details);
        } else {
            steps.failed(StepName.SIGNATURE_CHECK, elapsedMs(start), details);
        }
    }

    /**
     * Path to request at the EDR endpoint for the submodel: the part of the descriptor's {@code href} after the data
     * plane's public API base ({@code …/api/public}); falls back to {@code /<exchangeId>/<revision>}.
     */
    static String submodelPath(String href, UUID exchangeId, int revision) {
        String fallback = "/" + exchangeId + "/" + revision;
        if (href == null || href.isBlank()) {
            return fallback;
        }
        int publicApi = href.indexOf("/api/public");
        if (publicApi >= 0) {
            String rest = href.substring(publicApi + "/api/public".length());
            return rest.isEmpty() ? fallback : rest;
        }
        return fallback;
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static String abbreviate(String text) {
        return text == null || text.length() <= 500 ? text : text.substring(0, 500) + "…";
    }
}
