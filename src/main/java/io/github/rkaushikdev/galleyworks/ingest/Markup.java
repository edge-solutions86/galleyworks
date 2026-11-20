package io.github.rkaushikdev.galleyworks.ingest;

import io.github.rkaushikdev.galleyworks.Context;
import io.github.rkaushikdev.galleyworks.ParseError;
import io.github.rkaushikdev.galleyworks.util.OffsetMap;
import io.github.rkaushikdev.galleyworks.util.OffsetMapBuilder;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strike and insert markup used by amendment drafts.
 *
 * <p>Amendments are published as the section being amended with the removed words wrapped in
 * {@code {-...-}} and the added words in {@code {+...+}}. That single document describes two texts:
 * the current law to match against an existing corpus, and the proposed law to match against future
 * bills. Both are produced in one pass, each with an offset map back to the markup, so a passage
 * found in either can still be quoted with its markup intact.
 */
public final class Markup {

    private static final Pattern TAG =
            Pattern.compile("\\{-(?<struck>.*?)-\\}|\\{\\+(?<inserted>.*?)\\+\\}", Pattern.DOTALL);
    private static final Pattern OPENER = Pattern.compile("\\{[-+]");
    private static final int DELIMITER = 2;

    private Markup() {}

    /** Whether a marked edit removes or adds words. */
    public enum ChangeKind {
        STRIKE("strike"),
        INSERT("insert");

        private final String value;

        ChangeKind(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    /**
     * One marked edit, located in the markup.
     *
     * @param kind strike or insert
     * @param text the words between the delimiters
     * @param span the whole tag, delimiters included
     */
    public record Change(ChangeKind kind, String text, TextSpan span) {

        /** True for a strike. */
        public boolean isStrike() {
            return kind == ChangeKind.STRIKE;
        }
    }

    /**
     * The two readings of one marked-up passage.
     *
     * @param markup the text as published
     * @param current the law as it stands, struck words kept and inserts dropped
     * @param proposed the law as amended, inserts kept and struck words dropped
     * @param changes every marked edit, in order
     * @param currentMap translates offsets in {@code current} back to the markup
     * @param proposedMap translates offsets in {@code proposed} back to the markup
     */
    public record MarkedText(
            String markup,
            String current,
            String proposed,
            List<Change> changes,
            OffsetMap currentMap,
            OffsetMap proposedMap) {

        /** Copies the changes. */
        public MarkedText {
            changes = List.copyOf(changes);
        }

        /** True when the passage marks at least one edit. */
        public boolean isAmendment() {
            return !changes.isEmpty();
        }

        /** The strikes, in order. */
        public List<Change> struck() {
            return changes.stream().filter(Change::isStrike).toList();
        }

        /** The inserts, in order. */
        public List<Change> inserted() {
            return changes.stream().filter(change -> !change.isStrike()).toList();
        }
    }

    /** True when the text carries at least one well-formed tag. */
    public static boolean hasMarkup(String text) {
        return TAG.matcher(text).find();
    }

    /**
     * Split marked-up text into its current and proposed readings.
     *
     * @throws ParseError when a tag is opened and never closed
     */
    public static MarkedText parseMarkup(String text) {
        StringBuilder current = new StringBuilder();
        StringBuilder proposed = new StringBuilder();
        List<Change> changes = new ArrayList<>();
        OffsetMapBuilder currentMap = new OffsetMapBuilder();
        OffsetMapBuilder proposedMap = new OffsetMapBuilder();
        int cursor = 0;
        Matcher match = TAG.matcher(text);
        while (match.find()) {
            guardStrayOpeners(text, cursor, match.start());
            String literal = text.substring(cursor, match.start());
            if (!literal.isEmpty()) {
                current.append(literal);
                proposed.append(literal);
                currentMap.keep(literal.length());
                proposedMap.keep(literal.length());
            }
            TextSpan span = new TextSpan(match.start(), match.end());
            String struck = match.group("struck");
            if (struck != null) {
                changes.add(new Change(ChangeKind.STRIKE, struck, span));
                current.append(struck);
                keepInsideTag(currentMap, struck.length());
                proposedMap.drop(span.length());
            } else {
                String inserted = match.group("inserted");
                changes.add(new Change(ChangeKind.INSERT, inserted, span));
                proposed.append(inserted);
                keepInsideTag(proposedMap, inserted.length());
                currentMap.drop(span.length());
            }
            cursor = match.end();
        }
        guardStrayOpeners(text, cursor, text.length());
        String tail = text.substring(cursor);
        if (!tail.isEmpty()) {
            current.append(tail);
            proposed.append(tail);
            currentMap.keep(tail.length());
            proposedMap.keep(tail.length());
        }
        return new MarkedText(
                text,
                current.toString(),
                proposed.toString(),
                changes,
                currentMap.build(text.length()),
                proposedMap.build(text.length()));
    }

    /** Drop the two-character delimiters and keep the words between them aligned. */
    private static void keepInsideTag(OffsetMapBuilder builder, int length) {
        builder.drop(DELIMITER);
        builder.keep(length);
        builder.drop(DELIMITER);
    }

    private static void guardStrayOpeners(String text, int start, int end) {
        Matcher stray = OPENER.matcher(text).region(start, end);
        if (stray.find()) {
            int at = stray.start();
            int line =
                    (int) text.substring(0, at).chars().filter(c -> c == '\n').count() + 1;
            int column = at - text.lastIndexOf('\n', at - 1) - 1;
            throw new ParseError(
                    "amendment markup has an unclosed tag",
                    line,
                    column,
                    Context.of("tag", text.substring(at, at + 2)));
        }
    }
}
