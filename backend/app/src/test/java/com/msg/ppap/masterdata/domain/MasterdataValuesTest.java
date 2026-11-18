package com.msg.ppap.masterdata.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.msg.ppap.platform.domain.Bpnl;
import com.msg.ppap.platform.domain.FieldViolation;
import com.msg.ppap.platform.domain.ValidationException;
import com.msg.ppap.platform.domain.ViolationCodes;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MasterdataValuesTest {

    private static final Bpnl OWN = new Bpnl("BPNL000000000OEM");

    @Test
    void partValues() {
        var values = PartValues.parse(" 4711-01/A ", "Halter", "", "Z 4711", "B", "PA6", null);
        assertThat(values.partNumber().value()).isEqualTo("4711-01/A");
        assertThat(values.nameEn()).isNull();
        assertThat(values.drawingNumber()).isEqualTo(new PartNumber("Z 4711"));
        assertThat(PartValues.parse("1", "x", null, null, null, null, "urn:uuid:3fa85f64-5717-4562-b3fc-2c963f66afa6")
                        .catenaXId())
                .startsWith("urn:uuid:");
    }

    @Test
    void partValuesReportAllViolations() {
        assertViolations(
                () -> PartValues.parse("47#11", "", null, "x".repeat(65), null, null, "uuid:1"),
                tuple("partNumber", ViolationCodes.INVALID_FORMAT),
                tuple("nameDe", ViolationCodes.REQUIRED),
                tuple("drawingNumber", ViolationCodes.INVALID_FORMAT),
                tuple("catenaXId", ViolationCodes.INVALID_FORMAT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a#b", " lead", "12345678901234567890123456789012345678901234567890123456789012345"})
    void invalidPartNumbers(String value) {
        assertThatThrownBy(() -> new PartNumber(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void partnerValues() {
        var values = PartnerValues.parse(
                "BPNL00000003AYRE",
                "Supplier GmbH",
                List.of("supplier", "CUSTOMER"),
                "https://edc.example/api/v1/dsp",
                "did:web:supplier.example",
                "qs@supplier.example",
                OWN);
        assertThat(values.roles()).containsExactlyInAnyOrder(PartnerRole.SUPPLIER, PartnerRole.CUSTOMER);
        assertThat(values.addressable()).isTrue();
        var minimal = PartnerValues.parse("BPNL00000003AYRE", "S", List.of("SUPPLIER"), null, null, null, OWN);
        assertThat(minimal.addressable()).isFalse();
    }

    @Test
    void partnerValuesRejectInvalidInput() {
        assertViolations(
                () -> PartnerValues.parse("BPNL1", "", List.of("FOO"), "ftp://x", "web:x", "no-mail", OWN),
                tuple("bpnl", ViolationCodes.INVALID_FORMAT),
                tuple("name", ViolationCodes.REQUIRED),
                tuple("roles", ViolationCodes.INVALID_VALUE),
                tuple("dspEndpoint", ViolationCodes.INVALID_FORMAT),
                tuple("did", ViolationCodes.INVALID_FORMAT),
                tuple("contactEmail", ViolationCodes.INVALID_FORMAT));
        assertViolations(
                () -> PartnerValues.parse("BPNL00000003AYRE", "S", List.of(), "x".repeat(3000), null, null, OWN),
                tuple("roles", ViolationCodes.REQUIRED),
                tuple("dspEndpoint", ViolationCodes.TOO_LONG));
    }

    @Test
    void ownTenantCannotBePartner() {
        assertThatThrownBy(() -> PartnerValues.parse(OWN.value(), "Me", List.of("CUSTOMER"), null, null, null, OWN))
                .isInstanceOf(ValidationException.class)
                .satisfies(
                        ex -> assertThat(((ValidationException) ex).code()).isEqualTo(ViolationCodes.SELF_REFERENCE));
    }

    @Test
    void siteValues() {
        var values = SiteValues.parse("BPNS00000003AYRE", "Werk 1", OWN.value(), "Str. 1", "80331", "München", "de");
        assertThat(values.country()).isEqualTo("DE");
        assertViolations(
                () -> SiteValues.parse("BPNL00000003AYRE", "", "x", null, null, null, "XX"),
                tuple("bpns", ViolationCodes.INVALID_FORMAT),
                tuple("name", ViolationCodes.REQUIRED),
                tuple("ownerBpnl", ViolationCodes.INVALID_FORMAT),
                tuple("country", ViolationCodes.INVALID_FORMAT));
    }

    @Test
    void relationValuesDefaults() {
        var values = RelationValues.parse("BPNL00000003AYRE", "customer", null, " ");
        assertThat(values.exchangeMode()).isEqualTo(ExchangeMode.PUSH_NOTIFY_PULL_PAYLOAD);
        assertThat(values.policyProfile()).isEqualTo("default");
        assertThat(values.key()).isEqualTo("BPNL00000003AYRE/CUSTOMER");
        assertViolations(
                () -> RelationValues.parse(null, "OEM", "FAX", null),
                tuple("partnerBpnl", ViolationCodes.REQUIRED),
                tuple("partnerRole", ViolationCodes.INVALID_VALUE),
                tuple("exchangeMode", ViolationCodes.INVALID_VALUE));
    }

    @Test
    void partMappingValues() {
        UUID relation = UUID.randomUUID();
        UUID part = UUID.randomUUID();
        var values = PartMappingValues.parse(relation, part, "K-1", "BPNS00000003AYRE", null);
        assertThat(values.partnerPartNumber().value()).isEqualTo("K-1");
        assertViolations(
                () -> PartMappingValues.parse(relation, null, "", "BPNS1", null),
                tuple("partId", ViolationCodes.REQUIRED),
                tuple("partnerPartNumber", ViolationCodes.REQUIRED),
                tuple("ownSiteBpns", ViolationCodes.INVALID_FORMAT));
    }

    @Test
    void catalogValues() {
        var element = CatalogElementValues.parse(
                "VDA-PPF-09",
                "Prüfergebnisse",
                "Test results",
                null,
                null,
                "VDA 2",
                "AIAG 9",
                "values",
                Map.of("type", "object"),
                null);
        assertThat(element.evidenceType()).isEqualTo(EvidenceType.VALUES);
        assertThat(element.sortOrder()).isZero();
        assertThat(CatalogElementValues.parse("AB", "a", "b", null, null, null, null, "DOCUMENT", Map.of(), 5)
                        .valueSchema())
                .isNull();
        assertViolations(
                () -> CatalogElementValues.parse("a", "", "", null, null, null, null, null, null, 1),
                tuple("code", ViolationCodes.INVALID_FORMAT),
                tuple("nameDe", ViolationCodes.REQUIRED),
                tuple("nameEn", ViolationCodes.REQUIRED),
                tuple("evidenceType", ViolationCodes.REQUIRED));

        var reason = SubmissionReasonValues.parse("NEW_PART", "Neuteil", "New part", null, null, 10);
        assertThat(reason.code()).isEqualTo(new CatalogCode("NEW_PART"));
        assertViolations(
                () -> SubmissionReasonValues.parse("", "x", "", null, null, null),
                tuple("code", ViolationCodes.REQUIRED),
                tuple("nameEn", ViolationCodes.REQUIRED));
    }

    @Test
    void levelMatrixIsNormalized() {
        var level = SubmissionLevelValues.parse(
                "VDA-3",
                "Stufe 3",
                "Level 3",
                null,
                null,
                null,
                30,
                Map.of("VDA-01", "required", "VDA-02", "NOT_REQUIRED", "VDA-09", "ON_REQUEST"));
        assertThat(level.scheme()).isEqualTo(LevelScheme.CUSTOM);
        assertThat(level.requirements()).containsOnlyKeys(new CatalogCode("VDA-01"), new CatalogCode("VDA-09"));
        assertThat(level.requirementOf(new CatalogCode("VDA-02"))).isEqualTo(Requirement.NOT_REQUIRED);
        assertThat(level.requirementOf(new CatalogCode("VDA-01"))).isEqualTo(Requirement.REQUIRED);
        assertViolations(
                () -> SubmissionLevelValues.parse(
                        "VDA-3", "a", "b", null, null, "ISO", null, Map.of("bad code", "REQUIRED", "VDA-01", "MAYBE")),
                tuple("requirements.VDA-01", ViolationCodes.INVALID_VALUE),
                tuple("requirements.bad code", ViolationCodes.INVALID_FORMAT),
                tuple("scheme", ViolationCodes.INVALID_VALUE));
    }

    @Test
    void aggregateBehaviour() {
        var values = PartValues.parse("1", "a", null, null, null, null, null);
        Part part = Part.create(values, true);
        assertThat(part.version()).isNull();
        assertThat(part.metadata()).isNull();
        assertThat(part.businessKey()).isEqualTo("1");
        assertThat(part.wouldChange(values, true)).isFalse();
        assertThat(part.wouldChange(values, false)).isTrue();
        part.checkVersion(5L); // new objects have no version to compare
        part.deactivate();
        assertThat(part.active()).isFalse();

        var persisted = Part.restore(part.id(), values, true, new Metadata(3, null, "a", null, "b"));
        persisted.checkVersion(null);
        persisted.checkVersion(3L);
        assertThatThrownBy(() -> persisted.checkVersion(2L))
                .isInstanceOf(com.msg.ppap.platform.domain.VersionConflictException.class);
        var changed = PartValues.parse("1", "b", null, null, null, null, null);
        assertThat(persisted.wouldChange(changed, true)).isTrue();
        persisted.update(changed, true);
        assertThat(persisted.values()).isEqualTo(changed);
    }

    @Test
    void aggregateTypesAndKeys() {
        var partner = Partner.create(
                PartnerValues.parse("BPNL00000003AYRE", "S", List.of("SUPPLIER"), "http://edc", "did:web:s", null, OWN),
                true);
        assertThat(partner.exchangeReady()).isTrue();
        assertThat(partner.hasRole(PartnerRole.SUPPLIER)).isTrue();
        assertThat(partner.hasRole(PartnerRole.CUSTOMER)).isFalse();
        assertThat(partner.type()).isEqualTo(MasterdataType.PARTNER);
        assertThat(partner.businessKey()).isEqualTo("BPNL00000003AYRE");
        partner.deactivate();
        assertThat(partner.exchangeReady()).isFalse();

        var site = Site.create(SiteValues.parse("BPNS00000003AYRE", "W", OWN.value(), null, null, null, null), true);
        assertThat(site.businessKey()).isEqualTo("BPNS00000003AYRE");
        assertThat(site.type().label()).isEqualTo("Site");

        var relation = Relation.create(RelationValues.parse("BPNL00000003AYRE", "SUPPLIER", "PUSH", "p"), true);
        assertThat(relation.businessKey()).isEqualTo("BPNL00000003AYRE/SUPPLIER");
        assertThat(relation.type()).isEqualTo(MasterdataType.RELATION);

        var mappingValues = PartMappingValues.parse(relation.id(), UUID.randomUUID(), "K-1", null, null);
        var mapping = PartMapping.create(mappingValues);
        assertThat(mapping.wouldChange(mappingValues)).isFalse();
        var other = PartMappingValues.parse(relation.id(), mappingValues.partId(), "K-2", null, null);
        mapping.update(other);
        assertThat(mapping.businessKey()).endsWith("/K-2");
        assertThat(mapping.type()).isEqualTo(MasterdataType.PART_MAPPING);

        var element = CatalogElement.create(
                CatalogElementValues.parse("VDA-01", "a", "b", null, null, null, null, "DOCUMENT", null, 1), true);
        assertThat(element.businessKey()).isEqualTo("VDA-01");
        assertThat(element.type()).isEqualTo(MasterdataType.CATALOG_ELEMENT);
        var level = SubmissionLevel.create(
                SubmissionLevelValues.parse("L1", "a", "b", null, null, "VDA", 1, Map.of("VDA-01", "REQUIRED")), true);
        assertThat(level.references(new CatalogCode("VDA-01"))).isTrue();
        assertThat(level.businessKey()).isEqualTo("L1");
        assertThat(level.type()).isEqualTo(MasterdataType.SUBMISSION_LEVEL);
        var reason = SubmissionReason.create(SubmissionReasonValues.parse("R1", "a", "b", null, null, 1), true);
        assertThat(reason.businessKey()).isEqualTo("R1");
        assertThat(reason.type()).isEqualTo(MasterdataType.SUBMISSION_REASON);
        assertThat(CatalogElement.restore(element.id(), element.values(), false, null)
                        .active())
                .isFalse();
        assertThat(SubmissionLevel.restore(level.id(), level.values(), true, null)
                        .id())
                .isEqualTo(level.id());
        assertThat(SubmissionReason.restore(reason.id(), reason.values(), true, null)
                        .id())
                .isEqualTo(reason.id());
        assertThat(Partner.restore(partner.id(), partner.values(), true, null).id())
                .isEqualTo(partner.id());
        assertThat(Site.restore(site.id(), site.values(), true, null).id()).isEqualTo(site.id());
        assertThat(Relation.restore(relation.id(), relation.values(), true, null)
                        .id())
                .isEqualTo(relation.id());
        assertThat(PartMapping.restore(mapping.id(), mapping.values(), null).id())
                .isEqualTo(mapping.id());
    }

    @Test
    void rules() {
        assertThat(MasterdataRules.httpUrl("https://x.example/a")).hasHost("x.example");
        assertThatThrownBy(() -> MasterdataRules.httpUrl("/relative")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MasterdataRules.httpUrl("mailto:a@b.c")).isInstanceOf(IllegalArgumentException.class);
        assertThat(MasterdataRules.country("at")).isEqualTo("AT");
        assertThat(new CatalogCode("VDA-01")).hasToString("VDA-01");
        assertThat(new PartNumber("4711")).hasToString("4711");
    }

    @SafeVarargs
    private static void assertViolations(ThrowingCallable call, org.assertj.core.groups.Tuple... expected) {
        assertThatThrownBy(call)
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).violations())
                        .extracting(FieldViolation::field, FieldViolation::code)
                        .containsExactlyInAnyOrder(expected));
    }
}
