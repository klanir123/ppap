package com.msg.ppap.approval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Excel form import settings.
 *
 * @param maxFileSize maximum size of a form workbook
 * @param maxRows maximum number of element table rows scanned when the template defines no last row
 * @param attachToElement element (cover sheet) the original form file is attached to; empty disables attaching
 */
@ConfigurationProperties("ppap.approval.form-import")
public record FormImportProperties(
        @DefaultValue("10MB") DataSize maxFileSize,
        @DefaultValue("500") int maxRows,
        @DefaultValue("PPF-01") String attachToElement) {}
