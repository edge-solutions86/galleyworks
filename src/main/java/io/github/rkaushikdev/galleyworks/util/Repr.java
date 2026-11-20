package io.github.rkaushikdev.galleyworks.util;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * The developer-facing rendering of a value, as error messages have always quoted it.
 *
 * <p>Error context renders as {@code key='value'}, so a string is quoted, a number is bare and an
 * absent value reads {@code None}. Those strings are what operators grep for in logs, so they keep
 * the shape the original engine gave them.
 */
public final class Repr {

    private Repr() {}

    /** Render {@code value}. */
    public static String of(Object value) {
        return switch (value) {
            case null -> "None";
            case Optional<?> optional -> optional.map(Repr::of).orElse("None");
            case String text -> quote(text);
            case Boolean flag -> flag ? "True" : "False";
            case Double number -> Floats.repr(number);
            case Path path -> quote(path.toString());
            case Enum<?> constant -> quote(constant.toString());
            case Map<?, ?> map -> mapping(map);
            case Collection<?> items -> sequence(items);
            default -> value.toString();
        };
    }

    /** Quote {@code text} with the quote character it does not contain, escaping the rest. */
    public static String quote(String text) {
        char quote = text.indexOf('\'') >= 0 && text.indexOf('"') < 0 ? '"' : '\'';
        StringBuilder out = new StringBuilder(text.length() + 2).append(quote);
        text.codePoints().forEach(point -> {
            switch (point) {
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (point == quote) {
                        out.append('\\').append((char) point);
                    } else if (point < 0x20 || point == 0x7f) {
                        out.append(String.format("\\x%02x", point));
                    } else {
                        out.appendCodePoint(point);
                    }
                }
            }
        });
        return out.append(quote).toString();
    }

    private static String sequence(Collection<?> items) {
        StringBuilder out = new StringBuilder("[");
        Iterator<?> iterator = items.iterator();
        while (iterator.hasNext()) {
            out.append(of(iterator.next()));
            if (iterator.hasNext()) {
                out.append(", ");
            }
        }
        return out.append(']').toString();
    }

    private static String mapping(Map<?, ?> map) {
        StringBuilder out = new StringBuilder("{");
        Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<?, ?> entry = iterator.next();
            out.append(of(entry.getKey())).append(": ").append(of(entry.getValue()));
            if (iterator.hasNext()) {
                out.append(", ");
            }
        }
        return out.append('}').toString();
    }
}
