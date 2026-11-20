package io.github.rkaushikdev.galleyworks.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Character classification with the semantics the engine's text rules were written against.
 *
 * <p>Whitespace here is the full Unicode set the original matched (including the information
 * separators {@code U+001C}..{@code U+001F} and the non-breaking spaces), and a character is
 * printable unless it is a control, format, private-use, unassigned or separator character other
 * than the ASCII space.
 */
public final class Text {

    /** A regex character class matching exactly the code points {@link #isSpace(int)} accepts. */
    public static final String SPACE_CLASS =
            "[\\t\\n\\x0B\\f\\r\\x1C-\\x1F \\x85\\xA0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000]";

    private Text() {}

    /** True for every code point the engine treats as whitespace. */
    public static boolean isSpace(int point) {
        return switch (point) {
            case '\t',
                    '\n',
                    0x0B,
                    '\f',
                    '\r',
                    0x1C,
                    0x1D,
                    0x1E,
                    0x1F,
                    ' ',
                    0x85,
                    0xA0,
                    0x1680,
                    0x2028,
                    0x2029,
                    0x202F,
                    0x205F,
                    0x3000 -> true;
            default -> point >= 0x2000 && point <= 0x200A;
        };
    }

    /** True when {@code text} is non-empty and every code point in it is whitespace. */
    public static boolean isSpace(String text) {
        return !text.isEmpty() && text.codePoints().allMatch(Text::isSpace);
    }

    /** True when every code point of {@code text} is printable; the empty string is printable. */
    public static boolean isPrintable(String text) {
        return text.codePoints().allMatch(Text::isPrintable);
    }

    /** True unless {@code point} is a control, format, surrogate, private, unassigned or separator. */
    public static boolean isPrintable(int point) {
        if (point == ' ') {
            return true;
        }
        return switch (Character.getType(point)) {
            case Character.CONTROL,
                    Character.FORMAT,
                    Character.SURROGATE,
                    Character.PRIVATE_USE,
                    Character.UNASSIGNED,
                    Character.LINE_SEPARATOR,
                    Character.PARAGRAPH_SEPARATOR,
                    Character.SPACE_SEPARATOR -> false;
            default -> true;
        };
    }

    /** True for an uppercase code point. */
    public static boolean isUpper(int point) {
        return Character.isUpperCase(point);
    }

    /** True for a digit, including the superscript and circled digits. */
    public static boolean isDigit(int point) {
        if (Character.isDigit(point)) {
            return true;
        }
        int value = Character.getNumericValue(point);
        return Character.getType(point) == Character.OTHER_NUMBER && value >= 0 && value <= 9;
    }

    /** True when {@code text} is non-empty and made only of letters. */
    public static boolean isAlpha(String text) {
        return !text.isEmpty() && text.codePoints().allMatch(Character::isLetter);
    }

    /** {@code text} lowercased without reference to the machine's locale. */
    public static String lower(String text) {
        return text.toLowerCase(Locale.ROOT);
    }

    /** {@code text} uppercased without reference to the machine's locale. */
    public static String upper(String text) {
        return text.toUpperCase(Locale.ROOT);
    }

    /** {@code text} with leading and trailing whitespace removed. */
    public static String strip(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isSpace(text.charAt(start))) {
            start++;
        }
        while (end > start && isSpace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }

    /** {@code text} with trailing whitespace removed. */
    public static String rstrip(String text) {
        int end = text.length();
        while (end > 0 && isSpace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }

    /** The whitespace-separated words of {@code text}, with no empty strings. */
    public static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        int start = -1;
        for (int i = 0; i < text.length(); i++) {
            boolean space = isSpace(text.charAt(i));
            if (space && start >= 0) {
                words.add(text.substring(start, i));
                start = -1;
            } else if (!space && start < 0) {
                start = i;
            }
        }
        if (start >= 0) {
            words.add(text.substring(start));
        }
        return words;
    }

    /** {@code text} with every whitespace run collapsed to one space and the ends trimmed. */
    public static String squash(String text) {
        return String.join(" ", words(text));
    }
}
