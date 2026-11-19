package com.msg.ppap.approval.application;

import com.msg.ppap.approval.domain.CatalogRef;
import com.msg.ppap.approval.domain.ElementSnapshot;
import com.msg.ppap.approval.domain.EvidenceType;
import com.msg.ppap.approval.domain.RequestedElement;
import com.msg.ppap.approval.domain.Requirement;
import com.msg.ppap.masterdata.api.MasterdataQueries;
import com.msg.ppap.masterdata.api.MasterdataQueries.CatalogElementInfo;
import com.msg.ppap.masterdata.api.MasterdataQueries.LevelInfo;
import com.msg.ppap.platform.domain.Validation;
import com.msg.ppap.platform.domain.ViolationCodes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Builds snapshots of catalogue entries for requests (UC-02: catalogue changes do not affect running cases). */
@Component
public class RequestElementFactory {

    static final int NOTE_MAX = 4000;

    private final MasterdataQueries masterdata;

    public RequestElementFactory(MasterdataQueries masterdata) {
        this.masterdata = masterdata;
    }

    /** Snapshot of an active reason; records a violation if unknown or inactive. */
    public CatalogRef reason(Validation v, String field, String code) {
        String value = Validation.trimToNull(code);
        if (value == null) {
            v.add(field, ViolationCodes.REQUIRED, "must not be blank");
            return null;
        }
        return masterdata
                .findReason(value)
                .map(r -> {
                    if (!r.active()) {
                        v.add(field, ViolationCodes.INACTIVE_REFERENCE, "reason is inactive");
                    }
                    return new CatalogRef(r.code(), r.nameDe(), r.nameEn());
                })
                .orElseGet(() -> {
                    v.add(field, ViolationCodes.UNKNOWN_CODE, "unknown reason");
                    return null;
                });
    }

    /** Active level; records a violation if unknown or inactive. */
    public LevelInfo level(Validation v, String field, String code) {
        String value = Validation.trimToNull(code);
        if (value == null) {
            v.add(field, ViolationCodes.REQUIRED, "must not be blank");
            return null;
        }
        return masterdata
                .findLevel(value)
                .map(l -> {
                    if (!l.active()) {
                        v.add(field, ViolationCodes.INACTIVE_REFERENCE, "level is inactive");
                    }
                    return l;
                })
                .orElseGet(() -> {
                    v.add(field, ViolationCodes.UNKNOWN_CODE, "unknown level");
                    return null;
                });
    }

    public static CatalogRef ref(LevelInfo level) {
        return new CatalogRef(level.code(), level.nameDe(), level.nameEn());
    }

    /**
     * Requested elements: the given ones, or – without input – the elements {@code REQUIRED} at the level.
     * Unknown, inactive or duplicate codes are violations.
     */
    public List<RequestedElement> elements(Validation v, LevelInfo level, List<ElementInput> input) {
        Map<String, CatalogElementInfo> catalogue =
                masterdata.elements().stream().collect(Collectors.toMap(CatalogElementInfo::code, Function.identity()));
        Map<String, String> matrix = level == null ? Map.of() : level.requirements();
        List<RequestedElement> result = new ArrayList<>();
        if (input == null || input.isEmpty()) {
            catalogue.values().stream()
                    .sorted((a, b) -> Integer.compare(a.sortOrder(), b.sortOrder()))
                    .filter(e -> e.active() && "REQUIRED".equals(matrix.get(e.code())))
                    .forEach(e -> result.add(new RequestedElement(e.code(), Requirement.REQUIRED, null, snapshot(e))));
            return result;
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < input.size(); i++) {
            ElementInput item = input.get(i);
            String prefix = "elements[" + i + "].";
            String code = item == null ? null : Validation.trimToNull(item.code());
            if (code == null) {
                v.add(prefix + "code", ViolationCodes.REQUIRED, "must not be blank");
                continue;
            }
            CatalogElementInfo element = catalogue.get(code);
            if (element == null) {
                v.add(prefix + "code", ViolationCodes.UNKNOWN_CODE, "unknown element code");
                continue;
            }
            if (!element.active()) {
                v.add(prefix + "code", ViolationCodes.INACTIVE_REFERENCE, "element is inactive");
                continue;
            }
            if (!seen.add(code)) {
                v.add(prefix + "code", ViolationCodes.DUPLICATE_KEY, "element requested twice");
                continue;
            }
            Requirement requirement = requirement(v, prefix + "requirement", item.requirement(), matrix.get(code));
            String note = v.optional(prefix + "note", item.note(), NOTE_MAX);
            if (requirement != null) {
                result.add(new RequestedElement(code, requirement, note, snapshot(element)));
            }
        }
        return result;
    }

    /** Snapshot of a catalogue element by code (seed). */
    public ElementSnapshot snapshot(String code) {
        return masterdata
                .findElement(code)
                .map(RequestElementFactory::snapshot)
                .orElseThrow(() -> new IllegalArgumentException("unknown element code " + code));
    }

    static ElementSnapshot snapshot(CatalogElementInfo e) {
        return new ElementSnapshot(e.nameDe(), e.nameEn(), EvidenceType.valueOf(e.evidenceType()), e.valueSchema());
    }

    private static Requirement requirement(Validation v, String field, String raw, String levelDefault) {
        String value = Validation.trimToNull(raw);
        if (value == null) {
            return "ON_REQUEST".equals(levelDefault) ? Requirement.ON_REQUEST : Requirement.REQUIRED;
        }
        try {
            return Requirement.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            v.add(field, ViolationCodes.INVALID_VALUE, "must be REQUIRED or ON_REQUEST");
            return null;
        }
    }
}
