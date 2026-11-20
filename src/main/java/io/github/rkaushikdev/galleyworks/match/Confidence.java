package io.github.rkaushikdev.galleyworks.match;

import java.util.Arrays;

/** How strong the evidence behind a match is, on the evidence alone; constants run weakest first. */
public enum Confidence {
    WEAK("weak"),
    MODERATE("moderate"),
    STRONG("strong");

    private final String value;

    Confidence(String value) {
        this.value = value;
    }

    /** The lowercase name written in reports and accepted on the command line. */
    public String value() {
        return value;
    }

    /** The confidence named {@code value}. */
    public static Confidence fromValue(String value) {
        return Arrays.stream(values())
                .filter(candidate -> candidate.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("'" + value + "' is not a valid Confidence"));
    }

    @Override
    public String toString() {
        return value;
    }
}
