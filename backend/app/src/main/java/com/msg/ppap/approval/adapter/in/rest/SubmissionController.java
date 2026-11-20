package com.msg.ppap.approval.adapter.in.rest;

import com.msg.ppap.approval.application.CaseCommands;
import com.msg.ppap.approval.application.ExcelImportService;
import com.msg.ppap.approval.application.ExcelPreview;
import com.msg.ppap.approval.application.ReviewService;
import com.msg.ppap.approval.application.SubmissionService;
import com.msg.ppap.approval.application.SubmissionView;
import com.msg.ppap.approval.domain.Seal;
import com.msg.ppap.platform.domain.PpapException;
import com.msg.ppap.platform.web.CreatedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Revisions of the supplier's submission (UC-05), payload export and Excel form import. */
@RestController
@RequestMapping(SubmissionController.PATH)
@Tag(name = "submissions")
public class SubmissionController {

    static final String PATH = "/api/v1/approval-cases/{id}/submissions";

    /** Header with the payload hash of exported payloads. */
    static final String PAYLOAD_HASH = "X-Payload-Hash";

    /** Header with the detached JWS of exported payloads. */
    static final String SIGNATURE = "X-Signature";

    private final SubmissionService submissions;
    private final ReviewService reviews;
    private final ExcelImportService excel;
    private final JsonMapper jsonMapper;

    public SubmissionController(
            SubmissionService submissions, ReviewService reviews, ExcelImportService excel, JsonMapper jsonMapper) {
        this.submissions = submissions;
        this.reviews = reviews;
        this.excel = excel;
        this.jsonMapper = jsonMapper;
    }

    @GetMapping
    @Operation(operationId = "listSubmissions", summary = "All revisions of a case (short form)")
    public List<SubmissionSummaryDto> list(@PathVariable UUID id) {
        return submissions.list(id).stream()
                .map(v -> ApprovalDtos.submissionSummary(v.submission()))
                .toList();
    }

    @PostMapping
    @CreatedResponse
    @PreAuthorize(ApprovalRest.SUPPLIER)
    @Operation(
            operationId = "createRevision",
            summary = "New draft revision after rejection or conditional approval (prefilled from the last revision)")
    public ResponseEntity<SubmissionDto> newRevision(
            @PathVariable UUID id, @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        SubmissionView view = submissions.newRevision(id, ApprovalRest.expectedVersion(ifMatch));
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(PATH + "/{revision}")
                .buildAndExpand(id, view.submission().revision())
                .toUri();
        return ResponseEntity.created(location)
                .eTag(ApprovalRest.etag(view.approvalCase().version()))
                .body(ApprovalDtos.submission(view));
    }

    @GetMapping("/draft")
    @Operation(operationId = "getDraftSubmission", summary = "The draft revision with completeness")
    public ResponseEntity<SubmissionDto> draft(@PathVariable UUID id) {
        return respond(submissions.draft(id));
    }

    @GetMapping("/{revision}")
    @Operation(operationId = "getSubmission", summary = "A revision with completeness, seal and decision")
    public ResponseEntity<SubmissionDto> get(@PathVariable UUID id, @PathVariable int revision) {
        return respond(submissions.get(id, revision));
    }

    @PutMapping("/draft/elements/{code}")
    @PreAuthorize(ApprovalRest.SUPPLIER)
    @Operation(
            operationId = "updateDraftElement",
            summary = "Self assessment, values (validated against the value schema) and comment of an element")
    public ResponseEntity<SubmissionDto> updateElement(
            @PathVariable UUID id,
            @PathVariable String code,
            @RequestBody ElementEvidenceRequest request,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return respond(submissions.updateEvidence(
                id,
                code,
                new CaseCommands.Evidence(
                        ApprovalCaseController.name(request.selfAssessment()), request.values(), request.comment()),
                ApprovalRest.expectedVersion(ifMatch)));
    }

    @PostMapping("/draft/elements/{code}/documents")
    @PreAuthorize(ApprovalRest.SUPPLIER)
    @Operation(operationId = "attachDraftElementDocument", summary = "Attach an uploaded document to an element")
    public ResponseEntity<SubmissionDto> attachDocument(
            @PathVariable UUID id,
            @PathVariable String code,
            @RequestBody DocumentReferenceRequest request,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return respond(
                submissions.attachDocument(id, code, request.documentId(), ApprovalRest.expectedVersion(ifMatch)));
    }

    @DeleteMapping("/draft/elements/{code}/documents/{documentId}")
    @PreAuthorize(ApprovalRest.SUPPLIER)
    @Operation(operationId = "detachDraftElementDocument", summary = "Detach a document from an element")
    public ResponseEntity<Void> detachDocument(
            @PathVariable UUID id,
            @PathVariable String code,
            @PathVariable UUID documentId,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        Long version = submissions.detachDocument(id, code, documentId, ApprovalRest.expectedVersion(ifMatch));
        return ResponseEntity.noContent().eTag(ApprovalRest.etag(version)).build();
    }

