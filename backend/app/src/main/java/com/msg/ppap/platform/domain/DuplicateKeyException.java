package com.msg.ppap.platform.domain;

/** Business key is already used by another object of the tenant. */
public class DuplicateKeyException extends PpapException {

    public static final String CODE = "DUPLICATE_KEY";

    private final String field;

    public DuplicateKeyException(String objectType, String field, Object key) {
        super(Kind.CONFLICT, CODE, objectType + " with " + field + " '" + key + "' already exists");
        this.field = field;
    }

    /** Field holding the duplicate key. */
    public String field() {
        return field;
    }
}
