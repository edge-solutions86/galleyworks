package io.github.rkaushikdev.galleyworks.canon;

import io.github.rkaushikdev.galleyworks.CanonConfig;
import io.github.rkaushikdev.galleyworks.util.Regex;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Possessives and optional hyphens: two forms that carry no legal difference and change on
 * re-typesetting.
 *
 * <p>What is folded is {@code board's} against {@code boards'} against {@code board s}, one word set
 * three ways by three typesetters, and {@code non-resident} against {@code nonresident}. The prefix
 * list is short and closed on purpose: folding every hyphen would join {@code water-right holder}
 * into a word that appears nowhere.
 */
public final class Morphology {

    /** The prefixes whose hyphen is optional. */
    public static final List<String> JOINED_PREFIXES = List.of(
            "anti", "co", "counter", "inter", "intra", "multi", "non", "post", "pre", "re", "semi", "sub", "super",
            "un");

    // The trailing guard is a negative lookahead rather than a word boundary: after a plural
    // possessive the apostrophe is the last character, and a boundary between two non-word
    // characters does not exist.
    private static final Pattern POSSESSIVE = Regex.compile("\\b([A-Za-z]{2,})['\u2019]s?(?![\\w'\u2019])");
    private static final Pattern PREFIX =
            Regex.ignoringCase("\\b(" + String.join("|", JOINED_PREFIXES) + ")[-\u2010\u2011]([a-z]{3,})\\b");

    private Morphology() {}

    /** Strip a possessive ending from {@code word}. */
    public static String foldPossessive(String word) {
        return POSSESSIVE.matcher(word).replaceAll(match -> Matcher.quoteReplacement(match.group(1)));
    }

    /** Drops possessive endings and closes hyphenated prefixes. */
    public static final class MorphologyRule implements Rule {

        @Override
        public String name() {
            return "morphology";
        }

        @Override
        public List<Edit> edits(String text, CanonConfig config) {
            if (!config.foldVariants()) {
                return List.of();
            }
            List<Edit> edits = new ArrayList<>();
            List<TextSpan> claimed = new ArrayList<>();
            Matcher possessive = POSSESSIVE.matcher(text);
            while (possessive.find()) {
                TextSpan span = new TextSpan(possessive.start(), possessive.end());
                claimed.add(span);
                edits.add(new Edit(span, possessive.group(1)));
            }
            Matcher prefix = PREFIX.matcher(text);
            while (prefix.find()) {
                TextSpan span = new TextSpan(prefix.start(), prefix.end());
                if (claimed.stream().anyMatch(span::overlaps)) {
                    continue;
                }
                edits.add(new Edit(span, prefix.group(1) + prefix.group(2)));
            }
            return edits;
        }
    }
}
