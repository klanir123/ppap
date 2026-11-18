package com.msg.ppap.masterdata.application.imports;

import com.msg.ppap.masterdata.config.ImportProperties;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Suggests a column-to-field mapping: headers are compared case-insensitively without whitespace and special
 * characters against field names and the configured synonyms. Each field is mapped at most once (first match).
 */
@Component
public class ColumnMapper {

    private final Map<String, List<String>> synonyms;

    public ColumnMapper(ImportProperties properties) {
        this.synonyms = properties.synonyms();
    }

    /** Suggested mapping in column order; unmapped columns map to {@code null}. */
    public Map<String, String> suggest(ImportEntity entity, List<String> columns) {
        Map<String, String> lookup = new HashMap<>();
        for (ImportEntity.TargetField field : entity.fields()) {
            lookup.putIfAbsent(normalize(field.name()), field.name());
            for (String synonym : synonyms.getOrDefault(field.name(), List.of())) {
                lookup.putIfAbsent(normalize(synonym), field.name());
            }
        }
        Map<String, String> mapping = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        for (String column : columns) {
            String field = lookup.get(normalize(column));
            if (field != null && used.add(field)) {
                mapping.put(column, field);
            } else {
                mapping.put(column, null);
            }
        }
        return mapping;
    }

    /** Lower case, umlauts transliterated, only letters and digits. */
    static String normalize(String header) {
        if (header == null) {
            return "";
        }
        String lower = header.toLowerCase(Locale.ROOT)
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss");
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        return decomposed.replaceAll("[^a-z0-9]", "");
    }
}
