package com.msg.ppap.documents.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Document upload settings.
 *
 * @param maxSize maximum document size ({@code PPAP_UPLOAD_MAX_SIZE}, default 50 MB)
 */
@ConfigurationProperties("ppap.documents")
public record DocumentProperties(@DefaultValue("50MB") DataSize maxSize) {}
