package com.msg.ppap.approval.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class SecurityActorContextTest {

    @Test
    void parsesAuthTimeClaim() {
        assertThat(SecurityActorContext.authTime(1_700_000_000L)).isEqualTo(Instant.ofEpochSecond(1_700_000_000L));
        assertThat(SecurityActorContext.authTime("1700000000")).isEqualTo(Instant.ofEpochSecond(1_700_000_000L));
        assertThat(SecurityActorContext.authTime(Instant.EPOCH)).isEqualTo(Instant.EPOCH);
        assertThat(SecurityActorContext.authTime("yesterday")).isNull();
        assertThat(SecurityActorContext.authTime(null)).isNull();
    }
}
