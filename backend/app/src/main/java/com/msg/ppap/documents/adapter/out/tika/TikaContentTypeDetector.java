package com.msg.ppap.documents.adapter.out.tika;

import com.msg.ppap.documents.application.port.out.ContentTypeDetector;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

/**
 * Content type detection with Apache Tika (core only: magic bytes from {@code tika-mimetypes.xml}). The file name is
 * only used to refine a magic match into a subtype (e.g. ZIP → XLSX, plain text → CSV); a name never overrides
 * conflicting magic bytes.
 */
@Component
class TikaContentTypeDetector implements ContentTypeDetector {

    private final Tika tika = new Tika();

    @Override
    public String detect(Path content, String fileName) throws IOException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(content))) {
            return tika.detect(in, fileName);
        }
    }
}
