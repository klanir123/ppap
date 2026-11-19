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

/**
 * Validates payloads with networknt json-schema-validator against the bundled schemas
 * ({@code classpath:schemas/ppap-*-<version>.schema.json}, draft 2020-12, no network access).
 */
@Component
class NetworkntPayloadSchemaValidator implements PayloadSchemaValidator {

    static final Map<String, String> SCHEMA_FILES = Map.of(
            ExchangeModels.PPAP_REQUEST, "schemas/ppap-request-0.1.0.schema.json",
            ExchangeModels.PPAP_SUBMISSION, "schemas/ppap-submission-0.1.0.schema.json",
            ExchangeModels.PPAP_DECISION, "schemas/ppap-decision-0.1.0.schema.json");

    private final Map<String, Schema> schemas;

    NetworkntPayloadSchemaValidator() {
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
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
            return registry.getSchema(in);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot load schema " + resource, ex);
        }
    }
}
