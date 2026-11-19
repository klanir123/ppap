package com.msg.ppap.documents.adapter.in.rest;

import com.msg.ppap.documents.application.DocumentService;
import com.msg.ppap.platform.web.CreatedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Tenant-local documents: upload, metadata and content download. */
@RestController
@RequestMapping(DocumentController.PATH)
@Tag(name = "documents")
public class DocumentController {

    static final String PATH = "/api/v1/documents";

    /** Roles allowed to upload (quality roles of both sides and integrations). */
    static final String UPLOAD =
            "hasAnyRole('CUSTOMER_QUALITY_ENGINEER', 'SUPPLIER_QUALITY_MANAGER', 'SYSTEM_INTEGRATION')";

    private final DocumentService documents;

    public DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @CreatedResponse
    @PreAuthorize(UPLOAD)
    @Operation(
            operationId = "uploadDocument",
            summary = "Upload a document (content type checked by magic bytes against the whitelist, max. size "
                    + "PPAP_UPLOAD_MAX_SIZE)")
    public ResponseEntity<DocumentDto> upload(@RequestPart("file") MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            var info = documents.upload(file.getOriginalFilename(), in);
            URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path(PATH + "/{id}")
                    .buildAndExpand(info.id())
                    .toUri();
            return ResponseEntity.created(location).body(DocumentDto.from(info));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getDocument", summary = "Get document metadata")
    public DocumentDto get(@PathVariable UUID id) {
        return DocumentDto.from(documents.get(id));
    }

    @GetMapping(path = "/{id}/content", produces = MediaType.ALL_VALUE)
    @Operation(
            operationId = "getDocumentContent",
            summary = "Download the document content (nosniff, CSP sandbox, RFC 6266 Content-Disposition)",
            responses =
                    @ApiResponse(
                            responseCode = "200",
                            description = "Document content",
                            content =
                                    @Content(
                                            mediaType = "application/octet-stream",
                                            schema = @Schema(type = "string", format = "binary"))))
    public ResponseEntity<InputStreamResource> content(
            @PathVariable UUID id, @RequestParam(defaultValue = "attachment") Disposition disposition) {
        DocumentService.Content content = documents.open(id);
        var info = content.info();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(info.mediaType()))
                .contentLength(info.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition6266.of(disposition.value, info.fileName()))
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-cache")
                .eTag("\"" + info.sha256() + "\"")
                .body(new InputStreamResource(content.stored().content()));
    }

    /** Content disposition of downloads. */
    public enum Disposition {
        inline("inline"),
        attachment("attachment");

        private final String value;

        Disposition(String value) {
            this.value = value;
        }
    }
}
