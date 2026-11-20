package io.github.rkaushikdev.galleyworks.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.ingest.EnrolledLex.LexLine;
import io.github.rkaushikdev.galleyworks.ingest.EnrolledLex.LineKind;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EnrolledLexTest {

    private static final String BILL =
            """
            AN ACT relating to water rights.

            Be It Enacted by the Legislature of the State of Wyoming:

            Section 1.  W.S. 41-3-101 is amended to read:

            41-3-101.  Schedule of appropriations.

            (a)  The board shall publish a schedule.

                (i)  The schedule shall include the priority date.

                (B)  Fees are not refundable.

                (3)  The board may charge a fee.
            """;

    private static List<LineKind> kinds(String text) {
        return EnrolledLex.lexLines(text).stream()
                .map(LexLine::kind)
                .filter(kind -> kind != LineKind.BLANK)
                .toList();
    }

    private static LexLine first(LineKind kind) {
        return EnrolledLex.lexLines(BILL).stream()
                .filter(line -> line.kind() == kind)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void everyLineIsClassified() {
        assertThat(kinds(BILL))
                .containsExactly(
                        LineKind.TITLE,
                        LineKind.ENACTING,
                        LineKind.SECTION,
                        LineKind.CATCHLINE,
                        LineKind.SUBSECTION,
                        LineKind.AMBIGUOUS_LABEL,
                        LineKind.SUBPARAGRAPH,
                        LineKind.ITEM);
    }

    @Test
    void spansCoverTheSourceLines() {
        String[] lines = BILL.split("\n", -1);
        for (LexLine line : EnrolledLex.lexLines(BILL)) {
            assertThat(line.span().sliceOf(BILL)).isEqualTo(lines[line.number() - 1]);
        }
    }

    @Test
    void lineNumbersStartAtOne() {
        assertThat(EnrolledLex.lexLines("a\nb").get(0).number()).isEqualTo(1);
        assertThat(EnrolledLex.lexLines("a\nb").get(1).number()).isEqualTo(2);
    }

    @Test
    void sectionLabelAndBody() {
        LexLine line = first(LineKind.SECTION);
        assertThat(line.label()).isEqualTo("1");
        assertThat(line.body()).isPresent();
        assertThat(line.body().get().sliceOf(BILL)).startsWith("W.S. 41-3-101");
    }

    @Test
    void catchlineKeepsTheStatuteNumber() {
        LexLine line = first(LineKind.CATCHLINE);
        assertThat(line.label()).isEqualTo("41-3-101");
        assertThat(line.body()).isPresent();
        assertThat(line.body().get().sliceOf(BILL).strip()).isEqualTo("Schedule of appropriations.");
    }

    @Test
    void indentIsRecorded() {
        assertThat(first(LineKind.SUBSECTION).indent()).isZero();
        assertThat(first(LineKind.AMBIGUOUS_LABEL).indent()).isEqualTo(4);
    }

    @Test
    void romanIIsNotResolvedByTheLexer() {
        assertThat(kinds("(i)  text\n")).containsExactly(LineKind.AMBIGUOUS_LABEL);
        assertThat(kinds("(v)  text\n")).containsExactly(LineKind.AMBIGUOUS_LABEL);
    }

    @ParameterizedTest
    @CsvSource({
        "(a)  text, SUBSECTION",
        "(b)  text, SUBSECTION",
        "(ii)  text, PARAGRAPH",
        "(iii)  text, PARAGRAPH",
        "(A)  text, SUBPARAGRAPH",
        "(12)  text, ITEM"
    })
    void labelClassification(String line, LineKind kind) {
        assertThat(kinds(line)).containsExactly(kind);
    }

    @Test
    void unusableLabelsFallBackToText() {
        assertThat(kinds("(a1)  text")).containsExactly(LineKind.TEXT);
        assertThat(kinds("(abcd)  text")).containsExactly(LineKind.TEXT);
    }

    @Test
    void blankLinesHaveZeroIndentAndNoLabel() {
        LexLine blank = EnrolledLex.lexLines("   \nx").getFirst();
        assertThat(blank.kind()).isSameAs(LineKind.BLANK);
        assertThat(blank.indent()).isZero();
        assertThat(blank.hasLabel()).isFalse();
    }

    @Test
    void sectionMatchingIsCaseInsensitive() {
        assertThat(kinds("SECTION 4.  text")).containsExactly(LineKind.SECTION);
    }

    @Test
    void catchlineNeedsTextAfterTheNumber() {
        assertThat(kinds("41-3-101.")).containsExactly(LineKind.TEXT);
    }

    @Test
    void hasLabelCoversTheStructuralKinds() {
        assertThat(EnrolledLex.lexLines(BILL).stream()
                        .filter(LexLine::hasLabel)
                        .map(LexLine::kind)
                        .collect(Collectors.toSet()))
                .containsExactlyInAnyOrder(
                        LineKind.SECTION,
                        LineKind.SUBSECTION,
                        LineKind.AMBIGUOUS_LABEL,
                        LineKind.SUBPARAGRAPH,
                        LineKind.ITEM);
    }

    @Test
    void emptyTextGivesOneBlankLine() {
        assertThat(kinds("")).isEmpty();
        assertThat(EnrolledLex.lexLines("")).hasSize(1);
    }

    @Test
    void aWrappedLineIsNotALabel() {
        String text = "(a)  not later than sixty\n(60) days after the close.\n";
        assertThat(kinds(text)).containsExactly(LineKind.SUBSECTION, LineKind.TEXT);
    }

    @Test
    void aLabelAfterABlankLineIsALabel() {
        String text = "(a)  lead in\n\n(1)  an item.\n";
        assertThat(kinds(text)).containsExactly(LineKind.SUBSECTION, LineKind.ITEM);
    }

    @Test
    void theFirstLineCanBeALabel() {
        assertThat(kinds("(a)  first thing.\n")).containsExactly(LineKind.SUBSECTION);
    }
}
