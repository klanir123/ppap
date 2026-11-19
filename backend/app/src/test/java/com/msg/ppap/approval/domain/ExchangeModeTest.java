package com.msg.ppap.approval.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Mode selection of a relation (docs/api/pull-api.md, „Abläufe je Modus“). */
class ExchangeModeTest {

    @Test
    void relationValuesMapToModesWithDefault() {
        assertThat(ExchangeMode.of("PUSH")).isEqualTo(ExchangeMode.PUSH);
        assertThat(ExchangeMode.of("PULL")).isEqualTo(ExchangeMode.PULL);
        assertThat(ExchangeMode.of(null)).isEqualTo(ExchangeMode.PUSH_NOTIFY_PULL_PAYLOAD);
        assertThat(ExchangeMode.of(" ")).isEqualTo(ExchangeMode.PUSH_NOTIFY_PULL_PAYLOAD);
        assertThat(ExchangeMode.of("unknown")).isEqualTo(ExchangeMode.PUSH_NOTIFY_PULL_PAYLOAD);
    }

    @Test
    void onlyPullModesRegisterATwin() {
        assertThat(ExchangeMode.PUSH.registersTwin()).isFalse();
        assertThat(ExchangeMode.PULL.registersTwin()).isTrue();
        assertThat(ExchangeMode.PUSH_NOTIFY_PULL_PAYLOAD.registersTwin()).isTrue();
    }
}
