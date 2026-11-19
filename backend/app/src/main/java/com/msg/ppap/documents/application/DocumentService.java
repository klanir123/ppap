package com.msg.ppap.documents.application;

import com.msg.ppap.documents.api.DocumentInfo;
import com.msg.ppap.documents.api.Documents;
import com.msg.ppap.documents.application.port.out.ContentTypeDetector;
import com.msg.ppap.documents.application.port.out.DocumentRepository;
import com.msg.ppap.documents.application.port.out.DocumentStorage;
import com.msg.ppap.documents.application.port.out.MalwareScanner;
import com.msg.ppap.documents.application.port.out.StoredDocument;
import com.msg.ppap.documents.config.DocumentProperties;
import com.msg.ppap.documents.domain.AllowedMediaTypes;
import com.msg.ppap.documents.domain.Document;
import com.msg.ppap.documents.domain.DocumentErrors;
import com.msg.ppap.documents.domain.FileNames;
import com.msg.ppap.identity.ActorProvider;
import com.msg.ppap.identity.TenantContext;
import com.msg.ppap.platform.domain.Ids;
import com.msg.ppap.platform.domain.NotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Document upload and lookup. Uploads are streamed to a temporary file while the SHA-256 is computed and the size
 * limit enforced; the content type is detected from the magic bytes and checked against the whitelist; the content
 * is stored content-addressed ({@code <tenant>/<sha256>}, uploaded only once).
 */
@Service
public class DocumentService implements Documents {

    /** Object type in audit events. */
    public static final String OBJECT_TYPE = "DOCUMENT";

    private static final int BUFFER = 64 * 1024;

    private final DocumentRepository repository;
    private final DocumentStorage storage;
    private final ContentTypeDetector detector;
    private final MalwareScanner scanner;
    private final DocumentProperties properties;
    private final TenantContext tenant;
    private final ActorProvider actors;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public DocumentService(
            DocumentRepository repository,
            DocumentStorage storage,
            ContentTypeDetector detector,
            MalwareScanner scanner,
            DocumentProperties properties,
            TenantContext tenant,
            ActorProvider actors,
            ApplicationEventPublisher events,
            Clock clock) {
        this.repository = repository;
        this.storage = storage;
        this.detector = detector;
        this.scanner = scanner;
        this.properties = properties;
        this.tenant = tenant;
        this.actors = actors;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public DocumentInfo upload(String rawFileName, InputStream content) {
        String fileName = FileNames.sanitize(rawFileName, "document");
        Path temp = null;
        try {
            temp = Files.createTempFile("ppap-upload-", ".bin");
            long maxBytes = properties.maxSize().toBytes();
            MessageDigest digest = sha256();
            long size;
            try (OutputStream out = new DigestOutputStream(Files.newOutputStream(temp), digest)) {
                size = copyLimited(content, out, maxBytes);
            }
            if (size == 0) {
                throw DocumentErrors.empty();
            }
            String mediaType = AllowedMediaTypes.baseType(detector.detect(temp, fileName));
            if (!AllowedMediaTypes.isAllowed(mediaType)) {
                throw DocumentErrors.unsupported(mediaType);
            }
            Optional<String> finding = scanner.scan(temp, fileName);
            if (finding.isPresent()) {
                throw DocumentErrors.malware(finding.get());
            }
            String sha256 = HexFormat.of().formatHex(digest.digest());
            var document = new Document(
                    Ids.newId(),
                    tenant.tenantId(),
                    fileName,
                    mediaType,
                    size,
                    sha256,
                    clock.instant().truncatedTo(ChronoUnit.MICROS),
                    actors.currentActor());
            String key = document.storageKey();
            if (!storage.exists(key)) {
                try (InputStream in = Files.newInputStream(temp)) {
                    storage.put(key, in, size, mediaType);
                }
            }
            repository.insert(document);
            events.publishEvent(new DocumentUploaded(
                    tenant.tenantId(),
                    document.uploadedAt(),
                    document.uploadedBy(),
                    document.id(),
                    fileName,
                    mediaType,
                    size,
                    sha256));
            return toInfo(document);
        } catch (IOException ex) {
            throw new UncheckedIOException("Upload failed", ex);
        } finally {
            deleteQuietly(temp);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DocumentInfo> find(UUID id) {
        return repository.findById(id).map(DocumentService::toInfo);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentInfo get(UUID id) {
        return find(id).orElseThrow(() -> new NotFoundException("Document", id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentInfo> findAll(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return repository.findAllById(ids).stream().map(DocumentService::toInfo).toList();
    }

    /**
     * Opens the content of a document. The caller must close the stream.
     *
     * @throws NotFoundException if the document or its content does not exist
     */
    @Transactional(readOnly = true)
    public Content open(UUID id) {
        Document document = repository.findById(id).orElseThrow(() -> new NotFoundException("Document", id));
        StoredDocument stored =
                storage.get(document.storageKey()).orElseThrow(() -> new NotFoundException("Document content", id));
        return new Content(toInfo(document), stored);
    }

    /** Document metadata with its content stream. */
    public record Content(DocumentInfo info, StoredDocument stored) implements AutoCloseable {

        @Override
        public void close() throws IOException {
            stored.close();
        }
    }

    static long copyLimited(InputStream in, OutputStream out, long maxBytes) throws IOException {
        byte[] buffer = new byte[BUFFER];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw DocumentErrors.tooLarge(maxBytes);
            }
            out.write(buffer, 0, read);
        }
        return total;
    }

    private static DocumentInfo toInfo(Document d) {
        return new DocumentInfo(
                d.id(), d.fileName(), d.mediaType(), d.size(), d.sha256(), d.uploadedAt(), d.uploadedBy());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void deleteQuietly(Path temp) {
        if (temp != null) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // temp files are cleaned up by the OS
            }
        }
    }
}
