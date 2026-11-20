package com.msg.ppap.masterdata.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.msg.ppap.platform.domain.FieldViolation;
import com.msg.ppap.platform.domain.ValidationException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Validation of policy profiles (UsagePurpose format, FrameworkAgreement, additional constraints). */
class PolicyProfileValuesTest {

    @Test
    void parsesAValidProfileWithDefaults() {
        PolicyProfileValues v = PolicyProfileValues.parse(
                "quality",
                " Quality ",
                null,
                null,
                null,
                "DataExchangeGovernance:1.0",
                List.of("cx.quality.base:1", "cx.quality.base:1", "msg.ppap.productApproval:1"),
                List.of(new PolicyProfileValues.RawConstraint("ContractReference", "isAnyOf", List.of(" A ", "B"))));
        assertThat(v.name()).isEqualTo("Quality");
        assertThat(v.accessType()).isEqualTo(PolicyProfileValues.AccessType.BPN_GROUP);
        assertThat(v.membershipRequired()).isTrue();
        assertThat(v.usagePurposes()).containsExactly("cx.quality.base:1", "msg.ppap.productApproval:1");
        assertThat(v.additionalConstraints())
                .singleElement()
                .satisfies(c -> assertThat(c.rightOperand()).isEqualTo(List.of("A", "B")));
        PolicyProfile profile = PolicyProfile.create(v, true);
        assertThat(profile.type()).isEqualTo(MasterdataType.POLICY_PROFILE);
        assertThat(profile.businessKey()).isEqualTo("quality");
        assertThat(profile.isDefault()).isFalse();
    }

    @Test
    void reportsAllViolations() {
        assertThatThrownBy(() -> PolicyProfileValues.parse(
                        "bad code!",
                        null,
                        null,
                        "NOPE",
                        false,
                        "",
                        List.of("no-version", " "),
                        List.of(
                                new PolicyProfileValues.RawConstraint("UsagePurpose", "eq", "x"),
                                new PolicyProfileValues.RawConstraint("Ok", "like", List.of()),
                                new PolicyProfileValues.RawConstraint("Ok", "eq", List.of(1)))))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).violations())
                        .extracting(FieldViolation::field)
                        .contains(
                                "code",
                                "name",
                                "access.type",
                                "usage.frameworkAgreement",
                                "usage.usagePurposes[0]",
                                "usage.additionalConstraints[0].leftOperand",
                                "usage.additionalConstraints[1].operator",
                                "usage.additionalConstraints[1].rightOperand",
                                "usage.additionalConstraints[2].rightOperand"));
        assertThatThrownBy(() -> PolicyProfileValues.parse(
                        "ok", "Ok", null, "BPN", true, "DataExchangeGovernance:1.0", List.of(), null))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).violations())
                        .extracting(FieldViolation::field)
                        .containsExactly("usage.usagePurposes"));
        assertThatThrownBy(() -> PolicyProfileValues.parse(
                        "ok",
                        "Ok",
                        null,
                        "BPN",
                        true,
                        "DataExchangeGovernance:1.0",
                        List.of("a:1"),
                        java.util.Arrays.asList((PolicyProfileValues.RawConstraint) null)))
                .isInstanceOf(ValidationException.class);
    }
}
