package com.msg.ppap.approval.application;

import com.msg.ppap.approval.application.port.in.PartnerSharingUseCase;
import com.msg.ppap.approval.domain.ApprovalCase;
import com.msg.ppap.approval.domain.CaseRole;
import com.msg.ppap.approval.domain.CaseSource;
import com.msg.ppap.approval.domain.CaseStatus;
import com.msg.ppap.approval.domain.DocumentRef;
import com.msg.ppap.approval.domain.ExchangeMode;
import com.msg.ppap.approval.domain.Seal;
import com.msg.ppap.approval.domain.Submission;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link PartnerSharingUseCase}: authorization basis of the internal EDC endpoints (only submitted revisions and
 * documents of cases with exactly that partner) and the inputs of twin registration, pull polling and document
 * transfer.
 */
@Service
@Transactional(readOnly = true)
public class PartnerSharingService implements PartnerSharingUseCase {

    private static final List<String> AWAITING = List.of(CaseStatus.REQUESTED.name(), CaseStatus.IN_PROGRESS.name());

    private final ApprovalContext context;

    public PartnerSharingService(ApprovalContext context) {
        this.context = context;
    }

    @Override
    public Optional<SharedSubmission> sharedSubmission(UUID exchangeId, int revision, String partnerBpnl) {
        return supplierCase(exchangeId)
                .filter(c -> c.header().partner().bpnl().equals(partnerBpnl))
                .flatMap(c -> c.submissions().stream()
                        .filter(s -> s.revision() == revision && !s.draft() && s.seal() != null)
                        .findFirst()
                        .map(s -> shared(c, s)));
    }

    private static SharedSubmission shared(ApprovalCase c, Submission s) {
        Seal seal = s.seal();
        return new SharedSubmission(
                c.id(),
                s.revision(),
                seal.payload().getBytes(StandardCharsets.UTF_8),
                seal.payloadHash(),
                seal.signature().jws());
    }

    @Override
    public Optional<UUID> sharedDocument(String sha256, String partnerBpnl) {
        if (sha256 == null || !sha256.matches("^[0-9a-f]{64}$") || partnerBpnl == null || partnerBpnl.isBlank()) {
            return Optional.empty();
        }
        return context.repository().findSharedDocument(sha256, partnerBpnl);
    }

    @Override
    public Optional<TwinSource> twinSource(UUID exchangeId) {
        return supplierCase(exchangeId)
                .map(c -> new TwinSource(
                        c.id(),
                        c.caseNumber(),
                        c.exchangeId(),
                        c.header().partner().bpnl(),
                        c.customerPartNumber(),
                        c.header().part() != null && c.header().part().mapped() ? c.supplierPartNumber() : null,
                        c.submissions().stream()
                                .filter(s -> !s.draft() && s.seal() != null)
                                .filter(s -> s.exchangeMode() != null && s.exchangeMode() != ExchangeMode.PUSH)
                                .map(Submission::revision)
                                .toList()));
    }

    @Override
    public List<AwaitedSubmission> awaitedSubmissions() {
        return context.repository().findCustomerCases(AWAITING).stream()
                .map(k -> new AwaitedSubmission(
                        k.id(),
                        k.caseNumber(),
                        k.exchangeId(),
                        k.partnerBpnl(),
                        k.currentRevision() == null ? 0 : k.currentRevision()))
                .toList();
    }

    @Override
    public List<PartnerDocument> partnerDocuments(UUID caseId) {
        ApprovalCase c = context.load(caseId);
        Map<String, DocumentRef> refs = new LinkedHashMap<>();
        String kind;
        if (c.ownRole() == CaseRole.CUSTOMER && c.source() == CaseSource.LOCAL) {
            kind = "EVIDENCE";
            c.submissions().stream()
                    .filter(s -> !s.draft())
                    .flatMap(s -> s.elements().stream())
                    .flatMap(e -> e.documents().stream())
                    .forEach(d -> refs.putIfAbsent(d.sha256(), d));
        } else if (c.ownRole() == CaseRole.SUPPLIER && c.source() == CaseSource.EXCHANGE) {
            kind = "ATTACHMENT";
            c.request().attachments().forEach(d -> refs.putIfAbsent(d.sha256(), d));
        } else {
            return List.of();
        }
        List<PartnerDocument> result = new ArrayList<>();
        for (DocumentRef d : refs.values()) {
            if (d.documentId() == null) {
                result.add(new PartnerDocument(
                        c.id(),
                        c.caseNumber(),
                        c.exchangeId(),
                        c.header().partner().bpnl(),
                        d.sha256(),
                        d.fileName(),
                        d.mediaType(),
                        d.size(),
                        kind));
            }
        }
        return result;
    }

    @Override
    public Optional<CaseRef> findCase(UUID exchangeId, String ownRole) {
        return context.repository()
                .findByExchangeId(exchangeId, CaseRole.valueOf(ownRole))
                .map(c ->
                        new CaseRef(c.id(), c.caseNumber(), c.header().partner().bpnl()));
    }

    private Optional<ApprovalCase> supplierCase(UUID exchangeId) {
        return context.repository()
                .findByExchangeId(exchangeId, CaseRole.SUPPLIER)
                .filter(c -> c.source() == CaseSource.EXCHANGE);
    }
}
