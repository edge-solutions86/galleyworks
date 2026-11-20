package io.github.rkaushikdev.galleyworks.util;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A JSON encoder whose output is byte-for-byte the format the engine has always written.
 *
 * <p>The JSON report, the {@code --json} views and the log lines are consumed by scripts and diffed
 * by people, and the engine digest is a hash of a JSON blob, so the spacing, the key order and the
 * escaping are part of the contract. Values are plain Java: {@link Map} (keys rendered with {@code
 * String.valueOf}), {@link Collection} or array, {@link String}, {@link Number}, {@link Boolean},
 * {@link Optional}, {@link Enum} (its {@code toString}) and {@code null}. Anything else is rendered
 * through {@code toString} when the style allows it and refused otherwise.
 */
public final class Json {

    private Json() {}

    /**
     * How a document is laid out.
     *
     * @param indent spaces per nesting level, or negative for a single line
     * @param sortKeys whether object keys are written in sorted order
     * @param itemSeparator between array items and object members
     * @param keySeparator between an object key and its value
     * @param ensureAscii whether every non-ASCII character is written as an escape
     * @param stringifyUnknown whether an unknown type is written as its {@code toString}
     */
    public record Style(
            int indent,
            boolean sortKeys,
            String itemSeparator,
            String keySeparator,
            boolean ensureAscii,
            boolean stringifyUnknown) {

        /** One line, {@code ", "} and {@code ": "}, insertion order, ASCII only. */
        public static Style compact() {
            return new Style(-1, false, ", ", ": ", true, false);
        }

        /** Two-space indentation, insertion order, ASCII only. */
        public static Style indented() {
            return new Style(2, false, ",", ": ", true, false);
        }

        /** This style with sorted keys. */
        public Style sorted() {
            return new Style(indent, true, itemSeparator, keySeparator, ensureAscii, stringifyUnknown);
        }

        /** This style with the given separators. */
        public Style separators(String item, String key) {
            return new Style(indent, sortKeys, item, key, ensureAscii, stringifyUnknown);
        }

        /** This style writing non-ASCII characters as they are. */
        public Style unicode() {
            return new Style(indent, sortKeys, itemSeparator, keySeparator, false, stringifyUnknown);
        }

        /** This style rendering unknown types through {@code toString}. */
        public Style stringifying() {
            return new Style(indent, sortKeys, itemSeparator, keySeparator, ensureAscii, true);
        }
    }

    /** Encode {@code value} on one line with the default separators. */
    public static String write(Object value) {
        return write(value, Style.compact());
    }

    /** Encode {@code value} in {@code style}. */
    public static String write(Object value, Style style) {
        StringBuilder out = new StringBuilder();
        encode(value, style, 0, out);
        return out.toString();
    }

    /** Encode one string as a JSON string literal. */
    public static String quote(String text, boolean ensureAscii) {
        StringBuilder out = new StringBuilder(text.length() + 2);
        string(text, ensureAscii, out);
        return out.toString();
    }

    private static void encode(Object value, Style style, int depth, StringBuilder out) {
        switch (value) {
            case null -> out.append("null");
            case Optional<?> optional -> encode(optional.orElse(null), style, depth, out);
            case String text -> string(text, style.ensureAscii(), out);
            case Boolean flag -> out.append(flag ? "true" : "false");
            case Double number -> out.append(floating(number));
            case Float number -> out.append(floating(number.doubleValue()));
            case Integer number -> out.append(number);
            case Long number -> out.append(number);
            case Short number -> out.append(number);
            case Byte number -> out.append(number);
            case BigInteger number -> out.append(number);
            case BigDecimal number -> out.append(floating(number.doubleValue()));
            case Enum<?> constant -> string(constant.toString(), style.ensureAscii(), out);
            case Map<?, ?> map -> object(map, style, depth, out);
            case Collection<?> items -> array(items, style, depth, out);
            default -> {
                if (value.getClass().isArray()) {
                    List<Object> items = new ArrayList<>();
                    for (int i = 0; i < Array.getLength(value); i++) {
                        items.add(Array.get(value, i));
                    }
                    array(items, style, depth, out);
                } else if (style.stringifyUnknown()) {
                    string(value.toString(), style.ensureAscii(), out);
                } else {
                    throw new IllegalArgumentException(
                            "Object of type " + value.getClass().getSimpleName() + " is not JSON serializable");
                }
            }
        }
    }

    private static String floating(double number) {
        if (Double.isNaN(number)) {
            return "NaN";
        }
        if (Double.isInfinite(number)) {
            return number > 0 ? "Infinity" : "-Infinity";
        }
        return Floats.repr(number);
    }

    private static void object(Map<?, ?> map, Style style, int depth, StringBuilder out) {
        if (map.isEmpty()) {
            out.append("{}");
            return;
        }
        List<Map.Entry<String, Object>> entries = new ArrayList<>();
        map.forEach((key, item) -> entries.add(Map.entry(String.valueOf(key), nullSafe(item))));
        if (style.sortKeys()) {
            entries.sort(Map.Entry.comparingByKey());
        }
        out.append('{');
        Iterator<Map.Entry<String, Object>> iterator = entries.iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();
            newline(style, depth + 1, out);
            string(entry.getKey(), style.ensureAscii(), out);
            out.append(style.keySeparator());
            encode(unwrap(entry.getValue()), style, depth + 1, out);
            if (iterator.hasNext()) {
                out.append(style.itemSeparator());
            }
        }
        newline(style, depth, out);
        out.append('}');
    }

    private static void array(Collection<?> items, Style style, int depth, StringBuilder out) {
        if (items.isEmpty()) {
            out.append("[]");
            return;
        }
        out.append('[');
        Iterator<?> iterator = items.iterator();
        while (iterator.hasNext()) {
            newline(style, depth + 1, out);
            encode(iterator.next(), style, depth + 1, out);
            if (iterator.hasNext()) {
                out.append(style.itemSeparator());
            }
        }
        newline(style, depth, out);
        out.append(']');
    }

    private static void newline(Style style, int depth, StringBuilder out) {
        if (style.indent() >= 0) {
            out.append('\n').append(" ".repeat(style.indent() * depth));
        }
    }

    private static Object nullSafe(Object value) {
        return value == null ? Optional.empty() : value;
    }

    private static Object unwrap(Object value) {
        return value instanceof Optional<?> optional ? optional.orElse(null) : value;
    }

    private static void string(String text, boolean ensureAscii, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20 || (ensureAscii && c > 0x7e)) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
