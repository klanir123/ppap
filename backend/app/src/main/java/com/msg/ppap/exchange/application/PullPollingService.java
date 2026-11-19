package com.msg.ppap.exchange.application;

import com.msg.ppap.approval.application.port.in.PartnerSharingUseCase;
import com.msg.ppap.approval.application.port.in.PartnerSharingUseCase.AwaitedSubmission;
import com.msg.ppap.exchange.application.SubmissionPullService.Trigger;
import com.msg.ppap.exchange.application.port.out.CatalogOffer;
import com.msg.ppap.exchange.application.port.out.DataspaceConnector;
import com.msg.ppap.exchange.application.port.out.DataspaceException;
import com.msg.ppap.exchange.application.port.out.EndpointDataReference;
import com.msg.ppap.exchange.application.port.out.PartnerEndpoint;
import com.msg.ppap.exchange.config.PullProperties;
import com.msg.ppap.exchange.domain.ExchangeStep;
import com.msg.ppap.exchange.domain.StepName;
import com.msg.ppap.exchange.domain.StepStatus;
import com.msg.ppap.exchange.domain.StepTarget;
import com.msg.ppap.masterdata.api.MasterdataQueries;
import com.msg.ppap.semantics.api.AasDescriptors.ShellView;
import com.msg.ppap.semantics.api.DigitalTwinIds;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Mode PULL, customer side (docs/api/pull-api.md, „Abläufe je Modus“): periodically ({@code
 * PPAP_PULL_POLL_INTERVAL}) the DTRs of the suppliers are asked for the own open requests (lookup by {@code
 * ppapExchangeId}); a submodel descriptor of a revision newer than the last received one queues a pull job. Partners
 * without DTR offer are skipped (cached), so PUSH-only suppliers cost one catalog request per cache period. The
 * customer does not need to know the supplier's mode: in PUSH_NOTIFY_PULL_PAYLOAD the poller may find the revision
 * before the notification arrives – both lead to the same (idempotent) job.
 */
@Service
public class PullPollingService {

    private static final Logger LOG = LoggerFactory.getLogger(PullPollingService.class);

    private final PartnerSharingUseCase sharing;
    private final MasterdataQueries masterdata;
    private final DtrAccess dtr;
    private final DataspaceConnector connector;
    private final SubmissionPullService pulls;
    private final ExchangeContext context;
    private final PullProperties properties;
    private final AtomicBoolean running = new AtomicBoolean();

    @SuppressWarnings("java:S107")
    public PullPollingService(
            PartnerSharingUseCase sharing,
            MasterdataQueries masterdata,
            DtrAccess dtr,
            DataspaceConnector connector,
            SubmissionPullService pulls,
            ExchangeContext context,
            PullProperties properties) {
        this.sharing = sharing;
        this.masterdata = masterdata;
        this.dtr = dtr;
        this.connector = connector;
        this.pulls = pulls;
        this.context = context;
        this.properties = properties;
    }

    /**
     * One polling round over all open requests.
     *
     * @return number of queued pull jobs
     */
    public int poll() {
        if (!properties.pollEnabled() || !running.compareAndSet(false, true)) {
            return 0;
        }
        int queued = 0;
        try {
            for (AwaitedSubmission awaited : sharing.awaitedSubmissions()) {
                try {
                    queued += poll(awaited);
                } catch (DataspaceException ex) {
                    LOG.debug(
                            "Polling {} at {} failed: {}",
                            awaited.exchangeId(),
                            awaited.supplierBpnl(),
                            ex.getMessage());
                } catch (RuntimeException ex) {
                    LOG.warn("Polling {} at {} failed", awaited.exchangeId(), awaited.supplierBpnl(), ex);
                }
            }
        } finally {
            running.set(false);
        }
        return queued;
    }

    int poll(AwaitedSubmission awaited) {
        int next = awaited.lastRevision() + 1;
        // a job for the next revision exists (queued by notification or an earlier round): nothing to discover
        if (context.store()
                .findInbound(awaited.supplierBpnl(), SubmissionPullService.messageId(awaited.exchangeId(), next))
                .isPresent()) {
            return 0;
        }
        Optional<MasterdataQueries.PartnerInfo> partnerInfo =
                masterdata.findPartner(awaited.supplierBpnl()).filter(MasterdataQueries.PartnerInfo::exchangeReady);
        if (partnerInfo.isEmpty()) {
            return 0;
        }
        PartnerEndpoint supplier = new PartnerEndpoint(
                partnerInfo.get().bpnl(),
                partnerInfo.get().did(),
                partnerInfo.get().dspEndpoint());
        List<ExchangeStep> discovery = new ArrayList<>();
        Optional<CatalogOffer> offer = dtr.offer(
                supplier,
                (step, status, duration, details) -> discovery.add(step(step, status, duration.toMillis(), details)));
        if (offer.isEmpty()) {
            return 0;
        }
        EndpointDataReference edr = connector.obtainEndpoint(
                supplier,
                offer.get(),
                (step, status, duration, details) -> discovery.add(step(step, status, duration.toMillis(), details)));
        DtrAccess.Lookup<List<String>> lookup = dtr.lookup(edr, awaited.exchangeId());
        String shellId = DigitalTwinIds.shellId(awaited.exchangeId());
        if (!lookup.value().contains(shellId)) {
            return 0;
        }
        discovery.add(step(
                StepName.DTR_LOOKUP,
                StepStatus.OK,
                lookup.durationMs(),
                DtrAccess.details("httpStatus", lookup.httpStatus(), "shells", lookup.value(), "phase", "poll")));
        DtrAccess.Lookup<Optional<ShellView>> shell = dtr.shell(edr, shellId);
        if (shell.value().isEmpty()) {
            return 0;
        }
        discovery.add(step(
                StepName.SHELL_DESCRIPTOR,
                StepStatus.OK,
                shell.durationMs(),
                DtrAccess.details("httpStatus", shell.httpStatus(), "shellId", shellId, "phase", "poll")));
        int queued = 0;
        for (int revision = next; revision < next + Math.max(1, properties.lookahead()); revision++) {
            if (shell.value()
                    .get()
                    .submodel(DigitalTwinIds.submodelId(awaited.exchangeId(), revision))
                    .isEmpty()) {
                break;
            }
            if (pulls.schedule(
                    supplier.bpnl(),
                    awaited.exchangeId(),
                    revision,
                    null,
                    Trigger.POLL,
                    null,
                    revision == next ? discovery : List.of())) {
                queued++;
            }
        }
        return queued;
    }

    private ExchangeStep step(StepName step, StepStatus status, long durationMs, Map<String, Object> details) {
        return new ExchangeStep(1, step, status, context.now(), durationMs, StepTarget.DTR.apply(details, null));
    }
}
