package io.github.rkaushikdev.galleyworks;

import java.util.List;

/** The range and choice checks configuration records run in their constructors. */
final class ConfigChecks {

    private ConfigChecks() {}

    static void atLeast(String field, long value, long minimum) {
        if (value < minimum) {
            throw new IllegalArgumentException(field + ": Input should be greater than or equal to " + minimum);
        }
    }

    static void atMost(String field, long value, long maximum) {
        if (value > maximum) {
            throw new IllegalArgumentException(field + ": Input should be less than or equal to " + maximum);
        }
    }

    static void atLeast(String field, double value, double minimum) {
        if (!(value >= minimum)) {
            throw new IllegalArgumentException(field + ": Input should be greater than or equal to " + minimum);
        }
    }

    static void atMost(String field, double value, double maximum) {
        if (!(value <= maximum)) {
            throw new IllegalArgumentException(field + ": Input should be less than or equal to " + maximum);
        }
    }

    static void oneOf(String field, String value, List<String> allowed) {
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException(field + ": Input should be " + ConfigFields.describe(allowed));
        }
    }
}
