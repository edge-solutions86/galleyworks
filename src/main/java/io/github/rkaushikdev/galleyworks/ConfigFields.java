package io.github.rkaushikdev.galleyworks;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads one section of a configuration mapping with the coercions the configuration has always
 * accepted: a whole float or a numeric string for an integer, a number or numeric string for a
 * float, the usual spellings of yes and no for a flag.
 *
 * <p>Problems are collected rather than thrown one at a time, so a file with three mistakes reports
 * all three.
 */
final class ConfigFields {

    private static final Set<String> TRUE = Set.of("1", "on", "t", "true", "y", "yes");
    private static final Set<String> FALSE = Set.of("0", "off", "f", "false", "n", "no");

    private final Map<?, ?> data;
    private final String section;
    private final List<String> problems;

    private ConfigFields(Map<?, ?> data, String section, List<String> problems) {
        this.data = data;
        this.section = section;
        this.problems = problems;
    }

    /** A reader over {@code value}, which must be a mapping, under {@code section}. */
    static ConfigFields of(Object value, String section, Set<String> known, List<String> problems) {
        if (value == null) {
            return new ConfigFields(Map.of(), section, problems);
        }
        if (!(value instanceof Map<?, ?> map)) {
            problems.add(location(section, "") + "Input should be a valid dictionary");
            return new ConfigFields(Map.of(), section, problems);
        }
        for (Object key : map.keySet()) {
            if (!known.contains(String.valueOf(key))) {
                problems.add(location(section, String.valueOf(key)) + "Extra inputs are not permitted");
            }
        }
        return new ConfigFields(map, section, problems);
    }

    /** A fresh problem list. */
    static List<String> problems() {
        return new ArrayList<>();
    }

    boolean has(String key) {
        return data.containsKey(key);
    }

    Object raw(String key) {
        return data.get(key);
    }

    int integer(String key, int fallback) {
        if (!data.containsKey(key)) {
            return fallback;
        }
        Optional<Long> value = wholeNumber(data.get(key));
        if (value.isEmpty() || value.get() > Integer.MAX_VALUE || value.get() < Integer.MIN_VALUE) {
            problem(key, "Input should be a valid integer");
            return fallback;
        }
        return value.get().intValue();
    }

    long longInteger(String key, long fallback) {
        if (!data.containsKey(key)) {
            return fallback;
        }
        Optional<Long> value = wholeNumber(data.get(key));
        if (value.isEmpty()) {
            problem(key, "Input should be a valid integer");
            return fallback;
        }
        return value.get();
    }

    Optional<Integer> optionalInteger(String key, Optional<Integer> fallback) {
        if (!data.containsKey(key)) {
            return fallback;
        }
        if (data.get(key) == null) {
            return Optional.empty();
        }
        return Optional.of(integer(key, 0));
    }

    double number(String key, double fallback) {
        if (!data.containsKey(key)) {
            return fallback;
        }
        Object value = data.get(key);
        if (value instanceof Number number && !(value instanceof Boolean)) {
            return number.doubleValue();
        }
        if (value instanceof Boolean flag) {
            return flag ? 1.0 : 0.0;
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.strip());
            } catch (NumberFormatException exc) {
                problem(key, "Input should be a valid number, unable to parse string as a number");
                return fallback;
            }
        }
        problem(key, "Input should be a valid number");
        return fallback;
    }

    boolean flag(String key, boolean fallback) {
        if (!data.containsKey(key)) {
            return fallback;
        }
        Object value = data.get(key);
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof Number number && (number.doubleValue() == 0.0 || number.doubleValue() == 1.0)) {
            return number.doubleValue() == 1.0;
        }
        if (value instanceof String text) {
            String lowered = text.strip().toLowerCase(Locale.ROOT);
            if (TRUE.contains(lowered)) {
                return true;
            }
            if (FALSE.contains(lowered)) {
                return false;
            }
        }
        problem(key, "Input should be a valid boolean");
        return fallback;
    }

    String string(String key, String fallback) {
        if (!data.containsKey(key)) {
            return fallback;
        }
        if (data.get(key) instanceof String text) {
            return text;
        }
        problem(key, "Input should be a valid string");
        return fallback;
    }

    String choice(String key, String fallback, List<String> allowed) {
        String value = string(key, fallback);
        if (!allowed.contains(value)) {
            problem(key, "Input should be " + describe(allowed));
            return fallback;
        }
        return value;
    }

    void problem(String key, String message) {
        problems.add(location(section, key) + message);
    }

    /** Run {@code build}, recording a validation failure instead of throwing it. */
    <T> T validated(String key, T fallback, java.util.function.Supplier<T> build) {
        try {
            return build.get();
        } catch (IllegalArgumentException exc) {
            problem(key, exc.getMessage());
            return fallback;
        }
    }

    static String describe(List<String> allowed) {
        List<String> quoted = allowed.stream().map(value -> "'" + value + "'").toList();
        if (quoted.size() == 1) {
            return quoted.getFirst();
        }
        return String.join(", ", quoted.subList(0, quoted.size() - 1)) + " or " + quoted.getLast();
    }

    private static String location(String section, String key) {
        String path = section.isEmpty() ? key : key.isEmpty() ? section : section + "." + key;
        return path.isEmpty() ? "" : path + ": ";
    }

    private static Optional<Long> wholeNumber(Object value) {
        return switch (value) {
            case Boolean flag -> Optional.of(flag ? 1L : 0L);
            case Integer number -> Optional.of(number.longValue());
            case Long number -> Optional.of(number);
            case Short number -> Optional.of(number.longValue());
            case Byte number -> Optional.of(number.longValue());
            case BigInteger number -> number.bitLength() < 64 ? Optional.of(number.longValue()) : Optional.empty();
            case Double number -> whole(number);
            case Float number -> whole(number.doubleValue());
            case BigDecimal number -> whole(number.doubleValue());
            case String text -> parse(text.strip());
            case null, default -> Optional.empty();
        };
    }

    private static Optional<Long> whole(double number) {
        if (Double.isFinite(number) && number == Math.rint(number) && Math.abs(number) < 9.2e18) {
            return Optional.of((long) number);
        }
        return Optional.empty();
    }

    private static Optional<Long> parse(String text) {
        try {
            return Optional.of(Long.parseLong(text.replace("_", "")));
        } catch (NumberFormatException exc) {
            return Optional.empty();
        }
    }
}
