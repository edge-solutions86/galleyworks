package io.github.rkaushikdev.galleyworks.util;

import java.util.regex.Pattern;

/**
 * Compiles the engine's text patterns with Unicode-aware classes.
 *
 * <p>Legislative text arrives in every script a legislature prints, so {@code \w}, {@code \d},
 * {@code \s} and {@code \b} have to mean their Unicode versions, a case-insensitive pattern has to
 * fold Unicode case, and only {@code \n} ends a line.
 */
public final class Regex {

    private static final int BASE = Pattern.UNICODE_CHARACTER_CLASS | Pattern.UNIX_LINES;

    private Regex() {}

    /** A case-sensitive pattern. */
    public static Pattern compile(String regex) {
        return Pattern.compile(regex, BASE);
    }

    /** A pattern that ignores case. */
    public static Pattern ignoringCase(String regex) {
        return Pattern.compile(regex, BASE | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }
}
