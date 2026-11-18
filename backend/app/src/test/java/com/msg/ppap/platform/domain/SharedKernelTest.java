package com.msg.ppap.platform.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.HashSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SharedKernelTest {

    @Test
    void bpnsValidation() {
        assertThat(new Bpns("BPNS00000003AYRE")).hasToString("BPNS00000003AYRE");
        assertThat(Bpns.isValid("BPNL00000003AYRE")).isFalse();
        assertThat(Bpns.isValid(null)).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> new Bpns("BPNS1"));
    }

    @Test
    void idsAreVersion7AndUnique() {
        var ids = new HashSet<UUID>();
        for (int i = 0; i < 1000; i++) {
            UUID id = Ids.newId();
            assertThat(id.version()).isEqualTo(7);
            assertThat(id.variant()).isEqualTo(2);
            ids.add(id);
        }
        assertThat(ids).hasSize(1000);
    }

    @Test
    void exceptionsCarryCodesAndKinds() {
        assertThat(new NotFoundException("Part", "x"))
                .satisfies(ex -> assertThat(ex.kind()).isEqualTo(PpapException.Kind.NOT_FOUND))
                .satisfies(ex -> assertThat(ex.code()).isEqualTo("NOT_FOUND"));
        var duplicate = new DuplicateKeyException("Part", "partNumber", "4711");
        assertThat(duplicate.field()).isEqualTo("partNumber");
        assertThat(duplicate.kind()).isEqualTo(PpapException.Kind.CONFLICT);
        assertThat(new VersionConflictException("Part", "id", 1, 2).kind())
                .isEqualTo(PpapException.Kind.PRECONDITION_FAILED);
        assertThat(new ReferencedException("in use").code()).isEqualTo("REFERENCED");
        assertThat(ValidationException.of("a", "REQUIRED", "m").violations()).hasSize(1);
    }
}
