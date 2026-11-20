package io.github.rkaushikdev.galleyworks;

import java.util.Arrays;

/** The chamber an instrument originated in. */
public enum Chamber {
    LOWER("lower"),
    UPPER("upper"),
    UNICAMERAL("unicameral"),
    COUNCIL("council"),
    EXECUTIVE("executive");

    private final String value;

    Chamber(String value) {
        this.value = value;
    }

    /** The lowercase name written in keys, files and reports. */
    public String value() {
        return value;
    }

    /** The constant whose value is {@code value}. */
    public static Chamber fromValue(String value) {
        return Arrays.stream(values())
                .filter(candidate -> candidate.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("'" + value + "' is not a valid Chamber"));
    }

    @Override
    public String toString() {
        return value;
    }
}
