package io.github.rkaushikdev.galleyworks.canon;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.CanonConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefinedTermsTest {

    private static final String DEFINITIONS = "As used in this act, \"board\" means the state board of control and "
            + "\"appropriation\" means a right to divert water.";

    private static String rewrite(String text) {
        return CanonPipeline.applyEdits(text, new DefinedTerms.DefinedTermRule().edits(text, new CanonConfig()))
                .text();
    }

    private static List<String> terms(String text) {
        return DefinedTerms.findDefinitions(text).stream()
                .map(DefinedTerms.Definition::term)
                .toList();
    }

    @Test
    void definitionsAreFound() {
        assertThat(terms(DEFINITIONS)).containsExactly("board", "appropriation");
    }

    @Test
    void definitionSpansCoverTheTermOnly() {
        DefinedTerms.Definition definition =
                DefinedTerms.findDefinitions(DEFINITIONS).getFirst();
        assertThat(definition.span().sliceOf(DEFINITIONS)).isEqualTo("board");
    }

    @Test
    void definitionKeysAreFolded() {
        List<DefinedTerms.Definition> found = DefinedTerms.findDefinitions("\"State  Board\" means the agency.");
        assertThat(found.getFirst().key()).isEqualTo("state board");
    }

    @Test
    void curlyQuotesAreRecognised() {
        assertThat(terms("“board” means the agency.")).containsExactly("board");
    }

    @Test
    void singleQuotesAreRecognised() {
        assertThat(terms("'board' means the agency.")).containsExactly("board");
    }

    @Test
    void aLongPhraseIsNotADefinedTerm() {
        String text = "\"a very long quoted phrase that runs on and on here\" means something.";
        assertThat(DefinedTerms.findDefinitions(text)).isEmpty();
    }

    @Test
    void textWithNoDefinitions() {
        assertThat(DefinedTerms.findDefinitions("The board shall act.")).isEmpty();
    }

    @Test
    void quotesAreStrippedFromTheDefinitionItself() {
        assertThat(rewrite("\"board\" means the agency.")).isEqualTo("board means the agency.");
    }

    @Test
    void quotesAreStrippedFromLaterUses() {
        String text = "\"board\" means the agency. The \"board\" shall publish.";
        assertThat(rewrite(text)).isEqualTo("board means the agency. The board shall publish.");
    }

    @Test
    void anUndefinedQuotationKeepsItsQuotes() {
        String text = "\"board\" means the agency. The report said \"the schedule was late\".";
        assertThat(rewrite(text)).contains("\"the schedule was late\"");
    }

    @Test
    void caseDoesNotMatterForLaterUses() {
        String text = "\"board\" means the agency. The \"Board\" shall publish.";
        assertThat(rewrite(text)).isEqualTo("board means the agency. The Board shall publish.");
    }

    @Test
    void multiWordTermsAreHandled() {
        String text = "\"water right\" means a right. A \"water right\" is transferable.";
        assertThat(rewrite(text)).isEqualTo("water right means a right. A water right is transferable.");
    }

    @Test
    void twoDefinitionsInOneSentence() {
        String rewritten = rewrite(DEFINITIONS);
        assertThat(rewritten).doesNotContain("\"").contains("board means the state board of control");
    }

    @Test
    void aDocumentWithNoDefinitionsIsUntouched() {
        String text = "The report said \"late\".";
        assertThat(rewrite(text)).isEqualTo(text);
    }
}
