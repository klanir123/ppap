package com.msg.ppap.semantics.api;

import com.msg.ppap.platform.domain.FieldViolation;
import com.msg.ppap.platform.domain.ValidationException;
import java.util.List;

/**
 * An exchange payload violates its JSON Schema or is not valid JSON ({@code PAYLOAD_SCHEMA_VIOLATION}, 422). The
 * violations carry JSON pointers into the payload as {@code field}.
 */
public class PayloadValidationException extends ValidationException {

    public static final String CODE = "PAYLOAD_SCHEMA_VIOLATION";

    public PayloadValidationException(List<FieldViolation> violations) {
        super(Kind.UNPROCESSABLE, CODE, violations);
    }
}
