package io.github.rkaushikdev.galleyworks.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formatting, rounding and parsing of doubles with the exact results the report formats pin.
 *
 * <p>Reports, the engine digest and the JSON formats are compared byte for byte, so a score has
 * to print the same digits on every run. {@link String#format} rounds half-up from the shortest
 * decimal representation; these round half-even from the exact binary value, and print the
 * shortest round-tripping representation with fixed rules for when to switch to an exponent.
 */
public final class Floats {

    private static final int REPR_LOW_EXPONENT = -4;
    private static final int REPR_HIGH_EXPONENT = 16;

    private static final String DIGITS = "[0-9](?:_?[0-9])*";
    private static final Pattern NUMBER = Pattern.compile(
            "[+-]?(?:" + DIGITS + "(?:\\.(?:" + DIGITS + ")?)?|\\." + DIGITS + ")(?:[eE][+-]?" + DIGITS + ")?");
    private static final Pattern SPECIAL = Pattern.compile("([+-]?)(inf|infinity|nan)", Pattern.CASE_INSENSITIVE);

    private Floats() {}

    /**
     * Read {@code text} as a float the way the command line accepts one: surrounding whitespace
     * is ignored, any decimal digit counts, single underscores may separate digits, and {@code inf},
     * {@code infinity} and {@code nan} are accepted in any case.
     *
     * @throws NumberFormatException when {@code text} is not a float
     */
    public static double parse(String text) {
        StringBuilder ascii = new StringBuilder();
        Text.strip(text).codePoints().forEach(point -> {
            if (Character.isDigit(point)) {
                ascii.append((char) ('0' + Character.digit(point, 10)));
            } else {
                ascii.appendCodePoint(point);
            }
        });
        String cleaned = ascii.toString();
        Matcher special = SPECIAL.matcher(cleaned);
        if (special.matches()) {
            boolean negative = special.group(1).equals("-");
            if (Text.lower(special.group(2)).equals("nan")) {
                return Double.NaN;
            }
            return negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        }
        if (!NUMBER.matcher(cleaned).matches()) {
            throw new NumberFormatException("could not convert string to float: " + Repr.quote(text));
        }
        return Double.parseDouble(cleaned.replace("_", ""));
    }

    /** {@code value} with exactly {@code places} decimals, rounded half-even from its exact value. */
    public static String fixed(double value, int places) {
        if (Double.isNaN(value)) {
            return "nan";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "inf" : "-inf";
        }
        BigDecimal rounded = new BigDecimal(value).setScale(places, RoundingMode.HALF_EVEN);
        String text = rounded.toPlainString();
        boolean negative = value < 0 || (value == 0.0 && 1.0 / value < 0);
        if (negative && rounded.signum() == 0) {
            return "-" + text;
        }
        return text;
    }

    /** {@code value} as a percentage with {@code places} decimals, such as {@code 42.5%}. */
    public static String percent(double value, int places) {
        return fixed(value * 100.0, places) + "%";
    }

    /** {@code value} rounded to {@code places} decimals, half-even from its exact binary value. */
    public static double round(double value, int places) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return value;
        }
        return new BigDecimal(value).setScale(places, RoundingMode.HALF_EVEN).doubleValue();
    }

    /**
     * The shortest representation that reads back as {@code value}, written the way the reports
     * print floats: {@code 1.0}, {@code 0.0001}, {@code 1e-05}, {@code 1e+16}.
     */
    public static String repr(double value) {
        if (Double.isNaN(value)) {
            return "nan";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "inf" : "-inf";
        }
        String sign = value < 0 || (value == 0.0 && 1.0 / value < 0) ? "-" : "";
        double magnitude = Math.abs(value);
        if (magnitude == 0.0) {
            return sign + "0.0";
        }
        BigDecimal exact = new BigDecimal(Double.toString(magnitude)).stripTrailingZeros();
        String digits = exact.unscaledValue().toString();
        int point = digits.length() - exact.scale();
        if (point <= REPR_LOW_EXPONENT || point > REPR_HIGH_EXPONENT) {
            return sign + exponential(digits, point - 1);
        }
        if (point <= 0) {
            return sign + "0." + "0".repeat(-point) + digits;
        }
        if (point < digits.length()) {
            return sign + digits.substring(0, point) + "." + digits.substring(point);
        }
        return sign + digits + "0".repeat(point - digits.length()) + ".0";
    }

    private static String exponential(String digits, int exponent) {
        StringBuilder out = new StringBuilder().append(digits.charAt(0));
        if (digits.length() > 1) {
            out.append('.').append(digits, 1, digits.length());
        }
        out.append('e').append(exponent < 0 ? '-' : '+');
        int magnitude = Math.abs(exponent);
        if (magnitude < 10) {
            out.append('0');
        }
        return out.append(magnitude).toString();
    }
}
