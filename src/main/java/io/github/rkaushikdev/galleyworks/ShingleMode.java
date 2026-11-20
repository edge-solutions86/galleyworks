package io.github.rkaushikdev.galleyworks;

import java.util.Arrays;
import java.util.List;

/** Whether shingles are cut from words or from characters. */
public enum ShingleMode {
    WORD("word"),
    CHARACTER("character");

    private final String value;

    ShingleMode(String value) {
        this.value = value;
    }

    /** The lowercase name written in configuration files. */
    public String value() {
        return value;
    }

    /** The mode whose value is {@code value}. */
    public static ShingleMode fromValue(String value) {
        return Arrays.stream(values())
                .filter(candidate -> candidate.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("'" + value + "' is not a valid ShingleMode"));
    }

    static ShingleMode read(ConfigFields fields, String key, ShingleMode fallback) {
        if (!fields.has(key)) {
            return fallback;
        }
        Object raw = fields.raw(key);
        if (raw instanceof ShingleMode mode) {
            return mode;
        }
        for (ShingleMode mode : values()) {
            if (mode.value.equals(raw)) {
                return mode;
            }
        }
        fields.problem(key, "Input should be " + ConfigFields.describe(List.of("word", "character")));
        return fallback;
    }

    @Override
    public String toString() {
        return value;
    }
}
