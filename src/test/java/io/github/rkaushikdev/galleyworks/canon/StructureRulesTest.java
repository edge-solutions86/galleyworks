package io.github.rkaushikdev.galleyworks.canon;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.CanonConfig;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.List;
import org.junit.jupiter.api.Test;

class StructureRulesTest {

    private static String dropLabels(String text) {
        return dropLabels(text, new CanonConfig());
    }

    private static String dropLabels(String text, CanonConfig config) {
        return CanonPipeline.applyEdits(text, new StructureRules.EnumerationRule().edits(text, config))
                .text();
    }

    private static String dropEnacting(String text) {
        return dropEnacting(text, new CanonConfig());
    }

    private static String dropEnacting(String text, CanonConfig config) {
        return CanonPipeline.applyEdits(text, new StructureRules.EnactingClauseRule().edits(text, config))
                .text();
    }

    @Test
    void aLeadingLabelIsDropped() {
        assertThat(dropLabels("(a) the board shall act")).isEqualTo("the board shall act");
    }

    @Test
    void aLabelAfterAFullStopIsDropped() {
        assertThat(dropLabels("the board shall act. (b) the commission may object"))
                .isEqualTo("the board shall act. the commission may object");
    }

    @Test
    void aLabelAfterASemicolonIsDropped() {
        assertThat(dropLabels("by mail; (ii) by publication")).isEqualTo("by mail; by publication");
    }

    @Test
    void nestedLabelsAreBothDropped() {
        assertThat(dropLabels("(a) (i) by mail")).isEqualTo("by mail");
    }

    @Test
    void aCitedSubsectionIsKept() {
        String text = "as provided in subsection (b) of this section";
        assertThat(dropLabels(text)).isEqualTo(text);
    }

    @Test
    void aDoubledNumeralIsNotALabel() {
        String text = "not later than sixty (60) days";
        assertThat(dropLabels(text)).isEqualTo(text);
    }

    @Test
    void labelsReportTheirSpans() {
        String text = "(a) the board";
        List<TextSpan> spans = StructureRules.findEnumerationLabels(text);
        assertThat(spans.stream().map(span -> span.sliceOf(text))).containsExactly("(a) ");
    }

    @Test
    void aLabelAtALineStartIsDropped() {
        assertThat(dropLabels("lead in.\n(b) next part")).isEqualTo("lead in.\nnext part");
    }

    @Test
    void theEnumerationRuleCanBeSwitchedOff() {
        String text = "(a) the board shall act";
        assertThat(dropLabels(text, new CanonConfig().withDropEnumerationLabels(false)))
                .isEqualTo(text);
    }

    @Test
    void theEnactingClauseIsDropped() {
        String text = "Be It Enacted by the Legislature of the State of Wyoming: Section 1 applies.";
        assertThat(dropEnacting(text)).isEqualTo("Section 1 applies.");
    }

    @Test
    void anEnactingClauseEndingInAFullStop() {
        String text = "Be it enacted by the people of the State of Colorado. The board shall act.";
        assertThat(dropEnacting(text)).isEqualTo("The board shall act.");
    }

    @Test
    void textWithoutAnEnactingClauseIsUntouched() {
        String text = "The board shall act.";
        assertThat(dropEnacting(text)).isEqualTo(text);
    }

    @Test
    void theEnactingRuleCanBeSwitchedOff() {
        String text = "Be It Enacted by the Legislature: rest.";
        assertThat(dropEnacting(text, new CanonConfig().withDropEnactingClause(false)))
                .isEqualTo(text);
    }

    @Test
    void anEnactingClauseIsNotMatchedAcrossAWholeDocument() {
        String text = "Be it enacted " + "x".repeat(400) + ". tail.";
        assertThat(dropEnacting(text)).isEqualTo(text);
    }
}
