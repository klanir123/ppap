package com.msg.ppap.approval.application;

import com.msg.ppap.approval.application.port.in.InboundMessage;
import com.msg.ppap.approval.application.port.in.InboundResult;
import com.msg.ppap.approval.application.port.in.ReceiveDecisionUseCase;
import com.msg.ppap.approval.application.port.in.ReceiveRequestUseCase;
import com.msg.ppap.approval.application.port.in.ReceiveStatusUpdateUseCase;
import com.msg.ppap.approval.application.port.in.ReceiveSubmissionUseCase;
import com.msg.ppap.approval.application.port.in.ReceiveWithdrawalUseCase;
import com.msg.ppap.approval.application.port.out.CaseNumberGenerator;
import com.msg.ppap.approval.domain.Actor;
import com.msg.ppap.approval.domain.ApprovalCase;
import com.msg.ppap.approval.domain.ApprovalErrors;
import com.msg.ppap.approval.domain.ApprovalRequest;
import com.msg.ppap.approval.domain.CaseHeader;
import com.msg.ppap.approval.domain.CaseRole;
import com.msg.ppap.approval.domain.CaseStatus;
import com.msg.ppap.approval.domain.CatalogRef;
import com.msg.ppap.approval.domain.Clarification;
import com.msg.ppap.approval.domain.Decision;
import com.msg.ppap.approval.domain.DecisionResult;
import com.msg.ppap.approval.domain.DocumentRef;
import com.msg.ppap.approval.domain.ElementSnapshot;
import com.msg.ppap.approval.domain.EvidenceType;
import com.msg.ppap.approval.domain.ExchangeMode;
import com.msg.ppap.approval.domain.PartRef;
import com.msg.ppap.approval.domain.PartnerRef;
import com.msg.ppap.approval.domain.RequestedElement;
import com.msg.ppap.approval.domain.Requirement;
import com.msg.ppap.approval.domain.Seal;
import com.msg.ppap.approval.domain.SignatureStatus;
import com.msg.ppap.masterdata.api.MasterdataQueries;
import com.msg.ppap.masterdata.api.MasterdataQueries.PartInfo;
import com.msg.ppap.masterdata.api.MasterdataQueries.PartMappingInfo;
import com.msg.ppap.masterdata.api.MasterdataQueries.RelationInfo;
import com.msg.ppap.platform.domain.NotFoundException;
import com.msg.ppap.semantics.api.DecisionMessage;
import com.msg.ppap.semantics.api.ExchangeCodec;
import com.msg.ppap.semantics.api.RequestMessage;
import com.msg.ppap.semantics.api.StatusUpdateMessage;
import com.msg.ppap.semantics.api.SubmissionMessage;
import com.msg.ppap.semantics.api.WithdrawalMessage;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processing of partner messages delivered by the exchange inbox. Every payload is validated against its schema,
 * checked against the sender and the own tenant and processed idempotently; remote transitions are recorded with the
 * actor {@code partner:<BPNL>}.
 */
