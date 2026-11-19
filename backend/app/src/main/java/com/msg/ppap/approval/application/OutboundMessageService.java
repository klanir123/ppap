package com.msg.ppap.approval.application;

import com.msg.ppap.approval.ApprovalCaseChanged;
import com.msg.ppap.approval.ApprovalEvent;
import com.msg.ppap.approval.CaseCancelled;
import com.msg.ppap.approval.CaseRequested;
import com.msg.ppap.approval.DecisionMade;
import com.msg.ppap.approval.ReviewStarted;
import com.msg.ppap.approval.SubmissionSubmitted;
import com.msg.ppap.approval.WorkStarted;
import com.msg.ppap.approval.application.port.in.OutboundMessage;
import com.msg.ppap.approval.application.port.in.OutboundMessageUseCase;
import com.msg.ppap.approval.domain.ApprovalCase;
import com.msg.ppap.approval.domain.CaseAction;
import com.msg.ppap.approval.domain.CaseRole;
import com.msg.ppap.approval.domain.CaseSource;
import com.msg.ppap.approval.domain.CaseStatus;
import com.msg.ppap.approval.domain.Decision;
import com.msg.ppap.approval.domain.ExchangeMode;
import com.msg.ppap.approval.domain.Seal;
import com.msg.ppap.approval.domain.Submission;
import com.msg.ppap.semantics.api.DigitalTwinIds;
import com.msg.ppap.semantics.api.EncodedPayload;
import com.msg.ppap.semantics.api.ExchangeCodec;
import com.msg.ppap.semantics.api.ExchangeModels;
import com.msg.ppap.semantics.api.Notification.MessageType;
import com.msg.ppap.semantics.api.StatusUpdateMessage;
import com.msg.ppap.semantics.api.SubmissionAvailableMessage;
import com.msg.ppap.semantics.api.WithdrawalMessage;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link OutboundMessageUseCase}: only cases that are linked to a partner via the dataspace produce messages – the
 * customer's own requests (source {@code LOCAL}) and the supplier's received requests (source {@code EXCHANGE}).
 * Manually captured and migrated cases stay local.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
public class OutboundMessageService implements OutboundMessageUseCase {

    private final ApprovalContext context;
    private final ExchangeCodec codec;
    private final ExchangeMessageMapper mapper;

    public OutboundMessageService(ApprovalContext context, ExchangeCodec codec, ExchangeMessageMapper mapper) {
        this.context = context;
        this.codec = codec;
        this.mapper = mapper;
    }

    @Override
    public Optional<OutboundMessage> messageFor(ApprovalEvent event) {
        boolean customer = isSide(event, CaseRole.CUSTOMER, CaseSource.LOCAL);
        boolean supplier = isSide(event, CaseRole.SUPPLIER, CaseSource.EXCHANGE);
        return switch (event) {
            case CaseRequested e
            when customer -> {
                ApprovalCase c = context.load(e.caseId());
                yield message(e, MessageType.PPAP_REQUEST, codec.encodeRequest(mapper.request(c)), null);
            }
            case CaseCancelled e
            when customer && CaseStatus.REQUESTED.name().equals(e.fromStatus()) ->
                message(
                        e,
                        MessageType.PPAP_REQUEST_WITHDRAWN,
                        codec.encodeWithdrawal(
                                new WithdrawalMessage(e.exchangeId(), e.reason(), e.occurredAt(), e.actor())),
                        null);
            case WorkStarted e when supplier -> statusUpdate(e, StatusUpdateMessage.IN_PROGRESS, e.revision());
            case ApprovalCaseChanged e
            when supplier && CaseAction.NEW_REVISION.name().equals(e.action()) ->
                statusUpdate(e, StatusUpdateMessage.IN_PROGRESS, e.revision());
            case SubmissionSubmitted e when supplier -> submission(e);
            case ReviewStarted e when customer -> statusUpdate(e, StatusUpdateMessage.IN_REVIEW, e.revision());
            case DecisionMade e
            when customer -> {
                Seal seal = context.load(e.caseId())
                        .submission(e.revision())
                        .decision()
                        .map(Decision::seal)
                        .orElseThrow();
                yield sealed(e, MessageType.PPAP_DECISION, ExchangeModels.PPAP_DECISION, seal);
            }
            default -> Optional.empty();
        };
    }

    /**
     * Submission according to the exchange mode of the revision (docs/api/pull-api.md, „Abläufe je Modus“): PUSH sends
     * the signed payload, PUSH_NOTIFY_PULL_PAYLOAD only announces it (the customer pulls it via the DTR), PULL sends
     * nothing (the customer polls).
     */
    private Optional<OutboundMessage> submission(SubmissionSubmitted e) {
        Submission revision = context.load(e.caseId()).submission(e.revision());
        Seal seal = revision.seal();
        ExchangeMode mode = revision.exchangeMode() == null ? ExchangeMode.PUSH : revision.exchangeMode();
        return switch (mode) {
            case PUSH -> sealed(e, MessageType.PPAP_SUBMISSION, ExchangeModels.PPAP_SUBMISSION, seal);
            case PUSH_NOTIFY_PULL_PAYLOAD ->
                message(
                        e,
                        MessageType.PPAP_SUBMISSION_AVAILABLE,
                        codec.encodeSubmissionAvailable(new SubmissionAvailableMessage(
                                e.exchangeId(),
                                e.revision(),
                                seal.payloadHash(),
                                DigitalTwinIds.shellId(e.exchangeId()),
                                DigitalTwinIds.submodelId(e.exchangeId(), e.revision()))),
                        null);
            case PULL -> Optional.empty();
        };
    }

    private Optional<OutboundMessage> statusUpdate(ApprovalEvent e, String status, Integer revision) {
        var update = new StatusUpdateMessage(e.exchangeId(), status, revision, e.occurredAt(), e.actor());
        return message(e, MessageType.PPAP_STATUS_UPDATE, codec.encodeStatusUpdate(update), null);
    }

    private static Optional<OutboundMessage> sealed(ApprovalEvent e, MessageType type, String modelUrn, Seal seal) {
        var payload = new EncodedPayload(modelUrn, seal.payloadBytes(), seal.payloadHash());
        return message(e, type, payload, seal.signature().jws());
    }

    private static Optional<OutboundMessage> message(
            ApprovalEvent e, MessageType type, EncodedPayload payload, String signature) {
        return Optional.of(new OutboundMessage(
                type, e.caseId(), e.caseNumber(), e.exchangeId(), e.partnerBpnl(), payload, signature));
    }

    private static boolean isSide(ApprovalEvent e, CaseRole role, CaseSource source) {
        return role.name().equals(e.ownRole()) && source.name().equals(e.source());
    }
}
