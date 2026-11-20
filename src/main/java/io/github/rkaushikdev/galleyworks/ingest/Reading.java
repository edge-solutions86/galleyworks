package io.github.rkaushikdev.galleyworks.ingest;

import java.util.Arrays;

/** Which of the two texts a marked-up amendment draft describes. */
public enum Reading {
    CURRENT("current"),
    PROPOSED("proposed");

    private final String value;

    Reading(String value) {
        this.value = value;
    }

    /** The lowercase name used in configuration and reader names. */
    public String value() {
        return value;
    }

    /** The reading named {@code value}. */
    public static Reading fromValue(String value) {
        return Arrays.stream(values())
                .filter(candidate -> candidate.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("'" + value + "' is not a valid Reading"));
    }

    @Override
    public String toString() {
        return value;
    }
}
