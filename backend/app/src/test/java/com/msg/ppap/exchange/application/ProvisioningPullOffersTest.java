package com.msg.ppap.exchange.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.msg.ppap.exchange.application.port.out.AssetSpec;
import com.msg.ppap.exchange.application.port.out.CallbackSecretStore;
import com.msg.ppap.exchange.application.port.out.DataspaceConnector;
import com.msg.ppap.exchange.application.port.out.PolicySpec;
import com.msg.ppap.exchange.config.DtrOfferProperties;
import com.msg.ppap.exchange.config.EdcProperties;
import com.msg.ppap.exchange.config.ExchangeProperties;
import com.msg.ppap.masterdata.api.MasterdataQueries;
import com.msg.ppap.masterdata.api.MasterdataQueries.PartnerInfo;
import com.msg.ppap.masterdata.api.MasterdataQueries.RelationInfo;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Phase 5 provisioning: DTR asset with OAuth2, per-partner document/submission offers, removal of stale offers. */
class ProvisioningPullOffersTest {

    static final String OEM = "BPNL00000000OEM1";
    static final String TIER2 = "BPNL000000TIER02";

    DataspaceConnector connector = mock(DataspaceConnector.class);
    CallbackSecretStore secrets = mock(CallbackSecretStore.class);
    MasterdataQueries masterdata = mock(MasterdataQueries.class);
    ExchangeContext context = mock(ExchangeContext.class);

    ProvisioningService service(DtrOfferProperties dtr) {
        var edc = new EdcProperties(
                URI.create("http://cp/management"),
                "k",
                null,
                null,
                "http://tier1-app:8080",
                "secret",
                "X-Ppap-Edc-Secret",
                "ppap-edc-callback-secret",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                Duration.ofMillis(10),
                new EdcProperties.Policy("cx.quality.base:1", "DataExchangeGovernance:1.0", "ppap-partners"),
                new EdcProperties.Vault("http://vault", "t", "secret"));
        var exchange = new ExchangeProperties(
                Duration.ofSeconds(2),
                3,
                Duration.ofSeconds(1),
                Duration.ofMinutes(1),
                Duration.ofMinutes(1),
                10,
                Duration.ofSeconds(15),
                Duration.ofMinutes(10),
                Duration.ofSeconds(25));
        when(context.now()).thenReturn(java.time.Instant.parse("2026-11-19T10:00:00Z"));
        when(secrets.available()).thenReturn(true);
        when(masterdata.exchangePartners())
                .thenReturn(List.of(
                        new PartnerInfo(OEM, "OEM", Set.of("CUSTOMER"), true, true, "dsp", "did"),
                        new PartnerInfo(TIER2, "T2", Set.of("SUPPLIER"), true, true, "dsp", "did")));
        when(masterdata.findRelation(OEM, "CUSTOMER"))
                .thenReturn(Optional.of(new RelationInfo(UUID.randomUUID(), OEM, "OEM", "CUSTOMER", true, "PULL")));
        when(masterdata.findRelation(TIER2, "CUSTOMER")).thenReturn(Optional.empty());
        when(connector.ensureBusinessPartnerGroup(anyString(), any())).thenReturn(Set.of(OEM, TIER2));
        when(connector.listContractDefinitionIds())
                .thenReturn(
                        List.of("ppap-documents-BPNL0000000GONE-cd", "ppap-documents-" + OEM + "-cd", "foreign-cd"));
        return new ProvisioningService(connector, secrets, masterdata, edc, exchange, context, dtr);
    }

    @Test
    void offersDtrDocumentsAndSubmissionsPerPartner() {
        var state = service(new DtrOfferProperties(
                        "http://tier1-dtr:4243/api/v3/",
                        "http://keycloak:8080/realms/tier1/protocol/openid-connect/token",
                        "ppap-edc-dtr",
                        "edc-secret",
                        "ppap-dtr-edc-client-secret"))
                .reconcile();
        assertThat(state.ok()).isTrue();
        assertThat(state.assets())
                .contains("ppap-dtr", "ppap-documents-" + OEM, "ppap-documents-" + TIER2, "ppap-submission-" + OEM)
                .doesNotContain("ppap-submission-" + TIER2);
        var assets = ArgumentCaptor.forClass(AssetSpec.class);
        verify(connector, org.mockito.Mockito.atLeastOnce()).ensureAsset(assets.capture());
        AssetSpec dtr = assets.getAllValues().stream()
                .filter(a -> a.id().equals("ppap-dtr"))
                .findFirst()
                .orElseThrow();
        assertThat(dtr.type()).isEqualTo("cx-taxo:DigitalTwinRegistry");
        assertThat(dtr.dataAddress())
                .containsEntry("baseUrl", "http://tier1-dtr:4243/api/v3")
                .containsEntry("proxyPath", "true")
                .containsEntry("proxyQueryParams", "true")
                .containsEntry("oauth2:clientId", "ppap-edc-dtr")
                .containsEntry("oauth2:clientSecretKey", "ppap-dtr-edc-client-secret")
                .doesNotContainValue("edc-secret");
        verify(secrets).store("ppap-dtr-edc-client-secret", "edc-secret");
        AssetSpec documents = assets.getAllValues().stream()
                .filter(a -> a.id().equals("ppap-documents-" + OEM))
                .findFirst()
                .orElseThrow();
        assertThat(documents.dataAddress())
                .containsEntry("baseUrl", "http://tier1-app:8080/internal/edc/documents")
                .containsEntry("proxyPath", "true")
                .containsEntry("secretName", "ppap-edc-callback-secret");
        var policies = ArgumentCaptor.forClass(PolicySpec.class);
        verify(connector, org.mockito.Mockito.atLeastOnce()).ensurePolicy(policies.capture());
        assertThat(policies.getAllValues()).anySatisfy(p -> {
            assertThat(p.id()).startsWith("ppap-access-bpn-" + OEM + "-");
            assertThat(p.constraints().getFirst().leftOperand()).isEqualTo("BusinessPartnerNumber");
        });
        verify(connector).removeOffer("ppap-documents-BPNL0000000GONE-cd", "ppap-documents-BPNL0000000GONE");
        verify(connector, never()).removeOffer(eq("foreign-cd"), anyString());
    }

    @Test
    void withoutDtrNoPullOffersButDocuments() {
        var state = service(new DtrOfferProperties(null, null, "ppap-edc-dtr", null, "x"))
                .reconcile();
        assertThat(state.assets())
                .contains("ppap-documents-" + OEM)
                .doesNotContain("ppap-dtr", "ppap-submission-" + OEM);
    }
}
