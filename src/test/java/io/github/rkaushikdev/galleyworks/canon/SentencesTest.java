package io.github.rkaushikdev.galleyworks.canon;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SentencesTest {

    private static List<String> pieces(String text) {
        return pieces(text, false);
    }

    private static List<String> pieces(String text, boolean breakOnClause) {
        return Sentences.splitSentences(text, 0, breakOnClause).stream()
                .map(span -> span.sliceOf(text))
                .toList();
    }

    @Test
    void plainSentencesAreSplit() {
        String text = "The board shall act. The commission may object. Fees apply.";
        assertThat(pieces(text)).containsExactly("The board shall act.", "The commission may object.", "Fees apply.");
    }

    @Test
    void aStatutoryCitationIsNotABoundary() {
        String text = "W.S. 41-3-101 is amended. The board shall act.";
        assertThat(pieces(text)).containsExactly("W.S. 41-3-101 is amended.", "The board shall act.");
    }

    @Test
    void abbreviationsAreRespected() {
        String text = "See Sec. 4 for details. No. 1188 applies. Use e.g. water rights here.";
        assertThat(pieces(text))
                .containsExactly("See Sec. 4 for details.", "No. 1188 applies.", "Use e.g. water rights here.");
    }

    @Test
    void singleLetterInitialsDoNotSplit() {
        assertThat(pieces("Filed by J. Smith on Tuesday.")).containsExactly("Filed by J. Smith on Tuesday.");
    }

    @Test
    void aLowercaseContinuationIsNotABoundary() {
        assertThat(pieces("published no. 4 and the rest.")).containsExactly("published no. 4 and the rest.");
    }

    @Test
    void questionAndExclamationMarksSplit() {
        assertThat(pieces("Is it due? It is due! Pay now.")).containsExactly("Is it due?", "It is due!", "Pay now.");
    }

    @Test
    void aClosingQuoteStaysWithItsSentence() {
        String text = "The term means \"water right.\" The board shall publish.";
        assertThat(pieces(text)).containsExactly("The term means \"water right.\"", "The board shall publish.");
    }

    @Test
    void aClosingParenthesisStaysWithItsSentence() {
        String text = "The fee applies (see subsection (b).) The board shall act.";
        assertThat(pieces(text).get(1)).isEqualTo("The board shall act.");
    }

    @Test
    void theLastSentenceNeedsNoTerminator() {
        assertThat(pieces("First one. Second one without a stop"))
                .containsExactly("First one.", "Second one without a stop");
    }

    @Test
    void whitespaceOnlyInputYieldsNothing() {
        assertThat(pieces("   \n\t ")).isEmpty();
        assertThat(pieces("")).isEmpty();
    }

    @Test
    void spansAreTrimmedOfSurroundingWhitespace() {
        List<TextSpan> spans = Sentences.splitSentences("  First.   Second.  ");
        assertThat(spans).containsExactly(new TextSpan(2, 8), new TextSpan(11, 18));
    }

    @Test
    void offsetShiftsEverySpan() {
        List<TextSpan> spans = Sentences.splitSentences("A. B.", 100, false);
        assertThat(spans.getFirst().start()).isEqualTo(100);
    }

    @Test
    void clauseBreakingIsOffByDefault() {
        assertThat(pieces("the priority date; and the acreage irrigated.")).hasSize(1);
    }

    @Test
    void clauseBreakingSplitsOnSemicolonAnd() {
        assertThat(pieces("the priority date; and the acreage irrigated.", true))
                .containsExactly("the priority date;", "and the acreage irrigated.");
    }

    @Test
    void clauseBreakingSplitsOnSemicolonOr() {
        assertThat(pieces("by mail; or by publication.", true)).containsExactly("by mail;", "or by publication.");
    }

    @Test
    void aBareSemicolonIsNotAClauseBreak() {
        String text = "by mail; by publication.";
        assertThat(pieces(text, true)).containsExactly(text);
    }

    @ParameterizedTest
    @ValueSource(strings = {".", "?", "!"})
    void aNumberCanOpenASentence(String terminator) {
        String text = "The rate applies" + terminator + " 2025 is the first year.";
        assertThat(pieces(text)).hasSize(2);
    }
}
