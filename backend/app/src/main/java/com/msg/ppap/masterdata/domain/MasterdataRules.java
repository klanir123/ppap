package com.msg.ppap.masterdata.domain;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Field rules shared by UI, API and import (docs/api/masterdata-api.md, "Validierungsregeln"). */
public final class MasterdataRules {

    public static final int NAME_MAX = 255;
    public static final int TEXT_MAX = 4000;
    public static final int SHORT_MAX = 64;
    public static final int URL_MAX = 2048;
    public static final int EMAIL_MAX = 254;

    /** Part numbers, partner part numbers, drawing numbers. */
    public static final int PART_NUMBER_MAX = 64;

    public static final Pattern PART_NUMBER = Pattern.compile("^[A-Za-z0-9._\\- /]+$");
    public static final Pattern DID = Pattern.compile("^did:[a-z0-9]+:.+$");
    public static final Pattern CATALOG_CODE = Pattern.compile("^[A-Z0-9][A-Z0-9_\\-]{1,31}$");
    public static final Pattern CATENA_X_ID =
            Pattern.compile("^urn:uuid:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    public static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private static final Set<String> COUNTRIES = Set.of(Locale.getISOCountries());

    private MasterdataRules() {}

    /** Parses an absolute http(s) URL. */
    public static URI httpUrl(String value) {
        URI uri = URI.create(value);
        String scheme = uri.getScheme();
        if (!uri.isAbsolute()
                || uri.getHost() == null
                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("not an absolute http(s) URL: " + value);
        }
        return uri;
    }

    /** Validates an ISO 3166-1 alpha-2 country code (upper case). */
    public static String country(String value) {
        String upper = value.toUpperCase(Locale.ROOT);
        if (!COUNTRIES.contains(upper)) {
            throw new IllegalArgumentException("not an ISO 3166-1 alpha-2 code: " + value);
        }
        return upper;
    }
}
