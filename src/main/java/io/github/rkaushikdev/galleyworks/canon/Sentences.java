package io.github.rkaushikdev.galleyworks.canon;

import io.github.rkaushikdev.galleyworks.util.Regex;
import io.github.rkaushikdev.galleyworks.util.Text;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sentence segmentation for legislative prose.
 *
 * <p>Ordinary sentence splitters cut {@code W.S. 41-3-101} into three sentences and {@code No.
 * 1188} into two, so the splitter carries an explicit abbreviation list and refuses to break after
 * one. A statutory paragraph also ends on a semicolon followed by {@code and} or {@code or}; that is
 * a boundary when asked for, but off by default since running prose uses the same punctuation
 * differently.
 */
public final class Sentences {

    /** Abbreviations a period does not end a sentence after. */
    public static final Set<String> ABBREVIATIONS = Set.of(
            "art", "ch", "cf", "co", "corp", "dept", "div", "e.g", "et al", "et seq", "ex", "fig", "i.e", "inc", "no",
            "para", "pt", "sec", "seq", "subd", "subsec", "supp", "u.s.c", "v", "viz", "vol", "w.s");

    private static final Pattern TERMINATOR = Regex.compile("[.?!]");
    private static final Pattern SEMICOLON_CLAUSE = Regex.ignoringCase(";\\s+(?:and|or)\\b");
    private static final Pattern WORD_BEFORE = Regex.compile("([A-Za-z][A-Za-z.]*)\\s*$");
    private static final Pattern SINGLE_INITIAL = Regex.compile("^[A-Za-z]$");
    private static final String CLOSERS = ")\"'”’";
    private static final String OPENERS = "(\"'“‘";

    private Sentences() {}

    private static boolean isAbbreviation(String text, int dot) {
        Matcher match = WORD_BEFORE.matcher(text).region(0, dot);
        if (!match.find()) {
            return false;
        }
        String word = Text.lower(match.group(1));
        int end = word.length();
        while (end > 0 && word.charAt(end - 1) == '.') {
            end--;
        }
        word = word.substring(0, end);
        if (SINGLE_INITIAL.matcher(word).find()) {
            return true;
        }
        return ABBREVIATIONS.contains(word);
    }

    private static boolean startsNewSentence(String text, int position) {
        int cursor = position;
        while (cursor < text.length() && Text.isSpace(text.charAt(cursor))) {
            cursor++;
        }
        if (cursor >= text.length()) {
            return true;
        }
        int point = text.codePointAt(cursor);
        return Text.isUpper(point) || Text.isDigit(point) || OPENERS.indexOf(point) >= 0;
    }

    /** The sentence spans of {@code text}. */
    public static List<TextSpan> splitSentences(String text) {
        return splitSentences(text, 0, false);
    }

    /** The sentence spans of {@code text}, shifted by {@code offset}, optionally breaking clauses. */
    public static List<TextSpan> splitSentences(String text, int offset, boolean breakOnClause) {
        List<Integer> boundaries = new ArrayList<>(boundaries(text, breakOnClause));
        boundaries.add(text.length());
        List<TextSpan> spans = new ArrayList<>();
        int start = 0;
        for (int boundary : boundaries) {
            TextSpan piece = trim(text, start, boundary);
            if (piece != null) {
                spans.add(piece.shift(offset));
            }
            start = boundary;
        }
        return spans;
    }

    private static Set<Integer> boundaries(String text, boolean breakOnClause) {
        Set<Integer> found = new TreeSet<>();
        Matcher match = TERMINATOR.matcher(text);
        while (match.find()) {
            int dot = match.start();
            if (text.charAt(dot) == '.' && isAbbreviation(text, dot)) {
                continue;
            }
            int end = match.end();
            while (end < text.length() && CLOSERS.indexOf(text.charAt(end)) >= 0) {
                end++;
            }
            if (end >= text.length()) {
                continue;
            }
            if (!Text.isSpace(text.charAt(end))) {
                continue;
            }
            if (startsNewSentence(text, end)) {
                found.add(end);
            }
        }
        if (breakOnClause) {
            Matcher clause = SEMICOLON_CLAUSE.matcher(text);
            while (clause.find()) {
                found.add(clause.start() + 1);
            }
        }
        return found;
    }

    private static TextSpan trim(String text, int from, int to) {
        int start = from;
        int end = to;
        while (start < end && Text.isSpace(text.charAt(start))) {
            start++;
        }
        while (end > start && Text.isSpace(text.charAt(end - 1))) {
            end--;
        }
        if (start >= end) {
            return null;
        }
        return new TextSpan(start, end);
    }
}
