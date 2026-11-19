package com.msg.ppap.exchange.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.msg.ppap.exchange.application.port.out.CatalogOffer;
import com.msg.ppap.exchange.application.port.out.DataspaceConnector;
import com.msg.ppap.exchange.application.port.out.DataspaceException;
import com.msg.ppap.exchange.application.port.out.EndpointDataReference;
import com.msg.ppap.exchange.application.port.out.PartnerEndpoint;
import com.msg.ppap.exchange.application.port.out.StepListener;
import com.msg.ppap.exchange.config.PullProperties;
import com.msg.ppap.exchange.domain.StepName;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** DTR offer cache: a partner that offers no DTR yet (still provisioning) is asked again soon or on a job retry. */
class DtrAccessTest {

    static final PartnerEndpoint TIER1 = new PartnerEndpoint("BPNL000000TIER01", "did:web:t1", "http://t1/dsp");
    static final CatalogOffer OFFER = new CatalogOffer("ppap-dtr", "o-1", Map.of());
    static final EndpointDataReference EDR =
            new EndpointDataReference("tp", "ag", URI.create("http://dp/api/public"), "tok", false);

    DataspaceConnector connector = mock(DataspaceConnector.class);
    ExchangeContext context = mock(ExchangeContext.class);
    AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-19T10:00:00Z"));
    DtrAccess dtr;

    @BeforeEach
    void setUp() {
        when(context.now()).thenAnswer(i -> now.get());
        dtr = new DtrAccess(
                connector,
                new PullProperties(Duration.ofSeconds(60), true, Duration.ofMinutes(10), 3, Duration.ofSeconds(60)),
                context);
    }

    @Test
    void missingOfferExpiresQuicklyAndIsBypassedOnRetry() {
        when(connector.requestCatalogByType(any(), anyString())).thenReturn(List.of(), List.of(OFFER));
        assertThat(dtr.offer(TIER1, StepListener.NONE)).isEmpty();
        assertThat(dtr.offer(TIER1, StepListener.NONE)).isEmpty(); // cached negative
        verify(connector, times(1)).requestCatalogByType(any(), anyString());
        now.set(now.get().plusSeconds(61));
        assertThat(dtr.offer(TIER1, StepListener.NONE)).contains(OFFER);
        verify(connector, times(2)).requestCatalogByType(any(), anyString());
    }

    @Test
    void failingJobAsksTheCatalogAgain() {
        when(connector.requestCatalogByType(any(), anyString())).thenReturn(List.of(), List.of(OFFER));
        when(connector.obtainEndpoint(any(), any(CatalogOffer.class), any())).thenReturn(EDR);
        assertThatThrownBy(() -> dtr.endpoint(TIER1, StepListener.NONE)).isInstanceOf(DataspaceException.class);
        // the negative entry was dropped: the next attempt (retry) finds the offer without waiting for the TTL
        assertThat(dtr.endpoint(TIER1, StepListener.NONE, true)).isEqualTo(EDR);
    }

    @Test
    void failedNegotiationForgetsTheCachedOffer() {
        when(connector.requestCatalogByType(any(), anyString())).thenReturn(List.of(OFFER));
        when(connector.obtainEndpoint(any(), any(CatalogOffer.class), any()))
                .thenThrow(new DataspaceException(StepName.NEGOTIATION, "terminated"))
                .thenReturn(EDR);
        assertThatThrownBy(() -> dtr.endpoint(TIER1, StepListener.NONE)).isInstanceOf(DataspaceException.class);
        assertThat(dtr.endpoint(TIER1, StepListener.NONE)).isEqualTo(EDR);
        verify(connector, times(2)).requestCatalogByType(any(), anyString());
    }
}
