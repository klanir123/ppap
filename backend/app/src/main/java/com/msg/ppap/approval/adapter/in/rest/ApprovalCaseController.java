package com.msg.ppap.approval.adapter.in.rest;

import com.msg.ppap.approval.application.ApprovalCaseService;
import com.msg.ppap.approval.application.CaseCommands;
import com.msg.ppap.approval.application.CaseView;
import com.msg.ppap.approval.application.ElementInput;
import com.msg.ppap.approval.application.ReviewService;
import com.msg.ppap.approval.application.port.out.CaseQuery;
import com.msg.ppap.approval.domain.CaseRole;
import com.msg.ppap.approval.domain.CaseStatus;
import com.msg.ppap.approval.domain.Decision;
import com.msg.ppap.platform.domain.ValidationException;
import com.msg.ppap.platform.domain.ViolationCodes;
import com.msg.ppap.platform.web.CreatedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Approval cases: list, detail, request (UC-03), manual capture, lifecycle actions, review and decision (UC-07). */
@RestController
@RequestMapping(ApprovalCaseController.PATH)
@Tag(name = "approval-cases")
public class ApprovalCaseController {

    static final String PATH = "/api/v1/approval-cases";

    private static final Set<String> SORTABLE = Set.of(
            "caseNumber", "dueDate", "status", "createdAt", "updatedAt", "requestedAt", "partnerName", "partNumber");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"));

    private final ApprovalCaseService cases;
    private final ReviewService reviews;

    public ApprovalCaseController(ApprovalCaseService cases, ReviewService reviews) {
        this.cases = cases;
        this.reviews = reviews;
    }

