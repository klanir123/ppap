package com.msg.ppap.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BpnlTest {

    @ParameterizedTest
    @ValueSource(strings = {"BPNL00000003AYRE", "BPNL000000000OEM", "BPNLABCDEFGHIJKL"})
    void acceptsValidBpnl(String value) {
        assertThat(Bpnl.isValid(value)).isTrue();
        assertThat(new Bpnl(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "BPNL",
                "BPNL00000003AYR", // too short
                "BPNL00000003AYREX", // too long
                "bpnl00000003AYRE", // lower-case prefix
                "BPNL00000003ayre", // lower-case suffix
                "BPNS00000003AYRE", // site, not legal entity
                "BPNL00000003AY-E",
                " BPNL00000003AYRE"
            })
    void rejectsInvalidBpnl(String value) {
        assertThat(Bpnl.isValid(value)).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> new Bpnl(value));
    }

    @org.junit.jupiter.api.Test
    void rejectsNull() {
        assertThat(Bpnl.isValid(null)).isFalse();
        assertThatNullPointerException().isThrownBy(() -> new Bpnl(null));
    }
}
