package com.msg.ppap.masterdata.application.port.out;

import java.util.List;
import java.util.Map;

/** Checks that a catalog element's value schema is a valid JSON Schema (draft 2020-12). */
public interface ValueSchemaValidator {

    /**
     * Validates the schema against the draft 2020-12 meta-schema.
     *
     * @return problems found, empty if the schema is valid
     */
    List<String> validate(Map<String, Object> schema);
}
