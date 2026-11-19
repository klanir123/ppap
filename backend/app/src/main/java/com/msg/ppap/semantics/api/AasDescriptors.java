package com.msg.ppap.semantics.api;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * AAS Registry API 3.x formats of the PPAP digital twin (Digital Twin KIT, Tractus-X DTR 0.13): building the shell
 * descriptor of an approval case on the supplier side and reading lookup results, shell descriptors and the {@code
 * subprotocolBody} of submodel endpoints on the customer side. Pure functions, no I/O.
 */
public final class AasDescriptors {

    /** {@code semanticId} of the submodel per revision. */
    public static final String SUBMISSION_SEMANTIC_ID = ExchangeModels.PPAP_SUBMISSION;

    /** Endpoint interface of submodel descriptors (AAS Part 2, 3.0). */
    public static final String SUBMODEL_INTERFACE = "SUBMODEL-3.0";

    /** Sub-protocol of Catena-X submodel endpoints. */
    public static final String SUBPROTOCOL_DSP = "DSP";

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

    private AasDescriptors() {}

    /**
     * Twin of an approval case (supplier side).
     *
     * @param caseNumber supplier's case number ({@code idShort})
     * @param customerBpnl BPNL of the customer – the only {@code externalSubjectId} that may see the twin
     * @param supplierBpnl own BPNL ({@code manufacturerId})
     * @param customerPartId customer part number, may be {@code null}
     * @param manufacturerPartId supplier part number, may be {@code null} (unmapped part)
     * @param submodels one per submitted revision
     */
    public record TwinSpec(
            UUID exchangeId,
            String caseNumber,
            String customerBpnl,
            String supplierBpnl,
            String customerPartId,
            String manufacturerPartId,
            List<SubmodelSpec> submodels) {

        public TwinSpec {
            Objects.requireNonNull(exchangeId, "exchangeId");
            Objects.requireNonNull(caseNumber, "caseNumber");
            Objects.requireNonNull(customerBpnl, "customerBpnl");
            Objects.requireNonNull(supplierBpnl, "supplierBpnl");
            submodels = List.copyOf(submodels);
        }
    }

    /**
     * Submodel descriptor of one submitted revision.
     *
     * @param href endpoint as seen by the customer: {@code <own data plane public URL>/<exchangeId>/<revision>}
     * @param dspEndpoint own DSP endpoint ({@code subprotocolBody})
     */
    public record SubmodelSpec(int revision, String href, String dspEndpoint) {}

    /** {@code specificAssetId} as read from a descriptor. */
    public record AssetIdView(String name, String value, String externalSubjectId) {}

    /** Submodel descriptor as read from the registry (first endpoint). */
    public record SubmodelView(String id, String semanticId, String href, String subprotocol, String subprotocolBody) {

        /** Parsed {@code subprotocolBody} if the endpoint uses the DSP sub-protocol. */
        public Optional<SubprotocolBody> dsp() {
            return SUBPROTOCOL_DSP.equalsIgnoreCase(subprotocol)
                    ? SubprotocolBody.parse(subprotocolBody)
                    : Optional.empty();
        }
    }

    /** Shell descriptor as read from the registry. */
    public record ShellView(
            String id,
            String idShort,
            String globalAssetId,
            List<AssetIdView> specificAssetIds,
            List<SubmodelView> submodels) {

        public ShellView {
            specificAssetIds = List.copyOf(specificAssetIds);
            submodels = List.copyOf(submodels);
        }

        public Optional<SubmodelView> submodel(String submodelId) {
            return submodels.stream().filter(s -> s.id().equals(submodelId)).findFirst();
        }
    }

    /**
     * {@code subprotocolBody} of a DSP endpoint: {@code id=<assetId>;dspEndpoint=<control plane DSP URL>} (Digital
     * Twin KIT). Unknown keys are ignored.
     */
    public record SubprotocolBody(String assetId, String dspEndpoint) {

        public static String format(String assetId, String dspEndpoint) {
            return "id=" + assetId + ";dspEndpoint=" + dspEndpoint;
        }

        /** Parses the body; empty if {@code id} or {@code dspEndpoint} is missing. */
        public static Optional<SubprotocolBody> parse(String body) {
            if (body == null || body.isBlank()) {
                return Optional.empty();
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (String part : body.split(";")) {
                int eq = part.indexOf('=');
                if (eq > 0) {
                    values.putIfAbsent(
                            part.substring(0, eq).trim(), part.substring(eq + 1).trim());
                }
            }
            String id = values.get("id");
            String dsp = values.get("dspEndpoint");
            return id == null || id.isEmpty() || dsp == null || dsp.isEmpty()
                    ? Optional.empty()
                    : Optional.of(new SubprotocolBody(id, dsp));
        }
    }

