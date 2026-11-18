package com.msg.ppap.masterdata.adapter.out.schema;

import com.msg.ppap.masterdata.application.port.out.ValueSchemaValidator;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Validates value schemas against the JSON Schema draft 2020-12 meta-schema (bundled with the validator, no network
 * access).
 */
@Component
class NetworkntValueSchemaValidator implements ValueSchemaValidator {

    static final String META_SCHEMA = "https://json-schema.org/draft/2020-12/schema";

    private final JsonMapper jsonMapper;
    private final Schema metaSchema;

    NetworkntValueSchemaValidator(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.metaSchema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(SchemaLocation.of(META_SCHEMA));
    }

    @Override
    public List<String> validate(Map<String, Object> schema) {
        JsonNode node = jsonMapper.valueToTree(schema);
        List<Error> errors = metaSchema.validate(node);
        return errors.stream().map(Error::toString).toList();
    }
}
