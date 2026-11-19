package com.msg.ppap.documents.application.port.out;

import java.io.IOException;
import java.nio.file.Path;

/** Detects the media type of a file from its content (magic bytes), using the file name only as a hint. */
public interface ContentTypeDetector {

    /** Detected media type, e.g. {@code application/pdf}; {@code application/octet-stream} if unknown. */
    String detect(Path content, String fileName) throws IOException;
}
