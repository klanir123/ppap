package com.msg.ppap.masterdata.application.imports;

/**
 * Validation message of an import row.
 *
 * @param field affected field or {@code null} for row-level messages
 * @param code stable code
 * @param message English message
 */
public record ImportMessage(String field, String code, String message) {}
