package io.github.rkaushikdev.galleyworks;

import java.util.Arrays;

/** How far up the hierarchy of government a jurisdiction sits. */
public enum GovernmentLevel {
    FEDERAL("federal"),
    STATE("state"),
    COUNTY("county"),
    MUNICIPAL("municipal"),
    TRIBAL("tribal");

    private final String value;

    GovernmentLevel(String value) {
        this.value = value;
    }

    /** The lowercase name written in keys, files and reports. */
    public String value() {
        return value;
    }

    /** The constant whose value is {@code value}. */
    public static GovernmentLevel fromValue(String value) {
        return Arrays.stream(values())
                .filter(candidate -> candidate.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("'" + value + "' is not a valid GovernmentLevel"));
    }

    @Override
    public String toString() {
        return value;
    }
}
