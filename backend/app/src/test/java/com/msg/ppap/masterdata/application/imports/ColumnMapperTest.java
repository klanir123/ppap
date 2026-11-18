package com.msg.ppap.masterdata.application.imports;

import static org.assertj.core.api.Assertions.assertThat;

import com.msg.ppap.masterdata.config.ImportProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class ColumnMapperTest {

    private final ColumnMapper mapper = new ColumnMapper(new ImportProperties(
            DataSize.ofMegabytes(10),
            10_000,
            500,
            Map.of("partNumber", List.of("Sachnummer", "Part Number"), "nameDe", List.of("Bezeichnung"))));

    @Test
    void mapsFieldNamesAndSynonymsIgnoringCaseAndSpecialCharacters() {
        Map<String, String> mapping =
                mapper.suggest(ImportEntity.PARTS, List.of("SACH-NUMMER", "Bezeichnung", "name_en", "Unbekannt"));
        assertThat(mapping)
                .containsEntry("SACH-NUMMER", "partNumber")
                .containsEntry("Bezeichnung", "nameDe")
                .containsEntry("name_en", "nameEn")
                .containsEntry("Unbekannt", null);
        assertThat(mapping.keySet()).containsExactly("SACH-NUMMER", "Bezeichnung", "name_en", "Unbekannt");
    }

    @Test
    void mapsEachFieldOnlyOnce() {
        assertThat(mapper.suggest(ImportEntity.PARTS, List.of("Part Number", "partNumber")))
                .containsEntry("Part Number", "partNumber")
                .containsEntry("partNumber", null);
    }

    @Test
    void normalizesUmlauts() {
        assertThat(ColumnMapper.normalize("Änderungs-Stand ß")).isEqualTo("aenderungsstandss");
        assertThat(ColumnMapper.normalize(null)).isEmpty();
    }

    @Test
    void entityMetadata() {
        assertThat(ImportEntity.fromPath("PART-MAPPINGS")).contains(ImportEntity.PART_MAPPINGS);
        assertThat(ImportEntity.fromPath("nope")).isEmpty();
        assertThat(ImportEntity.fromPath(null)).isEmpty();
        assertThat(ImportEntity.PART_MAPPINGS.labelKey("partNumber")).isEqualTo("masterdata.partMappings.partNumber");
        assertThat(ImportEntity.RELATIONS.keyFields()).containsExactly("partnerBpnl", "partnerRole");
        assertThat(ImportEntity.PARTS.hasField("bpnl")).isFalse();
        assertThat(ImportEntity.SITES.sheetNames()).contains("sites");
    }
}
