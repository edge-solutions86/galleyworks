package io.github.rkaushikdev.galleyworks.ingest;

import io.github.rkaushikdev.galleyworks.Context;
import io.github.rkaushikdev.galleyworks.ParseError;
import io.github.rkaushikdev.galleyworks.util.Text;
import java.util.Map;

/**
 * Label sequences, and the rule that resolves {@code (i)}.
 *
 * <p>Legislative drafting nests labels alphabetically then in lower roman: {@code (a)}, {@code
 * (b)} ... then {@code (i)}, {@code (ii)}. Seven labels are legal in both alphabets, and {@code (i)}
 * is the one that actually happens, because it is both the ninth letter and roman one. What
 * resolves it is the sequence already open, which is why the successors live here.
 */
public final class Labels {

    /** The alphabetic label letters, in order. */
    public static final String ALPHA = "abcdefghijklmnopqrstuvwxyz";

    /** The value of each roman digit. */
    public static final Map<Character, Integer> ROMAN_VALUES =
            Map.of('i', 1, 'v', 5, 'x', 10, 'l', 50, 'c', 100, 'd', 500, 'm', 1000);

    private static final int[] MAGNITUDES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] GLYPHS = {"m", "cm", "d", "cd", "c", "xc", "l", "xl", "x", "ix", "v", "iv", "i"};

    private Labels() {}

    /** The label that follows {@code label} in the {@code a, b, ... z, aa, bb} sequence. */
    public static String alphaSuccessor(String label) {
        String lowered = Text.lower(label);
        if (lowered.isEmpty() || lowered.chars().anyMatch(c -> ALPHA.indexOf(c) < 0)) {
            throw new ParseError("not an alphabetic label", 0, 0, Context.of("label", label));
        }
        if (lowered.chars().distinct().count() != 1) {
            throw new ParseError("alphabetic labels repeat a single letter", 0, 0, Context.of("label", label));
        }
        char letter = lowered.charAt(0);
        if (letter == 'z') {
            return "a".repeat(lowered.length() + 1);
        }
        return String.valueOf(ALPHA.charAt(ALPHA.indexOf(letter) + 1)).repeat(lowered.length());
    }

    /** True when every character is a roman digit. */
    public static boolean isRoman(String label) {
        String lowered = Text.lower(label);
        return !lowered.isEmpty() && lowered.chars().allMatch(c -> ROMAN_VALUES.containsKey((char) c));
    }

    /** Decode a lower roman label using subtractive notation; only canonical forms are accepted. */
    public static int romanValue(String label) {
        String lowered = Text.lower(label);
        if (!isRoman(lowered)) {
            throw new ParseError("not a roman label", 0, 0, Context.of("label", label));
        }
        int total = 0;
        int previous = 0;
        for (int i = lowered.length() - 1; i >= 0; i--) {
            int current = ROMAN_VALUES.get(lowered.charAt(i));
            if (current < previous) {
                total -= current;
            } else {
                total += current;
                previous = current;
            }
        }
        if (!toRoman(total).equals(lowered)) {
            throw new ParseError("roman label is not in canonical form", 0, 0, Context.of("label", label));
        }
        return total;
    }

    /** Encode a positive integer as a lower roman label. */
    public static String toRoman(int value) {
        if (value <= 0) {
            throw new ParseError("roman labels start at one", 0, 0, Context.of("value", value));
        }
        StringBuilder out = new StringBuilder();
        int remaining = value;
        for (int i = 0; i < MAGNITUDES.length; i++) {
            while (remaining >= MAGNITUDES[i]) {
                out.append(GLYPHS[i]);
                remaining -= MAGNITUDES[i];
            }
        }
        return out.toString();
    }

    /** The roman label that follows {@code label}. */
    public static String romanSuccessor(String label) {
        return toRoman(romanValue(label) + 1);
    }
}
