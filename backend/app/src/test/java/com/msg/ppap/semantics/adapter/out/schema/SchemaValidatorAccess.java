package com.msg.ppap.semantics.adapter.out.schema;

import com.msg.ppap.semantics.application.port.out.PayloadSchemaValidator;

/** Test access to the package-private schema validator adapter. */
public final class SchemaValidatorAccess {

    private SchemaValidatorAccess() {}

    public static PayloadSchemaValidator validator() {
        return new NetworkntPayloadSchemaValidator();
    }
}
