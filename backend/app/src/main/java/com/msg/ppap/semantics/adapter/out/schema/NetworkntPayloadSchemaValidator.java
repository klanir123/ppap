package com.msg.ppap.semantics.adapter.out.schema;

import com.msg.ppap.platform.domain.FieldViolation;
import com.msg.ppap.semantics.api.ExchangeModels;
import com.msg.ppap.semantics.application.port.out.PayloadSchemaValidator;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.path.NodePath;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Validates payloads with networknt json-schema-validator against the JSON Schemas <b>generated from the SAMM
 * models</b> ({@code backend/semantics-models}, ESMF {@code generateJsonSchema}, classpath {@code
 * samm/com.msg.ppap/<version>/<Aspect>.schema.json}, JSON Schema draft 4, no network access).
 *
 * <p>The exchange format is validated closed-world: on loading, every object schema with {@code properties} gets
 * {@code additionalProperties: false} (SAMM/ESMF do not express this; unknown members would otherwise be accepted,
 * hashed and signed without being understood). TODO(EG): agree on closed-world validation in the standard.
 */
@Component
class NetworkntPayloadSchemaValidator implements PayloadSchemaValidator {

    static final String GENERATED_SCHEMAS = "samm/com.msg.ppap/" + ExchangeModels.VERSION + "/";

    static final Map<String, String> SCHEMA_FILES = Map.of(
            ExchangeModels.PPAP_REQUEST, GENERATED_SCHEMAS + "PpapRequest.schema.json",
            ExchangeModels.PPAP_SUBMISSION, GENERATED_SCHEMAS + "PpapSubmission.schema.json",
            ExchangeModels.PPAP_SUBMISSION_AVAILABLE, GENERATED_SCHEMAS + "PpapSubmissionAvailable.schema.json",
            ExchangeModels.PPAP_DECISION, GENERATED_SCHEMAS + "PpapDecision.schema.json",
            ExchangeModels.PPAP_STATUS_UPDATE, GENERATED_SCHEMAS + "PpapStatusUpdate.schema.json",
            ExchangeModels.PPAP_REQUEST_WITHDRAWN, GENERATED_SCHEMAS + "PpapRequestWithdrawn.schema.json",
            ExchangeModels.PPAP_NOTIFICATION, GENERATED_SCHEMAS + "PpapNotification.schema.json");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Map<String, Schema> schemas;

    NetworkntPayloadSchemaValidator() {
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_4);
        this.schemas = Map.copyOf(SCHEMA_FILES.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> load(registry, e.getValue()))));
    }

    @Override
    public List<FieldViolation> validate(String modelUrn, JsonNode payload) {
        Schema schema = schemas.get(modelUrn);
        if (schema == null) {
            throw new IllegalArgumentException("No schema for model " + modelUrn);
        }
        return schema.validate(payload).stream()
                .map(error -> new FieldViolation(pointer(error), "SCHEMA_" + upper(error.getKeyword()), message(error)))
                .toList();
    }

    /** RFC 6901 JSON pointer of the error location (for {@code required}: of the missing property). */
    static String pointer(Error error) {
        StringBuilder out = new StringBuilder();
        NodePath path = error.getInstanceLocation();
        if (path != null) {
            for (int i = 0; i < path.getNameCount(); i++) {
                out.append('/').append(escape(String.valueOf(path.getElement(i))));
            }
        }
        if ("required".equals(error.getKeyword()) && error.getProperty() != null) {
            out.append('/').append(escape(error.getProperty()));
        }
        return out.toString();
    }

    private static String message(Error error) {
        return error.getMessage() == null ? error.toString() : error.getMessage();
    }

    private static String upper(String keyword) {
        return keyword == null ? "VIOLATION" : keyword.toUpperCase(java.util.Locale.ROOT);
    }

    private static String escape(String token) {
        return token.replace("~", "~0").replace("/", "~1");
    }

    private static Schema load(SchemaRegistry registry, String resource) {
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            JsonNode schema = JSON.readTree(in);
            if (schema instanceof ObjectNode root) {
                // ESMF writes the draft-04 IRI without the trailing '#'
                root.put("$schema", "http://json-schema.org/draft-04/schema#");
            }
            closeObjects(schema);
            return registry.getSchema(schema);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot load schema " + resource, ex);
        }
    }

    /** Adds {@code additionalProperties: false} to every object schema that declares properties. */
    static void closeObjects(JsonNode node) {
        if (node instanceof ObjectNode object) {
            if (object.has("properties")
                    && object.get("properties").isObject()
                    && !object.has("additionalProperties")) {
                object.put("additionalProperties", false);
            }
            object.properties().forEach(entry -> {
                if (!"properties".equals(entry.getKey())) {
                    closeObjects(entry.getValue());
                } else {
                    entry.getValue().forEach(NetworkntPayloadSchemaValidator::closeObjects);
                }
            });
        } else if (node != null && node.isArray()) {
            node.forEach(NetworkntPayloadSchemaValidator::closeObjects);
        }
    }
}
