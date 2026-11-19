package com.msg.ppap.approval.application;

import com.msg.ppap.approval.CaseExchangeStatus;
import com.msg.ppap.approval.CaseExchangeStatus.LastOutbound;
import com.msg.ppap.approval.DocumentTransferStatus;
import com.msg.ppap.approval.application.port.out.CaseNumberGenerator;
import com.msg.ppap.approval.application.port.out.CaseQuery;
import com.msg.ppap.approval.application.port.out.CaseStatistics;
import com.msg.ppap.approval.application.port.out.CaseSummary;
import com.msg.ppap.approval.domain.Actor;
import com.msg.ppap.approval.domain.ApprovalCase;
import com.msg.ppap.approval.domain.CaseHeader;
import com.msg.ppap.approval.domain.CaseRole;
import com.msg.ppap.approval.domain.CatalogRef;
import com.msg.ppap.approval.domain.Completeness;
import com.msg.ppap.approval.domain.PartRef;
import com.msg.ppap.approval.domain.PartnerRef;
import com.msg.ppap.approval.domain.RequestedElement;
import com.msg.ppap.approval.domain.Submission;
import com.msg.ppap.approval.domain.ValueValidator;
import com.msg.ppap.masterdata.api.MasterdataQueries;
import com.msg.ppap.masterdata.api.MasterdataQueries.LevelInfo;
import com.msg.ppap.masterdata.api.MasterdataQueries.PartInfo;
import com.msg.ppap.masterdata.api.MasterdataQueries.PartMappingInfo;
import com.msg.ppap.masterdata.api.MasterdataQueries.RelationInfo;
import com.msg.ppap.masterdata.api.PartMappingRegistry;
import com.msg.ppap.platform.domain.Validation;
import com.msg.ppap.platform.domain.ValidationException;
import com.msg.ppap.platform.domain.ViolationCodes;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases of the customer request (UC-03), manual capture and the case lifecycle up to the start of work. */
@Service
@Transactional
public class ApprovalCaseService {

    static final int REMARKS_MAX = 4000;
    static final int REFERENCE_MAX = 255;

    private final ApprovalContext context;
    private final MasterdataQueries masterdata;
    private final RequestElementFactory elements;
    private final CaseNumberGenerator caseNumbers;
    private final DocumentRefs documents;
    private final ValueValidator validator;
    private final PartMappingRegistry mappingRegistry;
    private final ObjectProvider<CaseExchangeStatus> exchangeStatus;
    private final ObjectProvider<DocumentTransferStatus> transfers;

    @SuppressWarnings("java:S107")
    public ApprovalCaseService(
            ApprovalContext context,
            MasterdataQueries masterdata,
            RequestElementFactory elements,
            CaseNumberGenerator caseNumbers,
            DocumentRefs documents,
            ValueValidator validator,
            PartMappingRegistry mappingRegistry,
            ObjectProvider<CaseExchangeStatus> exchangeStatus,
            ObjectProvider<DocumentTransferStatus> transfers) {
        this.transfers = transfers;
        this.mappingRegistry = mappingRegistry;
        this.exchangeStatus = exchangeStatus;
        this.context = context;
        this.masterdata = masterdata;
        this.elements = elements;
        this.caseNumbers = caseNumbers;
        this.documents = documents;
        this.validator = validator;
    }

