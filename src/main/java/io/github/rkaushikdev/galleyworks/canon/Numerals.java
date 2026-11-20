package io.github.rkaushikdev.galleyworks.canon;

import io.github.rkaushikdev.galleyworks.CanonConfig;
import io.github.rkaushikdev.galleyworks.util.Regex;
import io.github.rkaushikdev.galleyworks.util.Text;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizing the quantities legislative text is full of.
 *
 * <p>Drafters write a quantity twice: {@code sixty (60) days}, {@code one hundred dollars
 * ($100.00)}, {@code five percent (5%)}. Two chambers pick different halves of that pair when they
 * re-typeset, and money is written with and without cents in the same act. Patterns are tried in
 * priority order and a later pattern is skipped where it would overlap an earlier match, so a
 * doubled amount of money is claimed once by the money rule rather than twice.
 */
public final class Numerals {

    /** Month names and their numbers. */
    public static final Map<String, Integer> MONTHS = months();

    private static final List<String> NUMBER_WORDS = List.of(("zero one two three four five six seven eight nine ten"
                    + " eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty thirty forty"
                    + " fifty sixty seventy eighty ninety hundred thousand million billion and")
            .split(" "));
    private static final String NUM_WORD = "(?:" + String.join("|", NUMBER_WORDS) + ")";
    private static final String WORDS = "\\b" + NUM_WORD + "(?:[\\s-]+" + NUM_WORD + ")*";

    private record NumeralPattern(String name, Pattern regex, Function<Matcher, String> render) {}

    private static final List<NumeralPattern> PATTERNS = List.of(
            new NumeralPattern(
                    "money-doubled",
                    Regex.ignoringCase(WORDS + "\\s+dollars?\\s*\\(\\s*\\$\\s*(?<amount>[\\d,]+(?:\\.\\d{2})?)\\s*\\)"),
                    Numerals::money),
            new NumeralPattern("money", Regex.compile("\\$\\s*(?<amount>[\\d,]+(?:\\.\\d{2})?)"), Numerals::money),
            new NumeralPattern(
                    "percent-doubled",
                    Regex.ignoringCase(
                            WORDS + "\\s+per\\s?cent(?:um)?\\s*\\(\\s*(?<amount>\\d+(?:\\.\\d+)?)\\s*%?\\s*\\)"),
                    Numerals::percent),
            new NumeralPattern(
                    "percent",
                    Regex.ignoringCase("(?<amount>\\d+(?:\\.\\d+)?)\\s*(?:%|per\\s?cent(?:um)?|percent)"),
                    Numerals::percent),
            new NumeralPattern(
                    "date",
                    Regex.ignoringCase("(?<month>" + String.join("|", MONTHS.keySet())
                            + ")\\s+(?<day>\\d{1,2}),?\\s+(?<year>\\d{4})"),
                    Numerals::date),
            new NumeralPattern(
                    "doubled-numeral",
                    Regex.ignoringCase(WORDS + "\\s*\\(\\s*(?<amount>[\\d,]+(?:\\.\\d+)?)\\s*\\)"),
                    match -> digits(match.group("amount"))),
            new NumeralPattern(
                    "ordinal",
                    Regex.ignoringCase("\\b(?<amount>\\d+)(?:st|nd|rd|th)\\b"),
                    match -> match.group("amount")));

    private Numerals() {}

    private static Map<String, Integer> months() {
        Map<String, Integer> months = new LinkedHashMap<>();
        String[] names = {
            "january", "february", "march", "april", "may", "june",
            "july", "august", "september", "october", "november", "december"
        };
        for (int i = 0; i < names.length; i++) {
            months.put(names[i], i + 1);
        }
        return Collections.unmodifiableMap(months);
    }

    private static String digits(String raw) {
        String cleaned = raw.replace(",", "");
        if (cleaned.endsWith(".00")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned;
    }

    private static String money(Matcher match) {
        return "usd " + digits(match.group("amount"));
    }

    private static String percent(Matcher match) {
        return digits(match.group("amount")) + " pct";
    }

    private static String date(Matcher match) {
        int month = MONTHS.get(Text.lower(match.group("month")));
        return String.format(
                Locale.ROOT,
                "date %04d-%02d-%02d",
                Integer.parseInt(match.group("year")),
                month,
                Integer.parseInt(match.group("day")));
    }

    /** Non-overlapping edits that put every quantity into one form, left to right. */
    public static List<Edit> normalizeNumerals(String text) {
        List<TextSpan> claimed = new ArrayList<>();
        List<Edit> found = new ArrayList<>();
        for (NumeralPattern pattern : PATTERNS) {
            Matcher match = pattern.regex().matcher(text);
            while (match.find()) {
                TextSpan span = new TextSpan(match.start(), match.end());
                if (claimed.stream().anyMatch(span::overlaps)) {
                    continue;
                }
                String replacement = pattern.render().apply(match);
                claimed.add(span);
                if (!replacement.equals(span.sliceOf(text))) {
                    found.add(new Edit(span, replacement));
                }
            }
        }
        found.sort(Comparator.comparingInt(edit -> edit.span().start()));
        return found;
    }

    /** Rewrites money, percentages, dates and doubled numerals. */
    public static final class NumeralRule implements Rule {

        @Override
        public String name() {
            return "numerals";
        }

        @Override
        public List<Edit> edits(String text, CanonConfig config) {
            if (!config.normalizeNumbers()) {
                return List.of();
            }
            return normalizeNumerals(text);
        }
    }
}
