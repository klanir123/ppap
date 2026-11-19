package com.msg.ppap.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.msg.ppap.semantics.api.CanonicalJson;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditRecordTest {

    @Test
    void canonicalJsonSortsKeysAndEscapes() {
        var map = new LinkedHashMap<String, Object>();
        map.put("b", 1);
        map.put("a", List.of(true, "x\"y\n", 2L));
        map.put("€", null);
        map.put("c", Map.of("z", new BigDecimal("1.0"), "y", BigInteger.TEN, "x", 1.5, "w", "\u0001"));
        assertThat(CanonicalJson.write(map))
                .isEqualTo(
                        "{\"a\":[true,\"x\\\"y\\n\",2],\"b\":1,\"c\":{\"w\":\"\\u0001\",\"x\":1.5,\"y\":10,\"z\":1},\"€\":null}");
        assertThat(CanonicalJson.write(new Object[] {(short) 1, (byte) 2, 3.0f}))
                .isEqualTo("[1,2,3]");
        assertThat(CanonicalJson.write(UUID.fromString("00000000-0000-0000-0000-000000000001")))
                .isEqualTo("\"00000000-0000-0000-0000-000000000001\"");
        assertThatIllegalArgumentException().isThrownBy(() -> CanonicalJson.write(Double.NaN));
    }

    @Test
    void hashChainLinksRecords() {
        Instant now = Instant.parse("2026-11-18T10:15:30.123456789Z");
        var first = AuditRecord.append(
                UUID.randomUUID(), "BPNL000000000OEM", now, "mia", "CREATED", "PART", "1", Map.of("v", 1), null);
        assertThat(first.occurredAt()).isEqualTo(Instant.parse("2026-11-18T10:15:30.123456Z"));
        assertThat(first.hash()).hasSize(64);
        assertThat(first.hashValid()).isTrue();

        var second = AuditRecord.append(
                UUID.randomUUID(), "BPNL000000000OEM", now, "mia", "UPDATED", "PART", "1", null, first.hash());
        assertThat(second.prevHash()).isEqualTo(first.hash());
        assertThat(second.hash()).isNotEqualTo(first.hash());
        assertThat(second.details()).isEmpty();

        var tampered = new AuditRecord(
                second.id(),
                second.tenantId(),
                second.occurredAt(),
                "eve",
                second.action(),
                second.objectType(),
                second.objectId(),
                second.details(),
                second.prevHash(),
                second.hash());
        assertThat(tampered.hashValid()).isFalse();
        assertThat(AuditRecord.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
