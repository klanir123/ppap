package com.msg.ppap.exchange.adapter.in.rest;

import com.msg.ppap.approval.application.port.in.PartnerSharingUseCase;
import com.msg.ppap.approval.application.port.in.PartnerSharingUseCase.SharedSubmission;
import com.msg.ppap.documents.api.DocumentContent;
import com.msg.ppap.documents.api.Documents;
import com.msg.ppap.exchange.application.ExchangeAssets;
import com.msg.ppap.exchange.application.SubmissionPullService;
import com.msg.ppap.platform.domain.NotFoundException;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Content endpoints called only by the own EDC data plane (phase 5, docs/api/pull-api.md): the submodel {@code
 * PpapSubmission} of a submitted revision (asset {@code ppap-submission-<BPNL>}) and documents (asset {@code
 * ppap-documents-<BPNL>}).
 *
 * <p>Authorization in two stages: the shared secret proves the call comes from the own data plane ({@code 401}); the
 * consumer BPN of the contract agreement forwarded by the data plane ({@code Edc-Bpn}, verified in Tractus-X EDC 0.13)
 * must be the partner of the case – a submitted revision of a case with exactly this partner, or a document attached
 * to a sent request / a submitted revision of such a case. Missing {@code Edc-Bpn} → {@code 403}; anything not shared
 * with that partner (drafts, other partners, unknown ids) → {@code 404} without revealing whether it exists. Every
 * served pull is logged (access evidence).
 */
@RestController
@Hidden
class EdcContentController {

    private static final Logger LOG = LoggerFactory.getLogger(EdcContentController.class);

    private final CallbackAuthentication authentication;
    private final PartnerSharingUseCase sharing;
    private final Documents documents;

    EdcContentController(CallbackAuthentication authentication, PartnerSharingUseCase sharing, Documents documents) {
        this.authentication = authentication;
        this.sharing = sharing;
        this.documents = documents;
    }

    @GetMapping({
        ExchangeAssets.SUBMODELS_PATH + "/{exchangeId}/{revision}",
        ExchangeAssets.SUBMODELS_PATH + "/{exchangeId}/{revision}/$value"
    })
    ResponseEntity<byte[]> submodel(
            HttpServletRequest request, @PathVariable String exchangeId, @PathVariable String revision) {
        Optional<ResponseEntity<byte[]>> denied = deny(request);
        if (denied.isPresent()) {
            return denied.get();
        }
        String consumer = request.getHeader(CallbackAuthentication.EDC_BPN_HEADER);
        Optional<SharedSubmission> shared = parse(exchangeId)
                .flatMap(id -> parseRevision(revision).flatMap(rev -> sharing.sharedSubmission(id, rev, consumer)));
        if (shared.isEmpty()) {
            LOG.warn("Submodel {}/{} not shared with {} (404)", exchangeId, revision, consumer);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        SharedSubmission s = shared.get();
        LOG.info(
                "Served submodel PpapSubmission {}/{} ({}) to {} (agreement {})",
                exchangeId,
                revision,
                s.payloadHash(),
                consumer,
                request.getHeader(CallbackAuthentication.EDC_AGREEMENT_HEADER));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(SubmissionPullService.HASH_HEADER, s.payloadHash())
                .header(SubmissionPullService.SIGNATURE_HEADER, s.jws())
                .body(s.payload());
    }

    /**
     * Detached JWS and hash of a submitted revision: the Tractus-X EDC 0.13 data plane does not forward response
     * headers of the backend to the consumer (verified), so the signature is a sibling resource of the submodel on the
     * same asset ({@code proxyPath}).
     */
    @GetMapping(
            path = ExchangeAssets.SUBMODELS_PATH + "/{exchangeId}/{revision}/signature",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> signature(
            HttpServletRequest request, @PathVariable String exchangeId, @PathVariable String revision) {
        Optional<ResponseEntity<byte[]>> denied = deny(request);
        if (denied.isPresent()) {
            return denied.get();
        }
        String consumer = request.getHeader(CallbackAuthentication.EDC_BPN_HEADER);
        Optional<SharedSubmission> shared = parse(exchangeId)
                .flatMap(id -> parseRevision(revision).flatMap(rev -> sharing.sharedSubmission(id, rev, consumer)));
        if (shared.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(java.util.Map.of(
                "payloadHash", shared.get().payloadHash(),
                "alg", "ES256",
                "signature", shared.get().jws()));
    }

    @GetMapping(ExchangeAssets.DOCUMENTS_PATH + "/{sha256}")
    ResponseEntity<?> document(HttpServletRequest request, @PathVariable String sha256) throws IOException {
        Optional<ResponseEntity<byte[]>> denied = deny(request);
        if (denied.isPresent()) {
            return denied.get();
        }
        String consumer = request.getHeader(CallbackAuthentication.EDC_BPN_HEADER);
        Optional<UUID> documentId = sharing.sharedDocument(sha256, consumer);
        if (documentId.isEmpty()) {
            LOG.warn("Document {} not shared with {} (404)", sha256, consumer);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        DocumentContent content;
        try {
            content = documents.openContent(documentId.get());
        } catch (NotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        LOG.info(
                "Served document {} ({} bytes) to {} (agreement {})",
                sha256,
                content.contentLength(),
                consumer,
                request.getHeader(CallbackAuthentication.EDC_AGREEMENT_HEADER));
        InputStream body = content.content();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.info().mediaType()))
                .contentLength(content.contentLength())
                .header("X-Ppap-Sha256", content.info().sha256())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new InputStreamResource(body));
    }

    private Optional<ResponseEntity<byte[]>> deny(HttpServletRequest request) {
        if (!authentication.authorized(request)) {
            return Optional.of(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        String consumer = request.getHeader(CallbackAuthentication.EDC_BPN_HEADER);
        if (consumer == null || consumer.isBlank()) {
            LOG.warn(
                    "Rejected {}: no {} header from the data plane",
                    request.getRequestURI(),
                    CallbackAuthentication.EDC_BPN_HEADER);
            return Optional.of(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
        }
        return Optional.empty();
    }

    private static Optional<UUID> parse(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static Optional<Integer> parseRevision(String value) {
        try {
            int revision = Integer.parseInt(value);
            return revision >= 1 ? Optional.of(revision) : Optional.empty();
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
