package com.msg.ppap.masterdata.domain;

import java.util.Objects;

/**
 * Part number (own or partner part number, drawing number): 1–64 characters of {@code [A-Za-z0-9._- /]}.
 *
 * @param value the part number, trimmed
 */
public record PartNumber(String value) {

    public PartNumber {
        Objects.requireNonNull(value, "part number must not be null");
        if (value.isEmpty()
                || value.length() > MasterdataRules.PART_NUMBER_MAX
                || !MasterdataRules.PART_NUMBER.matcher(value).matches()
                || !value.equals(value.strip())) {
            throw new IllegalArgumentException("Invalid part number: '" + value + "'");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
