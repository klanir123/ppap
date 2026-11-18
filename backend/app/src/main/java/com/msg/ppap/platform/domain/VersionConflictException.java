package com.msg.ppap.platform.domain;

/** Optimistic locking failure: the client's version ({@code If-Match}) is outdated. */
public class VersionConflictException extends PpapException {

    public static final String CODE = "VERSION_CONFLICT";

    public VersionConflictException(String objectType, Object id, long expected, long actual) {
        super(
                Kind.PRECONDITION_FAILED,
                CODE,
                objectType + " '" + id + "' has version " + actual + ", expected " + expected);
    }

    public VersionConflictException(String message) {
        super(Kind.PRECONDITION_FAILED, CODE, message);
    }
}