    /** Customer creates a draft (DRAFT). Relation and part mapping to the supplier must exist. */
    public CaseView create(CaseCommands.Create command) {
        Actor actor = context.actor();
        var v = new Validation();
        PartInfo part = part(v, command.partId());
        RelationInfo relation = null;
        PartMappingInfo mapping = null;
        if (part != null) {
            if (command.relationId() != null) {
                relation = masterdata
                        .findRelation(command.relationId())
                        .filter(r -> CaseRole.SUPPLIER.name().equals(r.partnerRole()))
                        .orElse(null);
                if (relation == null) {
                    v.add("relationId", ViolationCodes.UNKNOWN_REFERENCE, "no supplier relation with this id");
                } else {
                    mapping = masterdata.findMapping(relation.id(), part.id()).orElse(null);
                    if (mapping == null) {
                        v.add("partId", ViolationCodes.UNKNOWN_REFERENCE, "part is not mapped to this supplier");
                    }
                }
            } else {
                List<PartMappingInfo> candidates = masterdata.findMappingsOfPart(part.id()).stream()
                        .filter(m -> masterdata
                                .findRelation(m.relationId())
                                .filter(r -> CaseRole.SUPPLIER.name().equals(r.partnerRole()))
                                .isPresent())
                        .toList();
                if (candidates.isEmpty()) {
                    v.add("relationId", ViolationCodes.UNKNOWN_REFERENCE, "part is not mapped to any supplier");
                } else if (candidates.size() > 1) {
                    v.add("relationId", ViolationCodes.REQUIRED, "part is mapped to several suppliers");
                } else {
                    mapping = candidates.getFirst();
                    relation = masterdata.findRelation(mapping.relationId()).orElseThrow();
                }
            }
        }
        CatalogRef reason = elements.reason(v, "reasonCode", command.reasonCode());
        LevelInfo level = elements.level(v, "levelCode", command.levelCode());
        LocalDate dueDate = required(v, "dueDate", command.dueDate());
        String remarks = v.optional("remarks", command.remarks(), REMARKS_MAX);
        List<RequestedElement> requested = elements.elements(v, level, command.elements());
        v.throwIfInvalid();
        var header = new CaseHeader(
                new PartnerRef(relation.partnerBpnl(), relation.partnerName()),
                relation.id(),
                partRef(part),
                mapping.partnerPartNumber(),
                reason,
                RequestElementFactory.ref(level),
                dueDate,
                remarks,
                null);
        ApprovalCase created = ApprovalCase.createDraft(
                caseNumbers.next(context.today().getYear()), header, requested, actor, context.now());
        return view(context.save(created), actor);
    }

    /** Supplier captures a request received outside the dataspace (REQUESTED, source MANUAL). */
    public CaseView captureManual(CaseCommands.Manual command) {
        Actor actor = context.actor();
        var v = new Validation();
        PartInfo part = part(v, command.partId());
        RelationInfo relation = null;
        String bpnl = Validation.trimToNull(command.partnerBpnl());
        if (bpnl == null) {
            v.add("partnerBpnl", ViolationCodes.REQUIRED, "must not be blank");
        } else {
            relation = masterdata.findRelation(bpnl, CaseRole.CUSTOMER.name()).orElse(null);
            if (relation == null) {
                v.add("partnerBpnl", ViolationCodes.UNKNOWN_REFERENCE, "no relation to this customer");
            }
        }
        String partnerPartNumber = Validation.trimToNull(command.partnerPartNumber());
        if (partnerPartNumber == null && part != null && relation != null) {
            partnerPartNumber = masterdata
                    .findMapping(relation.id(), part.id())
                    .map(PartMappingInfo::partnerPartNumber)
                    .orElse(null);
            if (partnerPartNumber == null) {
                v.add("partnerPartNumber", ViolationCodes.REQUIRED, "no part mapping, customer part number required");
            }
        } else if (partnerPartNumber != null && partnerPartNumber.length() > 64) {
            v.add("partnerPartNumber", ViolationCodes.TOO_LONG, "must be at most 64 characters");
        }
        CatalogRef reason = elements.reason(v, "reasonCode", command.reasonCode());
        LevelInfo level = elements.level(v, "levelCode", command.levelCode());
        LocalDate dueDate = required(v, "dueDate", command.dueDate());
        String remarks = v.optional("remarks", command.remarks(), REMARKS_MAX);
        String reference = v.optional("externalReference", command.externalReference(), REFERENCE_MAX);
        List<RequestedElement> requested = elements.elements(v, level, command.elements());
        v.throwIfInvalid();
        var header = new CaseHeader(
                new PartnerRef(relation.partnerBpnl(), relation.partnerName()),
                relation.id(),
                partRef(part),
                partnerPartNumber,
                reason,
                RequestElementFactory.ref(level),
                dueDate,
                remarks,
                reference);
        ApprovalCase created = ApprovalCase.captureManual(
                caseNumbers.next(context.today().getYear()), header, requested, actor, context.now());
        return view(context.save(created), actor);
    }

