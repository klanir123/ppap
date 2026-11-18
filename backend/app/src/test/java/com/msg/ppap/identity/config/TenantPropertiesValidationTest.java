package com.msg.ppap.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TenantPropertiesValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void validPropertiesPass() {
        var properties = new TenantProperties("BPNL00000003AYRE", "OEM AG", "OEM", "did:web:oem");
        assertThat(validator.validate(properties)).isEmpty();
        assertThat(properties.businessPartnerNumber().value()).isEqualTo("BPNL00000003AYRE");
    }

    @Test
    void invalidBpnlIsRejected() {
        var properties = new TenantProperties("BPNL123", "OEM AG", "OEM", null);
        assertThat(validator.validate(properties))
                .singleElement()
                .satisfies(v -> assertThat(v.getPropertyPath()).hasToString("bpnl"));
    }

    @Test
    void blankNameIsRejected() {
        var properties = new TenantProperties("BPNL00000003AYRE", " ", "OEM", null);
        assertThat(validator.validate(properties))
                .singleElement()
                .satisfies(v -> assertThat(v.getPropertyPath()).hasToString("name"));
    }
}
