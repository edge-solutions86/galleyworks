package io.github.rkaushikdev.galleyworks.canon;

import io.github.rkaushikdev.galleyworks.CanonConfig;
import io.github.rkaushikdev.galleyworks.util.Regex;
import io.github.rkaushikdev.galleyworks.util.Text;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The three rules that run before anything looks at meaning: Unicode folding, whitespace and case.
 *
 * <p>Typesetting differences are the largest single source of false negatives in a reuse corpus:
 * one chamber publishes curly quotes and an en dash, another publishes ASCII, and the same sentence
 * fingerprints differently. Case folding comes last of the three in the pipeline, because the
 * citation and enumeration rules that run in between match on capitalisation.
 */
public final class TextRules {

    /** Typographic punctuation and the ASCII it folds to. */
    public static final Map<String, String> PUNCTUATION_FOLD = punctuationFold();

    private static final Pattern WHITESPACE_RUN = Regex.compile(Text.SPACE_CLASS + "{2,}|[\\t\\r\\f\\x0B\\n]");

    private TextRules() {}

    private static Map<String, String> punctuationFold() {
        Map<String, String> fold = new LinkedHashMap<>();
        fold.put("‘", "'");
        fold.put("’", "'");
        fold.put("‚", "'");
        fold.put("“", "\"");
        fold.put("”", "\"");
        fold.put("„", "\"");
        fold.put("‐", "-");
        fold.put("‑", "-");
        fold.put("‒", "-");
        fold.put("–", "-");
        fold.put("—", "-");
        fold.put("―", "-");
        fold.put("…", "...");
        fold.put(" ", " ");
        fold.put(" ", " ");
        fold.put(" ", " ");
        fold.put("﻿", "");
        fold.put("­", "");
        return java.util.Collections.unmodifiableMap(fold);
    }

    /**
     * One edit per maximal run where {@code folded} differs from {@code text}.
     *
     * <p>{@code folded} holds one replacement string per source code point, so a fold that
     * lengthens or drops characters still lines up with its source offsets.
     */
    public static List<Edit> runsWhereChanged(String text, List<String> folded) {
        List<Edit> edits = new ArrayList<>();
        int offset = 0;
        int start = -1;
        int startIndex = 0;
        int index = 0;
        while (offset < text.length()) {
            int point = text.codePointAt(offset);
            boolean changed = !folded.get(index).equals(Character.toString(point));
            if (changed && start < 0) {
                start = offset;
                startIndex = index;
            } else if (!changed && start >= 0) {
                edits.add(new Edit(new TextSpan(start, offset), String.join("", folded.subList(startIndex, index))));
                start = -1;
            }
            offset += Character.charCount(point);
            index++;
        }
        if (start >= 0) {
            edits.add(new Edit(
                    new TextSpan(start, text.length()), String.join("", folded.subList(startIndex, folded.size()))));
        }
        return edits;
    }

    /** Normalizes compatibility forms and typographic punctuation to ASCII. */
    public static final class UnicodeFoldRule implements Rule {

        @Override
        public String name() {
            return "unicode-fold";
        }

        @Override
        public List<Edit> edits(String text, CanonConfig config) {
            if (!config.foldUnicode()) {
                return List.of();
            }
            List<String> folded =
                    text.codePoints().mapToObj(UnicodeFoldRule::fold).toList();
            return runsWhereChanged(text, folded);
        }

        private static String fold(int point) {
            String character = Character.toString(point);
            String mapped = PUNCTUATION_FOLD.get(character);
            if (mapped != null) {
                return mapped;
            }
            String decomposed = Normalizer.normalize(character, Normalizer.Form.NFKC);
            return Text.isPrintable(decomposed) || Text.isSpace(decomposed) ? decomposed : character;
        }
    }

    /** Collapses every run of whitespace to a single space. */
    public static final class WhitespaceRule implements Rule {

        @Override
        public String name() {
            return "whitespace";
        }

        @Override
        public List<Edit> edits(String text, CanonConfig config) {
            List<Edit> edits = new ArrayList<>();
            Matcher match = WHITESPACE_RUN.matcher(text);
            while (match.find()) {
                TextSpan span = new TextSpan(match.start(), match.end());
                if (span.length() == 1 && text.charAt(span.start()) == ' ') {
                    continue;
                }
                edits.add(new Edit(span, " "));
            }
            return edits;
        }
    }

    /** Lowercases the text, keeping every offset accounted for. */
    public static final class CaseFoldRule implements Rule {

        @Override
        public String name() {
            return "case-fold";
        }

        @Override
        public List<Edit> edits(String text, CanonConfig config) {
            if (!config.foldCase()) {
                return List.of();
            }
            List<String> folded = text.codePoints()
                    .mapToObj(point -> Text.lower(Character.toString(point)))
                    .toList();
            return runsWhereChanged(text, folded);
        }
    }
}