    /** Changes the header of a draft. */
    public CaseView update(UUID id, CaseCommands.Update command, Long expectedVersion) {
        Actor actor = context.actor();
        ApprovalCase c = loadChecked(id, expectedVersion);
        var v = new Validation();
        CatalogRef reason = elements.reason(v, "reasonCode", command.reasonCode());
        LevelInfo level = elements.level(v, "levelCode", command.levelCode());
        LocalDate dueDate = required(v, "dueDate", command.dueDate());
        String remarks = v.optional("remarks", command.remarks(), REMARKS_MAX);
        v.throwIfInvalid();
        c.updateDraft(reason, RequestElementFactory.ref(level), dueDate, remarks, actor, context.now());
        return view(context.save(c), actor);
    }

    /** Replaces the requested elements of a draft. */
    public CaseView replaceElements(UUID id, List<ElementInput> input, Long expectedVersion) {
        Actor actor = context.actor();
        ApprovalCase c = loadChecked(id, expectedVersion);
        var v = new Validation();
        LevelInfo level = masterdata.findLevel(c.header().level().code()).orElse(null);
        List<RequestedElement> requested = elements.elements(v, level, input == null ? List.of() : input);
        if (input == null || input.isEmpty()) {
            v.add("elements", ViolationCodes.REQUIRED, "at least one element required");
        }
        v.throwIfInvalid();
        c.replaceRequestElements(requested, actor, context.now());
        return view(context.save(c), actor);
    }

    public CaseView addAttachment(UUID id, UUID documentId, Long expectedVersion) {
        Actor actor = context.actor();
        ApprovalCase c = loadChecked(id, expectedVersion);
        if (documentId == null) {
            throw ValidationException.of("documentId", ViolationCodes.REQUIRED, "must not be blank");
        }
        c.addAttachment(documents.resolve(documentId), actor, context.now());
        return view(context.save(c), actor);
    }

    public Long removeAttachment(UUID id, UUID documentId, Long expectedVersion) {
        Actor actor = context.actor();
        ApprovalCase c = loadChecked(id, expectedVersion);
        c.removeAttachment(documentId, actor, context.now());
        return context.save(c).version();
    }

    /** Sends the request (DRAFT → REQUESTED); delivery via EDC follows in phase 4 (event {@code CaseRequested}). */
    public CaseView send(UUID id, Long expectedVersion) {
        Actor actor = context.actor();
        ApprovalCase c = loadChecked(id, expectedVersion);
        boolean ready = masterdata
                .findPartner(c.header().partner().bpnl())
                .map(MasterdataQueries.PartnerInfo::exchangeReady)
                .orElse(false);
        c.send(ready, actor, context.now(), context.today());
        return view(context.save(c), actor);
    }

    public CaseView cancel(UUID id, String reason, Long expectedVersion) {
        Actor actor = context.actor();
        ApprovalCase c = loadChecked(id, expectedVersion);
        c.cancel(reason, actor, context.now());
        return view(context.save(c), actor);
    }

    /** Supplier starts working (REQUESTED → IN_PROGRESS, draft revision 1). */
    public CaseView startWork(UUID id, Long expectedVersion) {
        Actor actor = context.actor();
        ApprovalCase c = loadChecked(id, expectedVersion);
        c.startWork(actor, context.now());
        return view(context.save(c), actor);
    }

