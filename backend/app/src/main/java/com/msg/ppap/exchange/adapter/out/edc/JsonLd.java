package com.msg.ppap.exchange.adapter.out.edc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * JSON-LD vocabulary of the Tractus-X EDC 0.13 management API and helpers to read compacted responses. Responses are
 * compacted with {@code @vocab = edc}, but depending on the context a property may also appear prefixed or expanded;
 * single values are compacted to scalars, several to arrays.
 */
final class JsonLd {

    static final String EDC_NS = "https://w3id.org/edc/v0.0.1/ns/";
    static final String TX_NS = "https://w3id.org/tractusx/v0.0.1/ns/";
    static final String ODRL_PROFILE_CONTEXT = "https://w3id.org/dspace/2025/1/odrl-profile.jsonld";
    static final String CX_POLICY_CONTEXT = "https://w3id.org/catenax/2025/9/policy/context.jsonld";
    static final String DSP_PROTOCOL = "dataspace-protocol-http:2025-1";

    private JsonLd() {}

    /** {@code {"@vocab": edc}}. */
    static Map<String, Object> edcContext() {
        return Map.of("@vocab", EDC_NS);
    }

    /** Context of policies and contract requests (ODRL profile DSP 2025-1, Catena-X policy 2025/9, edc). */
    static List<Object> policyContext() {
        return List.of(ODRL_PROFILE_CONTEXT, CX_POLICY_CONTEXT, edcContext());
    }

    /** Context of assets (edc, Dublin Core, Catena-X taxonomy and common ontology). */
    static Map<String, Object> assetContext() {
        var context = new LinkedHashMap<String, Object>();
        context.put("@vocab", EDC_NS);
        context.put("dct", "http://purl.org/dc/terms/");
        context.put("cx-taxo", "https://w3id.org/catenax/taxonomy#");
        context.put("cx-common", "https://w3id.org/catenax/ontology/common#");
        return context;
    }

    /** Query spec with a single equality filter. */
    static Map<String, Object> querySpec(String operandLeft, Object operandRight, int limit) {
        var spec = new LinkedHashMap<String, Object>();
        spec.put("@context", edcContext());
        spec.put("@type", "QuerySpec");
        spec.put("limit", limit);
        if (operandLeft != null) {
            spec.put("filterExpression", List.of(filter(operandLeft, operandRight)));
        }
        return spec;
    }

    static Map<String, Object> filter(String operandLeft, Object operandRight) {
        return Map.of("operandLeft", operandLeft, "operator", "=", "operandRight", operandRight);
    }

    /** Property by plain, {@code edc:}-prefixed or expanded name. */
    static JsonNode property(JsonNode node, String name) {
        if (node == null) {
            return null;
        }
        for (String candidate : List.of(name, "edc:" + name, EDC_NS + name, "dcat:" + name, "odrl:" + name)) {
            JsonNode value = node.get(candidate);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    /** Text value (unwraps {@code {"@value": …}} and {@code {"@id": …}}). */
    static String text(JsonNode node, String name) {
        JsonNode value = property(node, name);
        return value == null ? null : scalar(value);
    }

    static String scalar(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isObject()) {
            JsonNode inner = value.has("@value") ? value.get("@value") : value.get("@id");
            return inner == null ? null : inner.asString();
        }
        if (value.isArray()) {
            return value.isEmpty() ? null : scalar(value.get(0));
        }
        return value.asString();
    }

    /** Values of a property that may be a scalar or an array. */
    static List<JsonNode> list(JsonNode node, String name) {
        JsonNode value = name == null ? node : property(node, name);
        List<JsonNode> out = new ArrayList<>();
        if (value == null || value.isNull()) {
            return out;
        }
        if (value.isArray()) {
            value.forEach(out::add);
        } else {
            out.add(value);
        }
        return out;
    }

    /** Text values of a property that may be a scalar or an array (e.g. {@code tx:groups}). */
    static List<String> texts(JsonNode node, String name) {
        return list(node, name).stream()
                .map(JsonLd::scalar)
                .filter(s -> s != null)
                .toList();
    }
}
