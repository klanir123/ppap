package com.msg.ppap.semantics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.msg.ppap.semantics.api.AasDescriptors;
import com.msg.ppap.semantics.api.AasDescriptors.ShellView;
import com.msg.ppap.semantics.api.AasDescriptors.SubmodelSpec;
import com.msg.ppap.semantics.api.AasDescriptors.SubprotocolBody;
import com.msg.ppap.semantics.api.AasDescriptors.TwinSpec;
import com.msg.ppap.semantics.api.DigitalTwinIds;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Digital twin descriptor mapping and parsing (docs/api/pull-api.md, „Digital Twin“) against recorded DTR 0.13 JSON. */
class AasDescriptorsTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final UUID EXCHANGE = UUID.fromString("0963e641-70f5-4542-83e8-de75ebcfce9f");
    static final String OEM = "BPNL00000000OEM1";
    static final String TIER1 = "BPNL000000TIER01";

    @Test
    void idsAreDeterministicPerExchangeIdAndRevision() {
        assertThat(DigitalTwinIds.shellId(EXCHANGE)).isEqualTo("urn:uuid:" + EXCHANGE);
        assertThat(DigitalTwinIds.submodelId(EXCHANGE, 1))
                .isEqualTo(DigitalTwinIds.submodelId(EXCHANGE, 1))
                .isNotEqualTo(DigitalTwinIds.submodelId(EXCHANGE, 2))
                .startsWith("urn:uuid:");
        assertThat(DigitalTwinIds.idShort("PPF-2026-00018")).isEqualTo("ppap-PPF-2026-00018");
        assertThat(DigitalTwinIds.submissionAssetId(OEM)).isEqualTo("ppap-submission-" + OEM);
    }

    @Test
    void shellDescriptorReleasesTheTwinOnlyToTheCustomer() {
        var spec = new TwinSpec(
                EXCHANGE,
                "PPF-2026-00018",
                OEM,
                TIER1,
                "OEM-4711",
                "T1-310000-01",
                List.of(
                        new SubmodelSpec(
                                1,
                                "http://tier1-edc-dp:8081/api/public/" + EXCHANGE + "/1",
                                "http://tier1-edc-cp:8084/api/v1/dsp/2025-1"),
                        new SubmodelSpec(
                                2,
                                "http://tier1-edc-dp:8081/api/public/" + EXCHANGE + "/2",
                                "http://tier1-edc-cp:8084/api/v1/dsp/2025-1")));
        JsonNode shell = JSON.readTree(AasDescriptors.shellDescriptor(spec));
        assertThat(shell.path("id").asString()).isEqualTo("urn:uuid:" + EXCHANGE);
        assertThat(shell.path("globalAssetId").asString()).isEqualTo("urn:uuid:" + EXCHANGE);
        assertThat(shell.path("idShort").asString()).isEqualTo("ppap-PPF-2026-00018");
        assertThat(shell.path("specificAssetIds")).hasSize(4);
        for (JsonNode assetId : shell.path("specificAssetIds")) {
            assertThat(assetId.path("externalSubjectId")
                            .path("keys")
                            .path(0)
                            .path("value")
                            .asString())
                    .isEqualTo(OEM);
        }
        JsonNode submodel = shell.path("submodelDescriptors").path(1);
        assertThat(submodel.path("id").asString()).isEqualTo(DigitalTwinIds.submodelId(EXCHANGE, 2));
        assertThat(submodel.path("semanticId")
                        .path("keys")
                        .path(0)
                        .path("value")
                        .asString())
                .isEqualTo("urn:samm:com.msg.ppap:0.1.0#PpapSubmission");
        JsonNode protocol = submodel.path("endpoints").path(0).path("protocolInformation");
        assertThat(submodel.path("endpoints").path(0).path("interface").asString())
                .isEqualTo("SUBMODEL-3.0");
        assertThat(protocol.path("href").asString()).endsWith("/" + EXCHANGE + "/2");
        assertThat(protocol.path("subprotocol").asString()).isEqualTo("DSP");
        assertThat(protocol.path("subprotocolBody").asString())
                .isEqualTo("id=ppap-submission-" + OEM + ";dspEndpoint=http://tier1-edc-cp:8084/api/v1/dsp/2025-1");

        // round trip through the reader
        ShellView view = AasDescriptors.parseShell(AasDescriptors.shellDescriptor(spec));
        assertThat(view.submodel(DigitalTwinIds.submodelId(EXCHANGE, 1))
                        .orElseThrow()
                        .dsp())
                .contains(new SubprotocolBody("ppap-submission-" + OEM, "http://tier1-edc-cp:8084/api/v1/dsp/2025-1"));
    }

    @Test
    void unmappedPartsAreLeftOut() {
        var spec = new TwinSpec(EXCHANGE, "PPF-1", OEM, TIER1, "OEM-1", null, List.of());
        JsonNode shell = JSON.readTree(AasDescriptors.shellDescriptor(spec));
        assertThat(shell.path("specificAssetIds").findValuesAsString("name"))
                .containsExactly("customerPartId", "manufacturerId", "ppapExchangeId");
        assertThat(shell.path("submodelDescriptors")).isEmpty();
    }

    @Test
    void readsRecordedDtrShellDescriptorAndLookup() throws IOException {
        ShellView shell = AasDescriptors.parseShell(fixture("shell-descriptor.json"));
        assertThat(shell.id()).isEqualTo("urn:uuid:" + EXCHANGE);
        assertThat(shell.specificAssetIds()).anySatisfy(a -> {
            assertThat(a.name()).isEqualTo("ppapExchangeId");
            assertThat(a.externalSubjectId()).isEqualTo(OEM);
        });
        var submodel = shell.submodel(DigitalTwinIds.submodelId(EXCHANGE, 1)).orElseThrow();
        assertThat(submodel.semanticId()).isEqualTo(AasDescriptors.SUBMISSION_SEMANTIC_ID);
        assertThat(submodel.dsp().orElseThrow().assetId()).isEqualTo("ppap-submission-" + OEM);
        assertThat(AasDescriptors.parseLookup(fixture("lookup-response.json"))).containsExactly("urn:uuid:" + EXCHANGE);
        assertThat(AasDescriptors.parseLookup("[\"a\",\"b\"]".getBytes(StandardCharsets.UTF_8)))
                .containsExactly("a", "b");
        assertThatThrownBy(() -> AasDescriptors.parseShell("{".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesSubprotocolBodies() {
        assertThat(SubprotocolBody.parse("id=a;dspEndpoint=http://x/dsp"))
                .contains(new SubprotocolBody("a", "http://x/dsp"));
        assertThat(SubprotocolBody.parse(" dspEndpoint = http://x/dsp ; id = a ; foo=bar"))
                .contains(new SubprotocolBody("a", "http://x/dsp"));
        assertThat(SubprotocolBody.parse("id=a;dspEndpoint=http://x/dsp?x=1=2"))
                .map(SubprotocolBody::dspEndpoint)
                .contains("http://x/dsp?x=1=2");
        assertThat(SubprotocolBody.parse("id=a")).isEmpty();
        assertThat(SubprotocolBody.parse("dspEndpoint=http://x")).isEmpty();
        assertThat(SubprotocolBody.parse("")).isEmpty();
        assertThat(SubprotocolBody.parse(null)).isEmpty();
        assertThat(new AasDescriptors.SubmodelView("s", "x", "h", "HTTP", "id=a;dspEndpoint=b").dsp())
                .isEmpty();
    }

    @Test
    void encodesLookupKeysAndIdentifiersBase64Url() {
        String encoded = AasDescriptors.lookupAssetId("ppapExchangeId", EXCHANGE.toString());
        JsonNode decoded = JSON.readTree(Base64.getUrlDecoder().decode(encoded));
        assertThat(decoded.path("name").asString()).isEqualTo("ppapExchangeId");
        assertThat(decoded.path("value").asString()).isEqualTo(EXCHANGE.toString());
        assertThat(encoded).doesNotContain("=", "+", "/");
        assertThat(new String(
                        Base64.getUrlDecoder().decode(AasDescriptors.encodeId("urn:uuid:x")), StandardCharsets.UTF_8))
                .isEqualTo("urn:uuid:x");
    }

    static byte[] fixture(String name) throws IOException {
        try (InputStream in = AasDescriptorsTest.class.getResourceAsStream("/dtr/" + name)) {
            return in.readAllBytes();
        }
    }
}