    /**
     * Supplier clarifies a received request whose part could not be mapped (UC-04 A1): assigns the own part and
     * optionally saves the part mapping for future requests.
     */
    public CaseView assignPart(UUID id, UUID partId, boolean saveMapping, Long expectedVersion) {
        Actor actor = context.actor();
        ApprovalCase c = loadChecked(id, expectedVersion);
        var v = new Validation();
        PartInfo part = part(v, partId);
        v.throwIfInvalid();
        String partnerPartNumber =
                c.clarification() == null ? null : c.clarification().partnerPartNumber();
        c.assignPart(partRef(part), actor, context.now());
        if (saveMapping && partnerPartNumber != null && c.header().relationId() != null) {
            mappingRegistry.registerMapping(c.header().relationId(), part.id(), partnerPartNumber);
        }
        return view(context.save(c), actor);
    }

    @Transactional(readOnly = true)
    public CaseView get(UUID id) {
        return view(context.load(id), context.actor());
    }

    @Transactional(readOnly = true)
    public Page<SummaryView> search(CaseQuery query, Pageable pageable) {
        Actor actor = context.actor();
        LocalDate today = context.today();
        Page<CaseSummary> page = context.repository().search(query, pageable);
        Map<UUID, LastOutbound> outbound =
                lastOutbound(page.getContent().stream().map(CaseSummary::id).toList());
        return page.map(s -> new SummaryView(
                s,
                ApprovalCase.allowedActions(
                        s.ownRole(), s.source(), s.status(), s.clarification(), actor, context.policy()),
                today,
                outbound.get(s.id())));
    }

    @Transactional(readOnly = true)
    public CaseStatistics statistics() {
        return context.repository()
                .statistics(new CaseStatistics.Parameters(
                        context.today(), context.now().minus(Duration.ofDays(30))));
    }

    /** Today, as used for {@code overdue}. */
    public LocalDate today() {
        return context.today();
    }

    CaseView view(ApprovalCase c, Actor actor) {
        Completeness completeness = c.currentSubmission()
                .filter(Submission::draft)
                .map(s -> c.completeness(s, validator))
                .orElse(null);
        return new CaseView(
                c,
                c.allowedActions(actor, context.policy()),
                context.today(),
                completeness,
                lastOutbound(List.of(c.id())).get(c.id()),
                transfers(c.id()));
    }

    private Map<String, DocumentTransferStatus.Transfer> transfers(UUID caseId) {
        DocumentTransferStatus status = transfers.getIfAvailable();
        return status == null ? Map.of() : status.transfers(caseId);
    }

    private Map<UUID, LastOutbound> lastOutbound(List<UUID> caseIds) {
        CaseExchangeStatus status = exchangeStatus.getIfAvailable();
        return status == null || caseIds.isEmpty() ? Map.of() : status.lastOutbound(caseIds);
    }

    private ApprovalCase loadChecked(UUID id, Long expectedVersion) {
        ApprovalCase c = context.load(id);
        c.checkVersion(expectedVersion);
        return c;
    }

    private PartInfo part(Validation v, UUID partId) {
        if (partId == null) {
            v.add("partId", ViolationCodes.REQUIRED, "must not be blank");
            return null;
        }
        Optional<PartInfo> part = masterdata.findPart(partId);
        if (part.isEmpty()) {
            v.add("partId", ViolationCodes.UNKNOWN_REFERENCE, "unknown part");
            return null;
        }
        if (!part.get().active()) {
            v.add("partId", ViolationCodes.INACTIVE_REFERENCE, "part is inactive");
        }
        return part.get();
    }

    static PartRef partRef(PartInfo part) {
        return new PartRef(part.id(), part.partNumber(), part.nameDe(), part.nameEn());
    }

    private static <T> T required(Validation v, String field, T value) {
        if (value == null) {
            v.add(field, ViolationCodes.REQUIRED, "must not be blank");
        }
        return value;
    }
}
