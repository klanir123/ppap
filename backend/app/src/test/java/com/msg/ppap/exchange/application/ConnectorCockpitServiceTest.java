package com.msg.ppap.exchange.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.msg.ppap.exchange.application.port.out.CatalogDataset;
import com.msg.ppap.exchange.application.port.out.ComponentStatusProbe;
import com.msg.ppap.exchange.application.port.out.ConnectorHealth;
import com.msg.ppap.exchange.application.port.out.DataspaceConnector;
import com.msg.ppap.exchange.application.port.out.DataspaceException;
import com.msg.ppap.exchange.application.port.out.ExchangeMessageStore;
import com.msg.ppap.exchange.application.port.out.ExchangeMessageStore.PartnerFigures;
import com.msg.ppap.exchange.config.DtrOfferProperties;
import com.msg.ppap.exchange.config.EdcProperties;
import com.msg.ppap.exchange.domain.Direction;
import com.msg.ppap.exchange.domain.ExchangeStep;
import com.msg.ppap.exchange.domain.ExchangeStepRecorded;
import com.msg.ppap.exchange.domain.ProvisioningState;
import com.msg.ppap.exchange.domain.StepName;
import com.msg.ppap.exchange.domain.StepStatus;
import com.msg.ppap.masterdata.api.MasterdataQueries;
import com.msg.ppap.masterdata.api.MasterdataQueries.PartnerInfo;
import com.msg.ppap.platform.domain.PpapException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Connector cockpit read side, live log text and exchange health (phase 6, UC-10/UC-11). */
class ConnectorCockpitServiceTest {

    static final String TIER1 = "BPNL000000TIER01";

    final DataspaceConnector connector = mock(DataspaceConnector.class);
    final ProvisioningService provisioning = mock(ProvisioningService.class);
    final ExchangeContext context = mock(ExchangeContext.class);
    final ExchangeMessageStore store = mock(ExchangeMessageStore.class);
    final ComponentStatusProbe components = mock(ComponentStatusProbe.class);
    final MasterdataQueries masterdata = mock(MasterdataQueries.class);
    final EdcProperties edc = new EdcProperties(
            URI.create("http://cp/management"),
            "k",
            URI.create("http://cp:8084/api/v1/dsp/2025-1"),
            "did:web:wallet-stub:BPNL00000000OEM1",
            null,
            null,
            "X",
            "s",
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            Duration.ofMillis(10),
            new EdcProperties.Policy("cx.quality.base:1", "DataExchangeGovernance:1.0", "ppap-partners"),
            new EdcProperties.Vault(null, null, "secret"));
    final ConnectorCockpitService cockpit = new ConnectorCockpitService(
            connector,
            provisioning,
            context,
            edc,
            new DtrOfferProperties("http://dtr/api/v3", null, "c", null, "n"),
            components,
            masterdata,
            new PartnerDirectory(masterdata));

    {
        when(context.store()).thenReturn(store);
        when(context.now()).thenReturn(Instant.parse("2026-11-20T10:00:00Z"));
        when(masterdata.findPartner(TIER1))
                .thenReturn(Optional.of(new PartnerInfo(
                        TIER1,
                        "Demo-Tier1 GmbH",
                        Set.of("SUPPLIER"),
                        true,
                        true,
                        "http://t1/dsp",
                        "did:web:x:" + TIER1)));
    }

    @Test
    void overviewCombinesConnectorProvisioningAndCounts() {
        when(connector.health()).thenReturn(ConnectorHealth.up("http://cp", Duration.ofMillis(12)));
        when(store.countByDirectionAndStatus())
                .thenReturn(Map.of("OUTBOUND/PENDING", 2L, "OUTBOUND/FAILED", 1L, "INBOUND/REJECTED", 3L));
        when(provisioning.state())
                .thenReturn(new ProvisioningState(List.of("a"), List.of(TIER1), true, Instant.EPOCH, null));
        when(connector.listAssets(anyInt())).thenReturn(List.of(Map.of("@id", "a"), Map.of("@id", "b")));
        when(connector.listPolicies(anyInt())).thenThrow(new IllegalStateException("down"));
        when(components.status("dtr")).thenReturn("UP");
        var overview = cockpit.overview();
        assertThat(overview.edc().status()).isEqualTo("UP");
        assertThat(overview.edc().dspEndpoint()).isEqualTo("http://cp:8084/api/v1/dsp/2025-1");
        assertThat(overview.dtr().status()).isEqualTo("UP");
        assertThat(overview.outboxPending()).isEqualTo(2);
        assertThat(overview.outboxFailed()).isEqualTo(1);
        assertThat(overview.inboxRejected()).isEqualTo(3);
        assertThat(overview.counts().assets()).isEqualTo(2);
        assertThat(overview.counts().policies()).isEqualTo(-1);

        when(connector.health()).thenReturn(ConnectorHealth.down("http://cp", Duration.ofMillis(3), "refused"));
        assertThat(cockpit.overview().counts()).isNull();
    }

