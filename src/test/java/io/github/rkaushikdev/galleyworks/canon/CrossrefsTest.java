package io.github.rkaushikdev.galleyworks.canon;

import static io.github.rkaushikdev.galleyworks.canon.CrossReferenceRule.SELF_REFERENCE;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.CanonConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CrossrefsTest {

    private static String rewrite(String text) {
        return rewrite(text, new CanonConfig());
    }

    private static String rewrite(String text, CanonConfig config) {
        return CanonPipeline.applyEdits(text, new CrossReferenceRule().edits(text, config))
                .text();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "this act",
                "this section",
                "this subsection",
                "this chapter",
                "that article",
                "the preceding section",
                "the following subsection",
                "the same paragraph"
            })
    void selfReferencesAreFolded(String phrase) {
        assertThat(rewrite("under " + phrase + ", the board shall act"))
                .isEqualTo("under " + SELF_REFERENCE + ", the board shall act");
    }

    @Test
    void thePluralIsFolded() {
        // "the preceding sections" is one reference, article included.
        assertThat(rewrite("these are the preceding sections")).isEqualTo("these are " + SELF_REFERENCE);
        assertThat(rewrite("under this subsections")).isEqualTo("under " + SELF_REFERENCE);
    }

    @Test
    void caseDoesNotMatter() {
        assertThat(rewrite("This Act applies")).isEqualTo(SELF_REFERENCE + " applies");
    }

    @Test
    void aNumberedSectionIsLeftAlone() {
        String text = "as provided in section 4 of the act";
        assertThat(rewrite(text)).isEqualTo(text);
    }

    @Test
    void aStatutoryCitationIsLeftAlone() {
        String text = "as provided in W.S. 41-3-101";
        assertThat(rewrite(text)).isEqualTo(text);
    }

    @Test
    void ordinaryProseIsUntouched() {
        String text = "the board shall publish a schedule";
        assertThat(rewrite(text)).isEqualTo(text);
    }

    @Test
    void theWordsOnEitherSideAreKeptApart() {
        String rewritten = rewrite("nothing in this act limits the board");
        assertThat(rewritten).isEqualTo("nothing in " + SELF_REFERENCE + " limits the board");
        assertThat(rewritten).doesNotContain("in limits");
    }

    @Test
    void severalReferencesInOneSentence() {
        String text = "this section and the preceding section apply under this act";
        assertThat(rewrite(text))
                .isEqualTo(SELF_REFERENCE + " and " + SELF_REFERENCE + " apply under " + SELF_REFERENCE);
    }

    @Test
    void theRuleCanBeSwitchedOff() {
        String text = "under this act";
        assertThat(rewrite(text, new CanonConfig().withNormalizeCitations(false)))
                .isEqualTo(text);
    }

    @Test
    void theRuleRunsInTheDefaultPipeline() {
        assertThat(Profiles.defaultRules().stream().map(Rule::name)).contains("cross-references");
    }

    @Test
    void thePipelineFoldsASelfReference() {
        CanonResult result = Profiles.canonicalize("(a)  Nothing in this act limits the board.");
        assertThat(result.text()).contains(SELF_REFERENCE).doesNotContain("this act");
    }

    @Test
    void twoActsNoLongerShareTheBoilerplateReference() {
        String left = Profiles.canonicalize("(a)  The provisions of this act apply to every district.")
                .text();
        String right = Profiles.canonicalize("(a)  The provisions of this chapter apply to every district.")
                .text();
        assertThat(left).isEqualTo(right);
    }
}
