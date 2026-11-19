package com.msg.ppap.platform.domain;

import java.util.List;

/** Input violates validation rules; carries all field violations. */
public class ValidationException extends PpapException {

    /** Default code for validation failures. */
    public static final String CODE = "VALIDATION_FAILED";

    private final List<FieldViolation> violations;

    public ValidationException(List<FieldViolation> violations) {
        this(CODE, violations);
    }

    public ValidationException(String code, List<FieldViolation> violations) {
        this(Kind.INVALID, code, violations);
    }

    /** Rule violations with a different category, e.g. {@code UNPROCESSABLE} for business rule failures. */
    public ValidationException(Kind kind, String code, List<FieldViolation> violations) {
        super(kind, code, "Validation failed: " + summary(violations));
        this.violations = List.copyOf(violations);
    }

    /** Single violation shortcut. */
    public static ValidationException of(String field, String code, String message) {
        return new ValidationException(List.of(new FieldViolation(field, code, message)));
    }

    public List<FieldViolation> violations() {
        return violations;
    }

    private static String summary(List<FieldViolation> violations) {
        return violations.stream().map(v -> v.field() + " " + v.code()).toList().toString();
    }
}
