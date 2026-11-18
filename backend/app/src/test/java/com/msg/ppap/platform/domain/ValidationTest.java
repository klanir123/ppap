package com.msg.ppap.platform.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ValidationTest {

    enum Color {
        RED,
        GREEN
    }

    @Test
    void trimsAndTreatsBlankAsAbsent() {
        var v = new Validation();
        assertThat(v.optional("a", "  x  ", 5)).isEqualTo("x");
        assertThat(v.optional("a", "   ", 5)).isNull();
        assertThat(v.required("b", " ", 5)).isNull();
        assertThat(v.violations()).extracting(FieldViolation::code).containsExactly(ViolationCodes.REQUIRED);
    }

    @Test
    void reportsTooLongAndInvalidFormat() {
        var v = new Validation();
        assertThat(v.required("a", "abcdef", 3)).isNull();
        assertThat(v.optionalMatching("b", "x y", 10, Pattern.compile("^\\w+$"), "word"))
                .isNull();
        assertThat(v.requiredMatching("c", "ok", 10, Pattern.compile("^\\w+$"), "word"))
                .isEqualTo("ok");
        assertThat(v.optional("d", "BPNL123", Bpnl::new, Bpnl.PATTERN)).isNull();
        assertThat(v.required("e", null, Bpnl::new, Bpnl.PATTERN)).isNull();
        assertThat(v.violations())
                .extracting(FieldViolation::field, FieldViolation::code)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("a", ViolationCodes.TOO_LONG),
                        org.assertj.core.groups.Tuple.tuple("b", ViolationCodes.INVALID_FORMAT),
                        org.assertj.core.groups.Tuple.tuple("d", ViolationCodes.INVALID_FORMAT),
                        org.assertj.core.groups.Tuple.tuple("e", ViolationCodes.REQUIRED));
        assertThatThrownBy(v::throwIfInvalid)
                .isInstanceOf(ValidationException.class)
                .satisfies(ex ->
                        assertThat(((ValidationException) ex).violations()).hasSize(4));
    }

    @Test
    void parsesEnumsCaseInsensitivelyWithDefaults() {
        var v = new Validation();
        assertThat(v.requiredEnum("c", "green", Color.class)).isEqualTo(Color.GREEN);
        assertThat(v.optionalEnum("c", null, Color.class, Color.RED)).isEqualTo(Color.RED);
        assertThat(v.optionalEnum("c", "blue", Color.class, Color.RED)).isNull();
        assertThat(v.requiredEnum("d", "", Color.class)).isNull();
        assertThat(v.violations())
                .extracting(FieldViolation::code)
                .containsExactly(ViolationCodes.INVALID_VALUE, ViolationCodes.REQUIRED);
    }

    @Test
    void parsesEnumSets() {
        var v = new Validation();
        assertThat(v.requiredEnumSet("roles", List.of("red", " ", "GREEN", "red"), Color.class))
                .containsExactlyInAnyOrder(Color.RED, Color.GREEN);
        assertThat(v.requiredEnumSet("none", List.of(), Color.class)).isEmpty();
        assertThat(v.requiredEnumSet("bad", List.of("blue"), Color.class)).isEmpty();
        assertThat(v.violations())
                .extracting(FieldViolation::field, FieldViolation::code)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("none", ViolationCodes.REQUIRED),
                        org.assertj.core.groups.Tuple.tuple("bad", ViolationCodes.INVALID_VALUE));
    }

    @Test
    void validInputDoesNotThrow() {
        var v = new Validation();
        v.required("a", "x", 1);
        v.throwIfInvalid();
        assertThat(v.hasViolations()).isFalse();
    }
}