    @GetMapping
    @Operation(operationId = "listApprovalCases", summary = "Search approval cases")
    public ApprovalCasePage list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) CaseRole ownRole,
            @RequestParam(required = false) String partnerBpnl,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) List<String> sort) {
        var pageable = ApprovalRest.pageable(page, size, sort, SORTABLE, DEFAULT_SORT);
        var query = new CaseQuery(q, statuses(status), ownRole, partnerBpnl, overdue, cases.today());
        return ApprovalCasePage.of(cases.search(query, pageable).map(ApprovalDtos::summary));
    }

    @GetMapping("/stats")
    @Operation(operationId = "getApprovalCaseStats", summary = "Dashboard figures of the approval cases")
    public CaseStatsDto stats() {
        return ApprovalDtos.stats(cases.statistics());
    }

    @GetMapping("/{id}")
    @Operation(
            operationId = "getApprovalCase",
            summary = "Get an approval case with request, revisions, last decision and timeline")
    public ResponseEntity<ApprovalCaseDetailDto> get(@PathVariable UUID id) {
        return respond(cases.get(id));
    }

    @PostMapping
    @CreatedResponse
    @PreAuthorize(ApprovalRest.CUSTOMER)
    @Operation(operationId = "createApprovalCase", summary = "Create a draft request (customer)")
    public ResponseEntity<ApprovalCaseDetailDto> create(@RequestBody CreateApprovalCaseRequest request) {
        CaseView view = cases.create(new CaseCommands.Create(
                request.partId(),
                request.relationId(),
                request.reasonCode(),
                request.levelCode(),
                request.dueDate(),
                request.remarks(),
                elements(request.elements())));
        return created(view);
    }

    @PostMapping("/manual")
    @CreatedResponse
    @PreAuthorize(ApprovalRest.SUPPLIER)
    @Operation(
            operationId = "captureManualApprovalCase",
            summary = "Capture a request received outside the dataspace (supplier, status REQUESTED)")
    public ResponseEntity<ApprovalCaseDetailDto> captureManual(@RequestBody ManualApprovalCaseRequest request) {
        CaseView view = cases.captureManual(new CaseCommands.Manual(
                request.partnerBpnl(),
                request.partId(),
                request.partnerPartNumber(),
                request.reasonCode(),
                request.levelCode(),
                request.dueDate(),
                request.remarks(),
                elements(request.elements()),
                request.externalReference()));
        return created(view);
    }

    @PutMapping("/{id}")
    @PreAuthorize(ApprovalRest.CUSTOMER)
    @Operation(operationId = "updateApprovalCase", summary = "Change the header of a draft (If-Match)")
    public ResponseEntity<ApprovalCaseDetailDto> update(
            @PathVariable UUID id,
            @RequestBody UpdateApprovalCaseRequest request,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return respond(cases.update(
                id,
                new CaseCommands.Update(
                        request.reasonCode(), request.levelCode(), request.dueDate(), request.remarks()),
                ApprovalRest.expectedVersion(ifMatch)));
    }

    @PutMapping("/{id}/request/elements")
    @PreAuthorize(ApprovalRest.CUSTOMER)
    @Operation(operationId = "replaceRequestElements", summary = "Replace the requested elements of a draft (If-Match)")
    public ResponseEntity<ApprovalCaseDetailDto> replaceElements(
            @PathVariable UUID id,
            @RequestBody List<RequestedElementInput> request,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return respond(cases.replaceElements(id, elements(request), ApprovalRest.expectedVersion(ifMatch)));
    }

    @PostMapping("/{id}/request/attachments")
    @PreAuthorize(ApprovalRest.CUSTOMER)
    @Operation(operationId = "addRequestAttachment", summary = "Attach an uploaded document to a draft request")
    public ResponseEntity<ApprovalCaseDetailDto> addAttachment(
            @PathVariable UUID id,
            @RequestBody DocumentReferenceRequest request,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return respond(cases.addAttachment(id, request.documentId(), ApprovalRest.expectedVersion(ifMatch)));
    }

    @DeleteMapping("/{id}/request/attachments/{documentId}")
    @PreAuthorize(ApprovalRest.CUSTOMER)
    @Operation(operationId = "removeRequestAttachment", summary = "Remove an attachment from a draft request")
    public ResponseEntity<Void> removeAttachment(
            @PathVariable UUID id,
            @PathVariable UUID documentId,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        Long version = cases.removeAttachment(id, documentId, ApprovalRest.expectedVersion(ifMatch));
        return ResponseEntity.noContent().eTag(ApprovalRest.etag(version)).build();
    }

    @PostMapping("/{id}/send")
    @PreAuthorize(ApprovalRest.CUSTOMER)
    @Operation(operationId = "sendApprovalCase", summary = "Send the request (DRAFT → REQUESTED)")
    public ResponseEntity<ApprovalCaseDetailDto> send(
            @PathVariable UUID id, @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return respond(cases.send(id, ApprovalRest.expectedVersion(ifMatch)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(ApprovalRest.ANY_QUALITY)
    @Operation(
            operationId = "cancelApprovalCase",
            summary = "Cancel a draft or request (customer; supplier for manually captured requests)")
    public ResponseEntity<ApprovalCaseDetailDto> cancel(
            @PathVariable UUID id,
            @RequestBody CancelCaseRequest request,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return respond(cases.cancel(id, request.reason(), ApprovalRest.expectedVersion(ifMatch)));
    }

    @PostMapping("/{id}/start-work")
    @PreAuthorize(ApprovalRest.SUPPLIER)
    @Operation(
            operationId = "startWork",
            summary = "Start working on a request (REQUESTED → IN_PROGRESS, creates draft revision)")
    public ResponseEntity<ApprovalCaseDetailDto> startWork(
            @PathVariable UUID id, @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return respond(cases.startWork(id, ApprovalRest.expectedVersion(ifMatch)));
    }

    @PostMapping("/{id}/start-review")
    @PreAuthorize("hasRole('CUSTOMER_QUALITY_ENGINEER')")
    @Operation(
            operationId = "startReview",
            summary = "Start the review (SUBMITTED → IN_REVIEW) after verifying hash and signature")
    public ResponseEntity<ApprovalCaseDetailDto> startReview(
            @PathVariable UUID id, @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return respond(reviews.startReview(id, ApprovalRest.expectedVersion(ifMatch)));
    }

    @PutMapping("/{id}/review/elements/{code}")
    @PreAuthorize(ApprovalRest.CUSTOMER)
    @Operation(
            operationId = "recordElementFinding",
            summary = "Record the review result of an element (IN_REVIEW, intermediate, not signed)")
    public ResponseEntity<ApprovalCaseDetailDto> recordFinding(
            @PathVariable UUID id,
            @PathVariable String code,
            @RequestBody ElementFindingRequest request,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return respond(reviews.recordFinding(
                id,
                code,
                new CaseCommands.Finding(name(request.result()), request.comment()),
                ApprovalRest.expectedVersion(ifMatch)));
    }

    @PostMapping("/{id}/decision")
    @PreAuthorize(ApprovalRest.CUSTOMER)
    @Operation(
            operationId = "decideApprovalCase",
            summary = "Decide (IN_REVIEW → APPROVED | CONDITIONALLY_APPROVED | REJECTED); signed, immutable; "
                    + "401 RECENT_AUTH_REQUIRED if a fresh login is required")
    public ResponseEntity<DecisionDto> decide(
            @PathVariable UUID id,
            @RequestBody DecisionRequest request,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        Decision decision = reviews.decide(
                id,
                new CaseCommands.Decide(
                        name(request.result()),
                        request.conditions() == null
                                ? List.of()
                                : request.conditions().stream()
                                        .map(c -> c == null ? null : c.text())
                                        .toList(),
                        request.conditionDueDate(),
                        request.comment(),
                        request.confirmation()),
                ApprovalRest.expectedVersion(ifMatch));
        return ApprovalRest.ok(ApprovalDtos.decision(decision), reviews.version(id));
    }

    private static ResponseEntity<ApprovalCaseDetailDto> respond(CaseView view) {
        return ApprovalRest.ok(ApprovalDtos.detail(view), view.approvalCase().version());
    }

    private static ResponseEntity<ApprovalCaseDetailDto> created(CaseView view) {
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(PATH + "/{id}")
                .buildAndExpand(view.approvalCase().id())
                .toUri();
        return ResponseEntity.created(location)
                .eTag(ApprovalRest.etag(view.approvalCase().version()))
                .body(ApprovalDtos.detail(view));
    }

    static List<ElementInput> elements(List<RequestedElementInput> input) {
        return input == null
                ? null
                : input.stream()
                        .map(e -> e == null
                                ? new ElementInput(null, null, null)
                                : new ElementInput(e.code(), name(e.requirement()), e.note()))
                        .toList();
    }

    static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static Set<CaseStatus> statuses(List<String> raw) {
        Set<CaseStatus> result = EnumSet.noneOf(CaseStatus.class);
        if (raw == null) {
            return result;
        }
        for (String entry : raw) {
            for (String token : entry.split(",")) {
                if (token.isBlank()) {
                    continue;
                }
                try {
                    result.add(CaseStatus.valueOf(token.strip().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ex) {
                    throw ValidationException.of("status", ViolationCodes.INVALID_VALUE, "unknown status " + token);
                }
            }
        }
        return result;
    }
}
