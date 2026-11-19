package com.msg.ppap.exchange.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a step works on (details {@code target} / {@code targetRef}): a pull legitimately runs catalog, negotiation,
 * transfer and EDR several times – for the DTR asset, the submodel asset and each document asset – so the cockpit
 * groups steps by target; a real retry increments the attempt instead.
 */
public enum StepTarget {
    NOTIFICATION,
    PUBLIC_KEYS,
    DTR,
    SUBMODEL,
    DOCUMENT;

    public static final String KEY = "target";
    public static final String REF_KEY = "targetRef";

    /** Details with target (and optional reference) added unless already present. */
    public Map<String, Object> apply(Map<String, Object> details, String ref) {
        var copy = new LinkedHashMap<String, Object>();
        copy.put(KEY, name());
        if (ref != null) {
            copy.put(REF_KEY, ref);
        }
        if (details != null) {
            copy.putAll(details);
        }
        return copy;
    }
}
