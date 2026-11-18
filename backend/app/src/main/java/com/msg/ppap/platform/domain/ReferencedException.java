package com.msg.ppap.platform.domain;

/** Operation not allowed because the object is referenced by other objects. */
public class ReferencedException extends PpapException {

    public static final String CODE = "REFERENCED";

    public ReferencedException(String message) {
        super(Kind.CONFLICT, CODE, message);
    }
}
