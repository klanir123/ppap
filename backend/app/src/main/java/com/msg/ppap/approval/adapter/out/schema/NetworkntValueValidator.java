package com.msg.ppap.approval.adapter.out.schema;

import com.msg.ppap.approval.domain.ValueValidator;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.path.NodePath;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Validates element values against value schemas (draft 2020-12) with networknt; compiled schemas are cached. */
@Component
class NetworkntValueValidator implements ValueValidator {

    private static final int CACHE_LIMIT = 500;

    private final JsonMapper jsonMapper;
    private final SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
    private final Map<String, Schema> cache = new ConcurrentHashMap<>();

    NetworkntValueValidator(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public List<Violation> validate(Map<String, Object> schema, Object values) {
        JsonNode schemaNode = jsonMapper.valueToTree(schema);
        String key = schemaNode.toString();
        if (cache.size() > CACHE_LIMIT) {
            cache.clear();
        }
        Schema compiled = cache.computeIfAbsent(key, k -> registry.getSchema(schemaNode));
        JsonNode instance = jsonMapper.valueToTree(values);
        return compiled.validate(instance).stream()
                .map(error -> new Violation(pointer(error), error.getMessage()))
                .toList();
    }

    static String pointer(Error error) {
        var out = new StringBuilder();
        NodePath path = error.getInstanceLocation();
        if (path != null) {
            for (int i = 0; i < path.getNameCount(); i++) {
                out.append('/')
                        .append(String.valueOf(path.getElement(i))
                                .replace("~", "~0")
                                .replace("/", "~1"));
            }
        }
        if ("required".equals(error.getKeyword()) && error.getProperty() != null) {
            out.append('/').append(error.getProperty().replace("~", "~0").replace("/", "~1"));
        }
        return out.toString();
    }
}