    /** Shell descriptor (JSON) of the twin. */
    public static byte[] shellDescriptor(TwinSpec spec) {
        var assetIds = new ArrayList<Map<String, Object>>();
        addAssetId(assetIds, "customerPartId", spec.customerPartId(), spec.customerBpnl());
        addAssetId(assetIds, "manufacturerPartId", spec.manufacturerPartId(), spec.customerBpnl());
        addAssetId(assetIds, "manufacturerId", spec.supplierBpnl(), spec.customerBpnl());
        addAssetId(assetIds, DigitalTwinIds.EXCHANGE_ID_KEY, spec.exchangeId().toString(), spec.customerBpnl());
        var shell = new LinkedHashMap<String, Object>();
        shell.put("id", DigitalTwinIds.shellId(spec.exchangeId()));
        shell.put("idShort", DigitalTwinIds.idShort(spec.caseNumber()));
        shell.put("globalAssetId", DigitalTwinIds.shellId(spec.exchangeId()));
        shell.put("specificAssetIds", assetIds);
        shell.put(
                "submodelDescriptors",
                spec.submodels().stream()
                        .map(s -> submodelDescriptor(spec.exchangeId(), spec.customerBpnl(), s))
                        .toList());
        return JSON.writeValueAsBytes(shell);
    }

    private static Map<String, Object> submodelDescriptor(UUID exchangeId, String customerBpnl, SubmodelSpec s) {
        var protocol = new LinkedHashMap<String, Object>();
        protocol.put("href", s.href());
        protocol.put("endpointProtocol", "HTTP");
        protocol.put("endpointProtocolVersion", List.of("1.1"));
        protocol.put("subprotocol", SUBPROTOCOL_DSP);
        protocol.put(
                "subprotocolBody",
                SubprotocolBody.format(DigitalTwinIds.submissionAssetId(customerBpnl), s.dspEndpoint()));
        protocol.put("subprotocolBodyEncoding", "plain");
        protocol.put("securityAttributes", List.of(Map.of("type", "NONE", "key", "NONE", "value", "NONE")));
        var descriptor = new LinkedHashMap<String, Object>();
        descriptor.put("id", DigitalTwinIds.submodelId(exchangeId, s.revision()));
        descriptor.put("idShort", "PpapSubmission-" + s.revision());
        descriptor.put("semanticId", reference(SUBMISSION_SEMANTIC_ID));
        descriptor.put("endpoints", List.of(Map.of("interface", SUBMODEL_INTERFACE, "protocolInformation", protocol)));
        return descriptor;
    }

    private static void addAssetId(List<Map<String, Object>> target, String name, String value, String subject) {
        if (value == null || value.isBlank()) {
            return;
        }
        var assetId = new LinkedHashMap<String, Object>();
        assetId.put("name", name);
        assetId.put("value", value);
        assetId.put("externalSubjectId", reference(subject));
        target.add(assetId);
    }

    private static Map<String, Object> reference(String value) {
        return Map.of("type", "ExternalReference", "keys", List.of(Map.of("type", "GlobalReference", "value", value)));
    }

    /** Reads a shell descriptor. */
    public static ShellView parseShell(byte[] json) {
        JsonNode node = read(json);
        var assetIds = new ArrayList<AssetIdView>();
        for (JsonNode a : node.path("specificAssetIds")) {
            assetIds.add(new AssetIdView(text(a, "name"), text(a, "value"), firstKey(a.path("externalSubjectId"))));
        }
        var submodels = new ArrayList<SubmodelView>();
        for (JsonNode s : node.path("submodelDescriptors")) {
            JsonNode protocol = s.path("endpoints").path(0).path("protocolInformation");
            submodels.add(new SubmodelView(
                    text(s, "id"),
                    firstKey(s.path("semanticId")),
                    text(protocol, "href"),
                    text(protocol, "subprotocol"),
                    text(protocol, "subprotocolBody")));
        }
        return new ShellView(text(node, "id"), text(node, "idShort"), text(node, "globalAssetId"), assetIds, submodels);
    }

    /** Shell ids of a lookup result ({@code GET /lookup/shells}: {@code {"result": [...]}}). */
    public static List<String> parseLookup(byte[] json) {
        JsonNode node = read(json);
        var ids = new ArrayList<String>();
        JsonNode result = node.isArray() ? node : node.path("result");
        for (JsonNode id : result) {
            if (id.isString()) {
                ids.add(id.asString());
            }
        }
        return ids;
    }

    /**
     * Query parameter value of {@code GET /lookup/shells?assetIds=…}: base64url-encoded {@code SpecificAssetId}
     * (AAS API 3.x, DTR 0.13).
     */
    public static String lookupAssetId(String name, String value) {
        var assetId = new LinkedHashMap<String, Object>();
        assetId.put("name", name);
        assetId.put("value", value);
        return BASE64URL.encodeToString(JSON.writeValueAsBytes(assetId));
    }

    /** Identifier as path segment ({@code /shell-descriptors/{aasIdentifier}}): base64url without padding. */
    public static String encodeId(String id) {
        return BASE64URL.encodeToString(id.getBytes(StandardCharsets.UTF_8));
    }

    private static JsonNode read(byte[] json) {
        try {
            JsonNode node = JSON.readTree(json);
            if (node == null) {
                throw new IllegalArgumentException("empty AAS document");
            }
            return node;
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("AAS document is not valid JSON", ex);
        }
    }

    private static String firstKey(JsonNode reference) {
        JsonNode key = reference.path("keys").path(0);
        return key.isMissingNode() ? null : text(key, "value");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }
}