    @PutMapping("/draft/sub-supplier-references")
    @PreAuthorize(ApprovalRest.SUPPLIER)
    @Operation(
            operationId = "replaceSubSupplierReferences",
            summary = "Cascade (UC-09): references of the draft to own, approved sub-supplier cases (If-Match);"
                    + " 422 SUB_SUPPLIER_REFERENCE_INVALID otherwise")
    public ResponseEntity<SubmissionDto> replaceSubSupplierReferences(
            @PathVariable UUID id,
            @RequestBody SubSupplierReferencesRequest request,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return respond(submissions.replaceSubSupplierReferences(
                id, request.childCaseIds(), ApprovalRest.expectedVersion(ifMatch)));
    }

    @PostMapping("/{revision}/verify-signature")
    @PreAuthorize(ApprovalRest.ANY_QUALITY)
    @Operation(
            operationId = "verifySubmissionSignature",
            summary = "Verify the signature of a received revision again (UNVERIFIED: the partner keys are fetched"
                    + " via EDC first)")
    public ResponseEntity<SubmissionDto> verifySignature(@PathVariable UUID id, @PathVariable int revision) {
        return respond(submissions.verifySignature(id, revision));
    }

    @PostMapping("/draft/submit")
    @PreAuthorize(ApprovalRest.SUPPLIER)
    @Operation(
            operationId = "submitDraft",
            summary = "Submit the draft revision (complete → canonical payload, hash, JWS; immutable)")
    public ResponseEntity<SubmissionDto> submit(
            @PathVariable UUID id,
            @RequestBody SubmitRevisionRequest request,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return respond(submissions.submit(id, request.confirmation(), ApprovalRest.expectedVersion(ifMatch)));
    }

    @GetMapping(path = "/{revision}/payload", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "getSubmissionPayload",
            summary = "Canonical PpapSubmission payload (RFC 8785) of a submitted revision",
            responses =
                    @ApiResponse(
                            responseCode = "200",
                            description = "Canonical payload",
                            headers = {
                                @Header(name = PAYLOAD_HASH, description = "sha256:<hex> of the payload bytes"),
                                @Header(name = SIGNATURE, description = "Detached JWS (ES256, b64=false)")
                            },
                            content =
                                    @Content(
                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                            schema = @Schema(type = "object"))))
    public ResponseEntity<String> payload(@PathVariable UUID id, @PathVariable int revision) {
        return payloadResponse(submissions.payload(id, revision));
    }

    @GetMapping(path = "/{revision}/decision/payload", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "getDecisionPayload",
            summary = "Canonical PpapDecision payload (RFC 8785) of a decided revision",
            responses =
                    @ApiResponse(
                            responseCode = "200",
                            description = "Canonical payload",
                            headers = {
                                @Header(name = PAYLOAD_HASH, description = "sha256:<hex> of the payload bytes"),
                                @Header(name = SIGNATURE, description = "Detached JWS (ES256, b64=false)")
                            },
                            content =
                                    @Content(
                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                            schema = @Schema(type = "object"))))
    public ResponseEntity<String> decisionPayload(@PathVariable UUID id, @PathVariable int revision) {
        return payloadResponse(reviews.decisionPayload(id, revision));
    }

    @PostMapping(path = "/draft/excel-import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(ApprovalRest.SUPPLIER)
    @Operation(
            operationId = "previewExcelImport",
            summary = "Read a filled Excel form (template recognized by its marker cell) against the draft")
    public FormImportPreviewDto previewExcel(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String templateCode) {
        return ApprovalDtos.preview(excel.preview(id, bytes(file), templateCode));
    }

    @PostMapping(path = "/draft/excel-import/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(ApprovalRest.SUPPLIER)
    @Operation(
            operationId = "applyExcelImport",
            summary = "Apply selected rows of an Excel form to the draft; the file is attached to the cover sheet "
                    + "element")
    public ResponseEntity<SubmissionDto> applyExcel(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @RequestParam String templateCode,
            @Parameter(description = "JSON {\"elementCodes\": [...], \"overwrite\": false}")
                    @RequestPart(name = "selection", required = false)
                    String selection,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return respond(excel.apply(
                id,
                file.getOriginalFilename(),
                bytes(file),
                templateCode,
                selection(selection),
                ApprovalRest.expectedVersion(ifMatch)));
    }

    private ExcelPreview.Selection selection(String json) {
        if (json == null || json.isBlank()) {
            return new ExcelPreview.Selection(List.of(), false);
        }
        try {
            return jsonMapper.readValue(json, ExcelPreview.Selection.class);
        } catch (JacksonException ex) {
            throw new PpapException(PpapException.Kind.INVALID, "MALFORMED_REQUEST", "selection is not valid JSON");
        }
    }

    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new PpapException(PpapException.Kind.INVALID, "UNREADABLE_FILE", "File cannot be read");
        }
    }

    private static ResponseEntity<String> payloadResponse(Seal seal) {
        var builder =
                ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).header(PAYLOAD_HASH, seal.payloadHash());
        if (seal.signature().jws() != null) {
            builder.header(SIGNATURE, seal.signature().jws());
        }
        return builder.body(seal.payload());
    }

    private static ResponseEntity<SubmissionDto> respond(SubmissionView view) {
        return ApprovalRest.ok(
                ApprovalDtos.submission(view), view.approvalCase().version());
    }
}
