package com.msg.ppap.platform.domain;

/** Requested object does not exist (for the current tenant). */
public class NotFoundException extends PpapException {

    public static final String CODE = "NOT_FOUND";

    public NotFoundException(String objectType, Object key) {
        super(Kind.NOT_FOUND, CODE, objectType + " '" + key + "' not found");
    }
}