@Service
@Transactional
public class InboundMessageService
        implements ReceiveRequestUseCase,
                ReceiveSubmissionUseCase,
                ReceiveDecisionUseCase,
                ReceiveStatusUpdateUseCase,
                ReceiveWithdrawalUseCase {

    public static final String UNKNOWN_SENDER = "UNKNOWN_SENDER";
    public static final String WRONG_RECIPIENT = "WRONG_RECIPIENT";

    private final ApprovalContext context;
    private final ExchangeCodec codec;
    private final PayloadSealing sealing;
    private final MasterdataQueries masterdata;
    private final CaseNumberGenerator caseNumbers;

    public InboundMessageService(
            ApprovalContext context,
            ExchangeCodec codec,
            PayloadSealing sealing,
            MasterdataQueries masterdata,
            CaseNumberGenerator caseNumbers) {
        this.context = context;
        this.codec = codec;
        this.sealing = sealing;
        this.masterdata = masterdata;
        this.caseNumbers = caseNumbers;
    }

    @Override
    public InboundResult receiveRequest(InboundMessage message) {
        RequestMessage request = codec.decodeRequest(message.payload()).message();
        checkParties(message.senderBpnl(), request.customerBpnl(), request.supplierBpnl());
        Optional<ApprovalCase> existing =
                context.repository().findByExchangeId(request.exchangeId(), CaseRole.SUPPLIER);
        if (existing.isPresent()) {
            return new InboundResult(existing.get().id(), false, existing.get().caseNumber());
        }
        RelationInfo relation = masterdata
                .findRelation(request.customerBpnl(), CaseRole.CUSTOMER.name())
                .orElseThrow(() -> ApprovalErrors.unprocessable(
                        UNKNOWN_SENDER, "No relation to customer " + request.customerBpnl()));
        Optional<PartInfo> part = masterdata
                .findMappingByPartnerPartNumber(relation.id(), request.customerPartNumber())
                .map(PartMappingInfo::partId)
                .flatMap(masterdata::findPart);
        // UC-04 A1: an unmapped part does not reject the request – the case goes to the clarification list
        Clarification clarification =
                part.isPresent() ? null : Clarification.partNotMapped(request.customerPartNumber());
        PartRef partRef = part.map(ApprovalCaseService::partRef).orElseGet(() -> PartRef.unmapped(request.partName()));
        var header = new CaseHeader(
                new PartnerRef(relation.partnerBpnl(), relation.partnerName()),
                relation.id(),
                partRef,
                request.customerPartNumber(),
                ref(request.reason()),
                ref(request.level()),
                request.dueDate(),
                request.remarks(),
                null);
        var approvalRequest = new ApprovalRequest(
                request.requestedAt(),
                request.requestedBy(),
                request.elements().stream()
                        .map(e -> new RequestedElement(
                                e.code(),
                                Requirement.valueOf(e.requirement()),
                                e.note(),
                                new ElementSnapshot(
                                        e.snapshot().nameDe(),
                                        e.snapshot().nameEn(),
                                        EvidenceType.valueOf(e.snapshot().evidenceType()),
                                        e.snapshot().valueSchema())))
                        .toList(),
                request.attachments().stream()
                        .map(d -> new DocumentRef(null, d.fileName(), d.mediaType(), d.size(), d.sha256(), null, null))
                        .toList());
        ApprovalCase created = ApprovalCase.receiveRequest(
                caseNumbers.next(context.today().getYear()),
                request.exchangeId(),
                header,
                approvalRequest,
                clarification,
                Actor.partner(message.senderBpnl()),
                context.now());
        ApprovalCase saved = context.save(created);
        return new InboundResult(saved.id(), true, saved.caseNumber());
    }

    @Override
    public InboundResult receiveSubmission(InboundMessage message) {
        var decoded = codec.decodeSubmission(message.payload());
        SubmissionMessage submission = decoded.message();
        checkParties(message.senderBpnl(), submission.supplierBpnl(), submission.customerBpnl());
        ApprovalCase c = context.repository()
                .findByExchangeId(submission.exchangeId(), CaseRole.CUSTOMER)
                .orElseThrow(() -> new NotFoundException("Approval case with exchange id", submission.exchangeId()));
        checkPartner(c, message.senderBpnl());
        Seal seal = sealing.received(decoded.payload(), message.jws(), message.senderBpnl());
        if (seal.signature().status() == SignatureStatus.INVALID) {
            throw ApprovalErrors.unprocessable(ApprovalErrors.SIGNATURE_INVALID, "Submission signature is invalid");
        }
        boolean applied = c.receiveSubmission(
                submission.revision(),
                ExchangeMessageMapper.evidence(submission),
                ExchangeMessageMapper.references(submission),
                submission.submittedAt(),
                submission.submittedBy(),
                seal,
                message.exchangeMode() == null ? ExchangeMode.PUSH : ExchangeMode.of(message.exchangeMode()),
                Actor.partner(message.senderBpnl()),
                context.now());
        if (applied) {
            context.save(c);
        }
        return new InboundResult(c.id(), applied, c.caseNumber());
    }

    @Override
    public InboundResult receiveDecision(InboundMessage message) {
        var decoded = codec.decodeDecision(message.payload());
        DecisionMessage decision = decoded.message();
        ApprovalCase c = context.repository()
                .findByExchangeId(decision.exchangeId(), CaseRole.SUPPLIER)
                .orElseThrow(() -> new NotFoundException("Approval case with exchange id", decision.exchangeId()));
        checkPartner(c, message.senderBpnl());
        Seal seal = sealing.received(decoded.payload(), message.jws(), message.senderBpnl());
        if (seal.signature().status() == SignatureStatus.INVALID) {
            throw ApprovalErrors.unprocessable(ApprovalErrors.SIGNATURE_INVALID, "Decision signature is invalid");
        }
        var received = new Decision(
                decision.revision(),
                DecisionResult.valueOf(decision.result()),
                decision.conditions(),
                decision.conditionDueDate(),
                decision.comment(),
                ExchangeMessageMapper.findings(decision),
                decision.decidedAt(),
                decision.decidedBy(),
                seal);
        boolean applied = c.receiveDecision(
                received, decision.submissionHash(), Actor.partner(message.senderBpnl()), context.now());
        if (applied) {
            context.save(c);
        }
        return new InboundResult(c.id(), applied, c.caseNumber());
    }

    @Override
    public InboundResult receiveStatusUpdate(InboundMessage message) {
        StatusUpdateMessage update = codec.decodeStatusUpdate(message.payload()).message();
        CaseRole ownRole =
                StatusUpdateMessage.IN_PROGRESS.equals(update.status()) ? CaseRole.CUSTOMER : CaseRole.SUPPLIER;
        ApprovalCase c = context.repository()
                .findByExchangeId(update.exchangeId(), ownRole)
                .orElseThrow(() -> new NotFoundException("Approval case with exchange id", update.exchangeId()));
        checkPartner(c, message.senderBpnl());
        boolean applied = c.receiveStatusUpdate(
                CaseStatus.valueOf(update.status()),
                update.revision(),
                Actor.partner(message.senderBpnl()),
                context.now());
        if (applied) {
            context.save(c);
        }
        return new InboundResult(c.id(), applied, c.caseNumber());
    }

    @Override
    public InboundResult receiveWithdrawal(InboundMessage message) {
        WithdrawalMessage withdrawal = codec.decodeWithdrawal(message.payload()).message();
        ApprovalCase c = context.repository()
                .findByExchangeId(withdrawal.exchangeId(), CaseRole.SUPPLIER)
                .orElseThrow(() -> new NotFoundException("Approval case with exchange id", withdrawal.exchangeId()));
        checkPartner(c, message.senderBpnl());
        boolean applied = c.receiveWithdrawal(withdrawal.reason(), Actor.partner(message.senderBpnl()), context.now());
        if (applied) {
            context.save(c);
        }
        return new InboundResult(c.id(), applied, c.caseNumber());
    }

    private void checkParties(String sender, String expectedSender, String expectedRecipient) {
        if (!sender.equals(expectedSender)) {
            throw ApprovalErrors.unprocessable(
                    UNKNOWN_SENDER, "Sender " + sender + " does not match the payload (" + expectedSender + ")");
        }
        if (!context.tenantBpnl().equals(expectedRecipient)) {
            throw ApprovalErrors.unprocessable(
                    WRONG_RECIPIENT, "Payload is addressed to " + expectedRecipient + ", not to this tenant");
        }
    }

    private static void checkPartner(ApprovalCase c, String sender) {
        if (!c.header().partner().bpnl().equals(sender)) {
            throw ApprovalErrors.unprocessable(UNKNOWN_SENDER, "Sender " + sender + " is not the partner of the case");
        }
    }

    private static CatalogRef ref(RequestMessage.CodeName codeName) {
        return new CatalogRef(codeName.code(), codeName.nameDe(), codeName.nameEn());
    }
}