    @Test
    void processesResolveThePartnerFromTheDid() {
        when(connector.listNegotiations(eq("FINALIZED"), eq(25), eq(25)))
                .thenReturn(List.of(Map.of(
                        "@id",
                        "n1",
                        "type",
                        "CONSUMER",
                        "state",
                        "FINALIZED",
                        "counterPartyId",
                        "did:web:wallet-stub:" + TIER1,
                        "assetId",
                        "ppap-dtr",
                        "contractAgreementId",
                        "ag",
                        "createdAt",
                        1795097922570L)));
        var process = cockpit.negotiations("FINALIZED", 1, 25).getFirst();
        assertThat(process.partnerBpnl()).isEqualTo(TIER1);
        assertThat(process.partnerName()).isEqualTo("Demo-Tier1 GmbH");
        assertThat(process.agreementId()).isEqualTo("ag");
        assertThat(process.createdAt()).isEqualTo(Instant.ofEpochMilli(1795097922570L));
        when(connector.listTransfers(any(), anyInt(), anyInt()))
                .thenReturn(List.of(
                        Map.of("@id", "t1", "counterPartyId", "did:web:unknown", "stateTimestamp", "1795097931559")));
        var transfer = cockpit.transfers(" ", 0, 25).getFirst();
        assertThat(transfer.partnerBpnl()).isNull();
        assertThat(transfer.updatedAt()).isEqualTo(Instant.ofEpochMilli(1795097931559L));
        assertThat(ConnectorCockpitService.bpnlOf(null)).isNull();
        when(connector.listContractDefinitions(anyInt()))
                .thenReturn(List.of(Map.of("@id", "ppap-dtr-cd", "@context", "x"), Map.of("@id", "smoke-cd")));
        assertThat(cockpit.contractDefinitions())
                .extracting(ConnectorCockpitService.EdcObject::managed)
                .containsExactly(true, false);
        assertThat(cockpit.contractDefinitions().getFirst().content()).doesNotContainKey("@context");
    }

    @Test
    void catalogIsRequestedLiveAndErrorsCarryTheEdcMessage() {
        when(connector.requestFullCatalog(any()))
                .thenReturn(List.of(new CatalogDataset("ppap-dtr", "x", Map.of(), List.of(Map.of("@id", "o")))));
        var catalog = cockpit.catalog(TIER1);
        assertThat(catalog.partnerName()).isEqualTo("Demo-Tier1 GmbH");
        assertThat(catalog.datasets()).hasSize(1);
        when(connector.requestFullCatalog(any())).thenThrow(new DataspaceException(StepName.CATALOG, "HTTP 502 boom"));
        assertThatThrownBy(() -> cockpit.catalog(TIER1))
                .isInstanceOf(PpapException.class)
                .hasMessageContaining("boom")
                .extracting(ex -> ((PpapException) ex).code())
                .isEqualTo(ConnectorCockpitService.CATALOG_REQUEST_FAILED);
        assertThatThrownBy(() -> cockpit.catalog("BPNL0000000NONE"))
                .extracting(ex -> ((PpapException) ex).code())
                .isEqualTo("PARTNER_NOT_EXCHANGE_READY");
    }

    @Test
    void liveLogTextAndLevelAreDerivedFromTheStep() {
        var step = new ExchangeStep(
                1, StepName.CATALOG, StepStatus.FAILED, Instant.EPOCH, 12, Map.of("assetId", "a1", "error", "x"));
        var recorded = new ExchangeStepRecorded(
                UUID.randomUUID(), UUID.randomUUID(), "PPAP_SUBMISSION", "c1", null, Direction.OUTBOUND, TIER1, step);
        assertThat(LiveEventService.level(StepStatus.FAILED)).isEqualTo("ERROR");
        assertThat(LiveEventService.level(StepStatus.OK)).isEqualTo("INFO");
        assertThat(LiveEventService.level(StepStatus.SKIPPED)).isEqualTo("DEBUG");
        assertThat(LiveEventService.text(recorded))
                .isEqualTo("→ " + TIER1 + " PPAP_SUBMISSION: CATALOG FAILED (12 ms), assetId=a1, error=x");
        var service = new LiveEventService(mock(com.msg.ppap.identity.TenantContext.class));
        service.connectorLog(recorded);
        assertThat(service.subscribers()).isZero();
    }

    @Test
    void exchangeHealthOfPartners() {
        assertThat(PartnerExchangeHealthService.status(new PartnerFigures(1, 0, 0, null)))
                .isEqualTo("FAILING");
        assertThat(PartnerExchangeHealthService.status(new PartnerFigures(0, 1, 0, null)))
                .isEqualTo("FAILING");
        assertThat(PartnerExchangeHealthService.status(new PartnerFigures(0, 0, 2, null)))
                .isEqualTo("DEGRADED");
        assertThat(PartnerExchangeHealthService.status(new PartnerFigures(0, 0, 0, Instant.EPOCH)))
                .isEqualTo("OK");
        when(store.partnerFigures(any(), any(), any()))
                .thenReturn(Map.of(TIER1, new PartnerFigures(0, 0, 0, Instant.EPOCH)));
        assertThat(new PartnerExchangeHealthService(context).health(List.of(TIER1)))
                .containsEntry(TIER1, new com.msg.ppap.approval.PartnerExchangeHealth.Health("OK", Instant.EPOCH));
    }
}
