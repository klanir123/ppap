package com.msg.ppap.approval.domain;

import com.msg.ppap.platform.domain.FieldViolation;
import com.msg.ppap.platform.domain.PpapException;
import com.msg.ppap.platform.domain.ValidationException;
import java.util.List;

/** Error codes and exception factories of the approval domain. */
public final class ApprovalErrors {

    public static final String INVALID_STATE_TRANSITION = "INVALID_STATE_TRANSITION";
    public static final String WRONG_CASE_ROLE = "WRONG_CASE_ROLE";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String INCOMPLETE_SUBMISSION = "INCOMPLETE_SUBMISSION";
    public static final String VALUE_SCHEMA_VIOLATION = "VALUE_SCHEMA_VIOLATION";
    public static final String PARTNER_NOT_EXCHANGE_READY = "PARTNER_NOT_EXCHANGE_READY";
    public static final String NO_ELEMENTS = "NO_ELEMENTS";
    public static final String DUE_DATE_IN_PAST = "DUE_DATE_IN_PAST";
    public static final String CONFIRMATION_REQUIRED = "CONFIRMATION_REQUIRED";
    public static final String REJECTION_REASON_REQUIRED = "REJECTION_REASON_REQUIRED";
    public static final String CONDITIONS_REQUIRED = "CONDITIONS_REQUIRED";
    public static final String ELEMENTS_NOT_ACCEPTED = "ELEMENTS_NOT_ACCEPTED";
    public static final String UNKNOWN_ELEMENT = "UNKNOWN_ELEMENT";
    public static final String PAYLOAD_HASH_MISMATCH = "PAYLOAD_HASH_MISMATCH";
    public static final String SIGNATURE_INVALID = "SIGNATURE_INVALID";
    public static final String REVISION_CONFLICT = "REVISION_CONFLICT";
    public static final String RECENT_AUTH_REQUIRED = "RECENT_AUTH_REQUIRED";

    private ApprovalErrors() {}

    /** Action not allowed in the current status (409). */
    public static PpapException invalidTransition(CaseStatus current, CaseAction action) {
        return new Problem(
                        PpapException.Kind.CONFLICT,
                        INVALID_STATE_TRANSITION,
                        "Action " + action + " is not allowed in status " + current)
                .with("currentStatus", current == null ? null : current.name())
                .with("action", action.name());
    }

    /** Action not allowed for the own role in the case (409). */
    public static PpapException wrongCaseRole(CaseRole ownRole, CaseAction action, String reason) {
        return new Problem(
                        PpapException.Kind.CONFLICT,
                        WRONG_CASE_ROLE,
                        "Action " + action + " is not allowed for the " + ownRole + " side: " + reason)
                .with("ownRole", ownRole.name())
                .with("action", action.name());
    }

    /** User lacks the role for the action (403). */
    public static PpapException forbidden(CaseAction action) {
        return new Problem(PpapException.Kind.FORBIDDEN, FORBIDDEN, "Missing role for action " + action)
                .with("action", action.name());
    }

    /** Business rule violation with a single message (422). */
    public static PpapException unprocessable(String code, String message) {
        return new Problem(PpapException.Kind.UNPROCESSABLE, code, message);
    }

    /** Business rule violations with field details (422). */
    public static ValidationException rule(String code, List<FieldViolation> violations) {
        return new ValidationException(PpapException.Kind.UNPROCESSABLE, code, violations);
    }

    /** Problem with additional properties. */
    static final class Problem extends PpapException {

        Problem(Kind kind, String code, String message) {
            super(kind, code, message);
        }

        Problem with(String name, Object value) {
            withProperty(name, value);
            return this;
        }
    }
}
