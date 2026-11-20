package io.github.rkaushikdev.galleyworks.ingest;

import io.github.rkaushikdev.galleyworks.util.Regex;
import io.github.rkaushikdev.galleyworks.util.Text;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Line lexer for enrolled bill text.
 *
 * <p>Enrolled text is line oriented and the structure lives in the left margin: a section opener,
 * a statute catchline, and parenthesised labels that nest. The lexer only classifies lines and
 * records their spans; deciding what nests inside what is the parser's job, because that decision
 * needs lookahead the lexer does not have. The awkward case is {@code (i)}, the ninth subsection
 * label and the first roman paragraph label: the lexer reports it as ambiguous and refuses to
 * guess.
 */
public final class EnrolledLex {

    private static final Pattern SECTION = Regex.ignoringCase("^Section\\s+(?<label>\\d+)\\s*\\.\\s*");
    private static final Pattern CATCHLINE = Regex.compile("^(?<label>\\d+(?:-\\w+)+)\\s*\\.\\s+\\S");
    private static final Pattern LABEL = Regex.compile("^\\((?<label>[A-Za-z0-9]{1,6})\\)\\s*");
    private static final Pattern TITLE = Regex.compile("^AN ACT\\b");
    private static final Pattern ENACTING = Regex.ignoringCase("^Be It Enacted\\b");

    private static final Pattern ROMAN_ONLY = Regex.compile("^[ivxlcdm]+$");
    private static final Pattern LOWER_ALPHA = Regex.compile("^[a-z]{1,3}$");
    private static final Pattern UPPER_ALPHA = Regex.compile("^[A-Z]{1,3}$");
    private static final Pattern DIGITS = Regex.compile("^\\d{1,4}$");
    private static final Set<String> AMBIGUOUS_LABELS = Set.of("i", "v", "x", "l", "c", "d", "m");

    private EnrolledLex() {}

    /** How a source line reads. */
    public enum LineKind {
        BLANK("blank"),
        TITLE("title"),
        ENACTING("enacting"),
        SECTION("section"),
        CATCHLINE("catchline"),
        SUBSECTION("subsection"),
        AMBIGUOUS_LABEL("ambiguous_label"),
        PARAGRAPH("paragraph"),
        SUBPARAGRAPH("subparagraph"),
        ITEM("item"),
        TEXT("text");

        private static final Set<LineKind> LABELLED =
                EnumSet.of(SECTION, SUBSECTION, AMBIGUOUS_LABEL, PARAGRAPH, SUBPARAGRAPH, ITEM);

        private final String value;

        LineKind(String value) {
            this.value = value;
        }

        /** True for the kinds that carry a structural label. */
        public boolean isLabelled() {
            return LABELLED.contains(this);
        }

        @Override
        public String toString() {
            return value;
        }
    }

    /**
     * One classified source line.
     *
     * @param number the 1-based line number
     * @param kind how the line reads
     * @param span the whole line, without its newline
     * @param indent leading whitespace characters
     * @param label the structural label, or empty
     * @param body the text after the label, for a labelled line
     */
    public record LexLine(int number, LineKind kind, TextSpan span, int indent, String label, Optional<TextSpan> body) {

        LexLine(int number, LineKind kind, TextSpan span, int indent) {
            this(number, kind, span, indent, "", Optional.empty());
        }

        /** True when the line carries a structural label. */
        public boolean hasLabel() {
            return kind.isLabelled();
        }
    }

    private static LineKind classifyLabel(String label) {
        if (DIGITS.matcher(label).matches()) {
            return LineKind.ITEM;
        }
        if (UPPER_ALPHA.matcher(label).matches()) {
            return LineKind.SUBPARAGRAPH;
        }
        if (!LOWER_ALPHA.matcher(label).matches()) {
            return LineKind.TEXT;
        }
        if (AMBIGUOUS_LABELS.contains(label)) {
            return LineKind.AMBIGUOUS_LABEL;
        }
        if (ROMAN_ONLY.matcher(label).matches()) {
            return LineKind.PARAGRAPH;
        }
        return LineKind.SUBSECTION;
    }

    /**
     * Classify every line of {@code text}, keeping spans into the original.
     *
     * <p>A parenthesised label only counts when it opens a block, that is, at the top of the
     * document or after a blank line. Enrolled text wraps at the margin, so {@code sixty\n(60)
     * days} would otherwise read as an item label.
     */
    public static List<LexLine> lexLines(String text) {
        List<LexLine> lines = new ArrayList<>();
        int cursor = 0;
        boolean atBlockStart = true;
        int number = 0;
        for (String raw : text.split("\n", -1)) {
            number++;
            TextSpan span = new TextSpan(cursor, cursor + raw.length());
            cursor = span.end() + 1;
            String stripped = Text.strip(raw);
            if (stripped.isEmpty()) {
                lines.add(new LexLine(number, LineKind.BLANK, span, 0));
                atBlockStart = true;
                continue;
            }
            int indent = 0;
            while (indent < raw.length() && Text.isSpace(raw.charAt(indent))) {
                indent++;
            }
            lines.add(classify(number, stripped, span, indent, span.start() + indent, atBlockStart));
            atBlockStart = false;
        }
        return lines;
    }

    private static LexLine classify(
            int number, String stripped, TextSpan span, int indent, int start, boolean atBlockStart) {
        Matcher section = SECTION.matcher(stripped);
        if (section.lookingAt()) {
            return new LexLine(
                    number,
                    LineKind.SECTION,
                    span,
                    indent,
                    section.group("label"),
                    Optional.of(new TextSpan(start + section.end(), span.end())));
        }
        Matcher catchline = CATCHLINE.matcher(stripped);
        if (catchline.lookingAt()) {
            return new LexLine(
                    number,
                    LineKind.CATCHLINE,
                    span,
                    indent,
                    catchline.group("label"),
                    Optional.of(new TextSpan(start + catchline.end() - 1, span.end())));
        }
        if (TITLE.matcher(stripped).lookingAt()) {
            return new LexLine(number, LineKind.TITLE, span, indent);
        }
        if (ENACTING.matcher(stripped).lookingAt()) {
            return new LexLine(number, LineKind.ENACTING, span, indent);
        }
        if (atBlockStart) {
            Matcher labelled = LABEL.matcher(stripped);
            if (labelled.lookingAt()) {
                LineKind kind = classifyLabel(labelled.group("label"));
                if (kind != LineKind.TEXT) {
                    return new LexLine(
                            number,
                            kind,
                            span,
                            indent,
                            labelled.group("label"),
                            Optional.of(new TextSpan(start + labelled.end(), span.end())));
                }
            }
        }
        return new LexLine(number, LineKind.TEXT, span, indent);
    }
}
