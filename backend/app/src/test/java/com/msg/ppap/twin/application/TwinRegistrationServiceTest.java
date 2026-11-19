package com.msg.ppap.twin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.msg.ppap.approval.application.port.in.PartnerSharingUseCase;
import com.msg.ppap.approval.application.port.in.PartnerSharingUseCase.TwinSource;
import com.msg.ppap.exchange.api.OutboxTaskHandler.OutboxTask;
import com.msg.ppap.exchange.api.OutboxTaskHandler.TaskFailure;
import com.msg.ppap.identity.TenantContext;
import com.msg.ppap.semantics.api.DigitalTwinIds;
import com.msg.ppap.twin.application.port.out.TwinRegistry;
import com.msg.ppap.twin.application.port.out.TwinRegistryException;
import com.msg.ppap.twin.application.port.out.TwinStore;
import com.msg.ppap.twin.config.TwinProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Twin registration as outbox task: descriptor per case, idempotent PUT, stored read-back, failure classification. */
class TwinRegistrationServiceTest {

    static final UUID EXCHANGE = UUID.randomUUID();
    static final UUID CASE = UUID.randomUUID();

    TwinRegistry registry = mock(TwinRegistry.class);
    TwinStore store = mock(TwinStore.class);
    PartnerSharingUseCase sharing = mock(PartnerSharingUseCase.class);
    TenantContext tenant = mock(TenantContext.class);
    TwinRegistrationService service;
    List<String> steps = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(tenant.tenantId()).thenReturn("BPNL000000TIER01");
        when(registry.configured()).thenReturn(true);
        service = new TwinRegistrationService(
                registry,
                store,
                sharing,
                tenant,
                new TwinProperties(
                        "http://dtr/api/v3",
                        "http://kc/token",
                        "ppap-backend-dtr",
                        "s",
                        "http://tier1-edc-dp:8081/api/public/",
                        "http://tier1-edc-cp:8084/api/v1/dsp/2025-1",
                        Duration.ofSeconds(1)),
                Clock.fixed(Instant.parse("2026-11-19T10:00:00Z"), ZoneOffset.UTC));
        when(sharing.twinSource(EXCHANGE))
                .thenReturn(Optional.of(
                        new TwinSource(CASE, "PPF-1", EXCHANGE, "BPNL00000000OEM1", "OEM-1", "T1-1", List.of(1, 2))));
    }

    OutboxTask task() {
        return new OutboxTask(UUID.randomUUID(), CASE, EXCHANGE, "BPNL00000000OEM1", 1);
    }

    @Test
    void registersTheShellWithOneSubmodelPerPulledRevision() {
        when(registry.findShell(anyString()))
                .thenReturn(Optional.of("{\"id\":\"x\"}".getBytes(StandardCharsets.UTF_8)));
        service.execute(task(), (step, ok, ms, details) -> steps.add(step + ":" + ok + ":" + details.get("target")));

        var descriptor = ArgumentCaptor.forClass(byte[].class);
        verify(registry).putShell(eq(DigitalTwinIds.shellId(EXCHANGE)), descriptor.capture());
        JsonNode shell = JsonMapper.builder().build().readTree(descriptor.getValue());
        assertThat(shell.path("submodelDescriptors")).hasSize(2);
        assertThat(shell.path("submodelDescriptors")
                        .path(1)
                        .path("endpoints")
                        .path(0)
                        .path("protocolInformation")
                        .path("href")
                        .asString())
                .isEqualTo("http://tier1-edc-dp:8081/api/public/" + EXCHANGE + "/2");
        verify(store).save(eq(EXCHANGE), eq(CASE), eq(DigitalTwinIds.shellId(EXCHANGE)), eq("{\"id\":\"x\"}"), any());
        assertThat(steps).containsExactly("TWIN_REGISTRATION:true:DTR");
        assertThat(service.taskType()).isEqualTo("TWIN_REGISTRATION");
    }

    @Test
    void failuresAreRecordedAndClassified() {
        doThrow(new TwinRegistryException("bad", 400, null)).when(registry).putShell(anyString(), any());
        assertThatThrownBy(() -> service.execute(task(), (step, ok, ms, d) -> steps.add(step + ":" + ok)))
                .isInstanceOfSatisfying(
                        TaskFailure.class, ex -> assertThat(ex.retryable()).isFalse());
        doThrow(new TwinRegistryException("down", null, null)).when(registry).putShell(anyString(), any());
        assertThatThrownBy(() -> service.execute(task(), (step, ok, ms, d) -> steps.add(step + ":" + ok)))
                .isInstanceOfSatisfying(
                        TaskFailure.class, ex -> assertThat(ex.retryable()).isTrue());
        when(sharing.twinSource(EXCHANGE)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.execute(task(), (step, ok, ms, d) -> steps.add(step + ":" + ok)))
                .isInstanceOfSatisfying(
                        TaskFailure.class, ex -> assertThat(ex.retryable()).isFalse());
        assertThat(steps).containsOnly("TWIN_REGISTRATION:false");
        when(store.find(EXCHANGE)).thenReturn(Optional.empty());
        assertThat(service.find(EXCHANGE)).isEmpty();
    }

    @Test
    void withoutRegistryTheTaskIsRetried() {
        when(registry.configured()).thenReturn(false);
        assertThatThrownBy(() -> service.execute(task(), (step, ok, ms, d) -> {}))
                .isInstanceOfSatisfying(
                        TaskFailure.class, ex -> assertThat(ex.retryable()).isTrue());
    }
}
