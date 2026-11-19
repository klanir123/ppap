package com.msg.ppap.exchange.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.msg.ppap.exchange.application.port.out.DataPlaneResponse;
import com.msg.ppap.exchange.application.port.out.DataspaceConnector;
import com.msg.ppap.exchange.application.port.out.DataspaceException;
import com.msg.ppap.exchange.application.port.out.EndpointDataReference;
import com.msg.ppap.exchange.application.port.out.ExchangeMessageStore;
import com.msg.ppap.exchange.application.port.out.ExchangeMessageStore.StoredMessage;
import com.msg.ppap.exchange.application.port.out.PartnerEndpoint;
import com.msg.ppap.exchange.config.ExchangeProperties;
import com.msg.ppap.exchange.domain.Direction;
import com.msg.ppap.exchange.domain.ExchangeRecord;
import com.msg.ppap.exchange.domain.MessageStatus;
import com.msg.ppap.exchange.domain.StepName;
import com.msg.ppap.identity.TenantContext;
import com.msg.ppap.masterdata.api.MasterdataQueries;
import com.msg.ppap.masterdata.api.MasterdataQueries.PartnerInfo;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Delivery worker: success, token rejection (EDR invalidated, retry), terminal rejection, exhausted attempts. */
class OutboundDeliveryServiceTest {

    static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");
    static final String PARTNER = "BPNL000000TIER01";

    ExchangeMessageStore store = mock(ExchangeMessageStore.class);
    DataspaceConnector connector = mock(DataspaceConnector.class);
    MasterdataQueries masterdata = mock(MasterdataQueries.class);
    OutboundDeliveryService service;
    EndpointDataReference edr = new EndpointDataReference("tp", "ag", URI.create("http://dp/api/public"), "tok", true);

    @BeforeEach
    void setUp() {
        var tenant = mock(TenantContext.class);
        when(tenant.tenantId()).thenReturn("BPNL00000000OEM1");
        var context =
                new ExchangeContext(store, new LiveEventService(tenant), tenant, Clock.fixed(NOW, ZoneOffset.UTC));
        var properties = new ExchangeProperties(
                Duration.ofSeconds(2),
                3,
                Duration.ofSeconds(1),
                Duration.ofMinutes(15),
                Duration.ofMinutes(2),
                10,
                Duration.ofSeconds(15),
                Duration.ofMinutes(10),
                Duration.ofSeconds(25));
        service = new OutboundDeliveryService(
                context, connector, new PartnerDirectory(masterdata), properties, Runnable::run, java.util.List.of());
        when(masterdata.findPartner(PARTNER))
                .thenReturn(Optional.of(new PartnerInfo(
                        PARTNER,
                        "Tier1",
                        Set.of("SUPPLIER"),
                        true,
                        true,
                        "http://tier1-edc-cp:8084/api/v1/dsp/2025-1",
                        "did:web:wallet-stub:" + PARTNER)));
        when(connector.obtainEndpoint(any(), anyString(), any())).thenReturn(edr);
    }

    StoredMessage claimed(int attempts) {
        var record = new ExchangeRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                Direction.OUTBOUND,
                "PPAP_REQUEST",
                PARTNER,
                "Tier1",
                UUID.randomUUID(),
                "PPF-1",
                UUID.randomUUID(),
                MessageStatus.IN_PROGRESS,
                attempts,
                null,
                "sha256:x",
                "corr",
                NOW,
                null,
                null,
                null,
                List.of());
        when(store.claimOutbound(anyInt(), any(), any()))
                .thenReturn(List.of(new StoredMessage(record, "{}", null)), List.of());
        return new StoredMessage(record, "{}", null);
    }

    @Test
    void deliversAndRecordsTheDeliveryStep() {
        StoredMessage m = claimed(1);
        when(connector.call(eq(edr), eq("POST"), any(), eq("application/json"), any()))
                .thenReturn(new DataPlaneResponse(200, "{}".getBytes()));
        assertThat(service.deliverDue()).isEqualTo(1);
        verify(store)
                .updateStatus(
                        eq(m.record().id()),
                        eq(MessageStatus.DELIVERED),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(NOW),
                        any(),
                        isNull());
        verify(store).addStep(eq(m.record().id()), any());
    }

    @Test
    void rejectedTokenInvalidatesTheEdrAndRetriesWithBackoff() {
        StoredMessage m = claimed(2);
        when(connector.call(any(), any(), any(), any(), any())).thenReturn(new DataPlaneResponse(403, new byte[0]));
        service.deliverDue();
        verify(connector).invalidateEndpoint(any(PartnerEndpoint.class), eq(ExchangeAssets.NOTIFICATION_ASSET));
        verify(store)
                .updateStatus(
                        eq(m.record().id()),
                        eq(MessageStatus.PENDING),
                        eq(NOW.plusSeconds(2)),
                        eq("DELIVERY_UNAUTHORIZED"),
                        any(),
                        isNull(),
                        any(),
                        isNull());
    }

    @Test
    void schemaRejectionByThePartnerIsTerminal() {
        StoredMessage m = claimed(1);
        when(connector.call(any(), any(), any(), any(), any()))
                .thenReturn(new DataPlaneResponse(400, "bad".getBytes()));
        service.deliverDue();
        verify(store)
                .updateStatus(
                        eq(m.record().id()),
                        eq(MessageStatus.FAILED),
                        isNull(),
                        eq("DELIVERY_REJECTED"),
                        any(),
                        eq(NOW),
                        any(),
                        isNull());
    }

    @Test
    void givesUpAfterMaxAttempts() {
        StoredMessage m = claimed(3);
        when(connector.obtainEndpoint(any(), anyString(), any()))
                .thenThrow(new DataspaceException(StepName.CATALOG, "not offered"));
        service.deliverDue();
        verify(store)
                .updateStatus(
                        eq(m.record().id()),
                        eq(MessageStatus.FAILED),
                        isNull(),
                        eq("EDC_CATALOG"),
                        eq("not offered"),
                        eq(NOW),
                        any(),
                        isNull());
    }

    @Test
    void partnerWithoutConnectorDataIsRetried() {
        StoredMessage m = claimed(1);
        when(masterdata.findPartner(PARTNER)).thenReturn(Optional.empty());
        service.deliverDue();
        verify(connector, never()).call(any(), any(), any(), any(), any());
        verify(store)
                .updateStatus(
                        eq(m.record().id()),
                        eq(MessageStatus.PENDING),
                        eq(NOW.plusSeconds(1)),
                        eq("EDC_CATALOG"),
                        any(),
                        isNull(),
                        any(),
                        isNull());
    }
}
