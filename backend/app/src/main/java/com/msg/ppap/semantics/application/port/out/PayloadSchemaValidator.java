package com.msg.ppap.semantics.application.port.out;

import com.msg.ppap.platform.domain.FieldViolation;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** Validates exchange payloads against the JSON Schema of their semantic model. */
public interface PayloadSchemaValidator {

    /**
     * Validates the payload.
     *
     * @param modelUrn model URN identifying the schema, e.g. {@code urn:samm:com.msg.ppap:0.1.0#PpapSubmission}
     * @return violations with JSON pointers as field, empty if valid
     * @throws IllegalArgumentException if no schema is known for the URN
     */
    List<FieldViolation> validate(String modelUrn, JsonNode payload);
}
