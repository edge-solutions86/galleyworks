package io.github.rkaushikdev.galleyworks.canon;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.CanonConfig;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MorphologyTest {

    private static String rewrite(String text) {
        return rewrite(text, new CanonConfig());
    }

    private static String rewrite(String text, CanonConfig config) {
        return CanonPipeline.applyEdits(text, new Morphology.MorphologyRule().edits(text, config))
                .text();
    }

    @Test
    void aPossessiveIsFolded() {
        assertThat(rewrite("the board's schedule")).isEqualTo("the board schedule");
    }

    @Test
    void aPluralPossessiveIsFolded() {
        assertThat(rewrite("the boards' schedule")).isEqualTo("the boards schedule");
    }

    @Test
    void aCurlyApostropheIsFolded() {
        assertThat(rewrite("the board’s schedule")).isEqualTo("the board schedule");
    }

    @Test
    void aContractionOfTwoLettersIsLeftAlone() {
        String text = "a b's c";
        assertThat(rewrite(text)).isEqualTo(text);
    }

    @Test
    void foldPossessiveWorksOnABareWord() {
        assertThat(Morphology.foldPossessive("governor's")).isEqualTo("governor");
        assertThat(Morphology.foldPossessive("appropriation")).isEqualTo("appropriation");
    }

    @ParameterizedTest
    @ValueSource(strings = {"non", "pre", "sub", "inter", "re"})
    void hyphenatedPrefixesCloseUp(String prefix) {
        assertThat(rewrite("a " + prefix + "-resident owner")).isEqualTo("a " + prefix + "resident owner");
    }

    @Test
    void thePrefixListIsLowercaseAndUnique() {
        assertThat(Morphology.JOINED_PREFIXES)
                .allSatisfy(prefix -> assertThat(prefix).isLowerCase());
        assertThat(new HashSet<>(Morphology.JOINED_PREFIXES)).hasSameSizeAs(Morphology.JOINED_PREFIXES);
    }

    @Test
    void aCompoundThatIsNotAPrefixIsLeftAlone() {
        String text = "a water-right holder";
        assertThat(rewrite(text)).isEqualTo(text);
    }

    @Test
    void aShortTailIsLeftAlone() {
        String text = "the re-do order";
        assertThat(rewrite(text)).isEqualTo(text);
    }

    @Test
    void caseIsRecognised() {
        assertThat(rewrite("Non-Resident")).isEqualTo("NonResident");
    }

    @Test
    void theRuleCanBeSwitchedOff() {
        String text = "the board's non-resident owner";
        assertThat(rewrite(text, new CanonConfig().withFoldVariants(false))).isEqualTo(text);
    }

    @Test
    void theRuleRunsInTheDefaultPipeline() {
        assertThat(Profiles.defaultRules().stream().map(Rule::name)).contains("morphology");
    }

    @Test
    void twoTypesettingsCanonicalizeAlike() {
        String left = Profiles.canonicalize("(a)  The board's non-resident applicant shall pay.")
                .text();
        String right = Profiles.canonicalize("(a)  The board nonresident applicant shall pay.")
                .text();
        assertThat(left).isEqualTo(right);
    }

    @Test
    void severalFormsInOneSentence() {
        String text = "the board's pre-approval of a non-resident co-owner";
        assertThat(rewrite(text)).isEqualTo("the board preapproval of a nonresident coowner");
    }
}
