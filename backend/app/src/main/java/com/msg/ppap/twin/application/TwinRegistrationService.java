package com.msg.ppap.twin.application;

import com.msg.ppap.approval.application.port.in.PartnerSharingUseCase;
import com.msg.ppap.approval.application.port.in.PartnerSharingUseCase.TwinSource;
import com.msg.ppap.exchange.api.OutboxTaskHandler;
import com.msg.ppap.identity.TenantContext;
import com.msg.ppap.semantics.api.AasDescriptors;
import com.msg.ppap.semantics.api.AasDescriptors.SubmodelSpec;
import com.msg.ppap.semantics.api.AasDescriptors.TwinSpec;
import com.msg.ppap.semantics.api.DigitalTwinIds;
import com.msg.ppap.twin.application.port.out.TwinRegistry;
import com.msg.ppap.twin.application.port.out.TwinRegistryException;
import com.msg.ppap.twin.application.port.out.TwinStore;
import com.msg.ppap.twin.config.TwinProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Supplier side twin registration (docs/api/pull-api.md, „Digital Twin“) as local outbox task {@code
 * TWIN_REGISTRATION}: queued with the submission, executed before the {@code PPAP_SUBMISSION_AVAILABLE} notification
 * of the same exchange id, retried with the outbox backoff. Idempotent: the shell of the case is written as a whole
 * ({@code PUT}) with one submodel descriptor per submitted revision provided by pull; cancelled cases keep their shell
 * (history).
 */
@Service
public class TwinRegistrationService implements OutboxTaskHandler {

    public static final String TASK = "TWIN_REGISTRATION";
    private static final Logger LOG = LoggerFactory.getLogger(TwinRegistrationService.class);

    private final TwinRegistry registry;
    private final TwinStore store;
    private final PartnerSharingUseCase sharing;
    private final TenantContext tenant;
    private final TwinProperties properties;
    private final Clock clock;

    public TwinRegistrationService(
            TwinRegistry registry,
            TwinStore store,
            PartnerSharingUseCase sharing,
            TenantContext tenant,
            TwinProperties properties,
            Clock clock) {
        this.registry = registry;
        this.store = store;
        this.sharing = sharing;
        this.tenant = tenant;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String taskType() {
        return TASK;
    }

    @Override
    public void execute(OutboxTask task, StepRecorder recorder) {
        long start = System.nanoTime();
        var details = new LinkedHashMap<String, Object>();
        details.put("target", "DTR");
        details.put("shellId", DigitalTwinIds.shellId(task.exchangeId()));
        try {
            TwinSource source = sharing.twinSource(task.exchangeId())
                    .orElseThrow(() -> new TaskFailure("No supplier case for " + task.exchangeId(), false, null));
            if (!registry.configured()) {
                throw new TaskFailure("No Digital Twin Registry configured (PPAP_DTR_URL)", true, null);
            }
            TwinSpec spec = spec(source);
            details.put("targetRef", DigitalTwinIds.shellId(task.exchangeId()));
            details.put(
                    "submodelIds",
                    source.revisions().stream()
                            .map(r -> DigitalTwinIds.submodelId(task.exchangeId(), r))
                            .toList());
            String shellId = DigitalTwinIds.shellId(task.exchangeId());
            registry.putShell(shellId, AasDescriptors.shellDescriptor(spec));
            byte[] registered = registry.findShell(shellId)
                    .orElseThrow(() -> new TaskFailure("Shell " + shellId + " not readable after PUT", true, null));
            store.save(
                    task.exchangeId(),
                    source.caseId(),
                    shellId,
                    new String(registered, StandardCharsets.UTF_8),
                    clock.instant().truncatedTo(ChronoUnit.MILLIS));
            recorder.step(TASK, true, elapsedMs(start), details);
            LOG.info("Twin {} registered with revisions {}", shellId, source.revisions());
        } catch (TwinRegistryException ex) {
            details.put("error", ex.getMessage());
            if (ex.httpStatus() != null) {
                details.put("httpStatus", ex.httpStatus());
            }
            recorder.step(TASK, false, elapsedMs(start), details);
            throw new TaskFailure(ex.getMessage(), ex.retryable(), ex);
        } catch (TaskFailure ex) {
            details.put("error", ex.getMessage());
            recorder.step(TASK, false, elapsedMs(start), details);
            throw ex;
        }
    }

    /** Twin of a supplier case: shell + one submodel descriptor per pulled revision. */
    TwinSpec spec(TwinSource source) {
        String base = properties.dataplanePublicUrl() == null
                ? ""
                : properties.dataplanePublicUrl().replaceAll("/+$", "");
        List<SubmodelSpec> submodels = source.revisions().stream()
                .map(r -> new SubmodelSpec(r, base + "/" + source.exchangeId() + "/" + r, properties.dspUrl()))
                .toList();
        return new TwinSpec(
                source.exchangeId(),
                source.caseNumber(),
                source.customerBpnl(),
                tenant.tenantId(),
                source.customerPartNumber(),
                source.supplierPartNumber(),
                submodels);
    }

    /** Registered twin (diagnosis). */
    public Optional<TwinStore.StoredTwin> find(UUID exchangeId) {
        return store.find(exchangeId);
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
