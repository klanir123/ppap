package com.msg.ppap.semantics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.msg.ppap.semantics.api.CanonicalJson;
import com.msg.ppap.semantics.api.PayloadHash;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** RFC 8785 test vectors (sections 3.2.2, 3.2.3 and appendix B). */
class CanonicalJsonTest {

    @Test
    void rfc8785Section322Example() {
        String input = "{\"numbers\":[333333333.33333329,1E30,4.50,2e-3,0.000000000000000000000000001],"
                + "\"string\":\"\\u20ac$\\u000F\\u000aA'\\u0042\\u0022\\u005c\\\\\\\"\\/\","
                + "\"literals\":[null,true,false]}";
        assertThat(CanonicalJson.canonicalizeJson(input))
                .isEqualTo("{\"literals\":[null,true,false],\"numbers\":[333333333.3333333,1e+30,4.5,0.002,1e-27],"
                        + "\"string\":\"€$\\u000f\\nA'B\\\"\\\\\\\\\\\"/\"}");
    }

    @Test
    void rfc8785Section323SortsByUtf16CodeUnits() {
        String input = "{\"\\u20ac\":\"Euro Sign\",\"\\r\":\"Carriage Return\",\"\\ufb33\":\"Hebrew Letter Dalet With"
                + " Dagesh\",\"1\":\"One\",\"\\ud83d\\ude00\":\"Emoji: Grinning Face\",\"\\u0080\":\"Control\","
                + "\"\\u00f6\":\"Latin Small Letter O With Diaeresis\"}";
        assertThat(CanonicalJson.canonicalizeJson(input))
                .isEqualTo(
                        "{\"\\r\":\"Carriage Return\",\"1\":\"One\",\"\u0080\":\"Control\",\"ö\":\"Latin Small Letter"
                                + " O With Diaeresis\",\"€\":\"Euro Sign\",\"😀\":\"Emoji: Grinning Face\",\"\ufb33\":\"Hebrew"
                                + " Letter Dalet With Dagesh\"}");
    }

    @ParameterizedTest
    @CsvSource({
        "0, 0",
        "-0, 0",
        "1e21, 1e+21",
        "1e20, 100000000000000000000",
        "9007199254740992, 9007199254740992",
        "0.000001, 0.000001",
        "1e-7, 1e-7",
        "999999999999999900000, 999999999999999900000",
        "4.0, 4",
        "-1.5, -1.5"
    })
    void rfc8785AppendixBNumbers(String number, String expected) {
        assertThat(CanonicalJson.canonicalizeJson("[" + number + "]")).isEqualTo("[" + expected + "]");
    }

    @Test
    void javaValuesAreCanonicalized() {
        var map = new LinkedHashMap<String, Object>();
        map.put("b", 1);
        map.put("a", List.of(true, "x"));
        map.put("t", Instant.parse("2026-11-19T10:00:00Z"));
        assertThat(CanonicalJson.write(map)).isEqualTo("{\"a\":[true,\"x\"],\"b\":1,\"t\":\"2026-11-19T10:00:00Z\"}");
        assertThat(CanonicalJson.canonicalize(Map.of("k", "ü")))
                .isEqualTo("{\"k\":\"ü\"}".getBytes(StandardCharsets.UTF_8));
        assertThatIllegalArgumentException().isThrownBy(() -> CanonicalJson.write(Map.of("x", List.of(Double.NaN))));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CanonicalJson.write(new Object[] {Float.NEGATIVE_INFINITY}));
        assertThatIllegalArgumentException().isThrownBy(() -> CanonicalJson.canonicalizeJson("{broken"));
    }

    @Test
    void canonicalBytesAreStableAcrossFormatting() {
        byte[] a =
                CanonicalJson.canonicalizeJson("{ \"b\" : [1, 2.0], \"a\" : \"x\" }".getBytes(StandardCharsets.UTF_8));
        byte[] b = CanonicalJson.canonicalizeJson("{\"a\":\"x\",\"b\":[1,2]}".getBytes(StandardCharsets.UTF_8));
        assertThat(a).isEqualTo(b);
        assertThat(PayloadHash.of(a)).isEqualTo(PayloadHash.of(b)).matches(PayloadHash.PATTERN);
        assertThat(PayloadHash.of("abc".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
