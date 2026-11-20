package io.github.rkaushikdev.galleyworks.canon;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.CanonConfig;
import io.github.rkaushikdev.galleyworks.util.Text;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VariantsTest {

    private static String rewrite(String text) {
        return rewrite(text, new CanonConfig());
    }

    private static String rewrite(String text, CanonConfig config) {
        return CanonPipeline.applyEdits(text, new Variants.VariantSpellingRule().edits(text, config))
                .text();
    }

    @ParameterizedTest
    @CsvSource({"judgement, judgment", "licence, license", "offence, offense", "authorised, authorized", "whilst, while"
    })
    void variantsAreFolded(String written, String preferred) {
        assertThat(rewrite("the " + written + " of the board")).isEqualTo("the " + preferred + " of the board");
    }

    @Test
    void thePreferredFormIsLeftAlone() {
        String text = "the judgment of the board";
        assertThat(rewrite(text)).isEqualTo(text);
    }

    @Test
    void caseIsRecognisedButThePreferredFormWins() {
        assertThat(rewrite("Judgement")).isEqualTo("judgment");
    }

    @Test
    void aWordThatIsNotAVariantIsUntouched() {
        String text = "the board shall insure the property and ensure compliance";
        assertThat(rewrite(text)).isEqualTo(text);
    }

    @Test
    void theTableMapsToRealWords() {
        assertThat(Variants.VARIANTS.values())
                .allSatisfy(value -> assertThat(Text.isAlpha(value)).isTrue());
        assertThat(Variants.VARIANTS.keySet()).allSatisfy(key -> assertThat(key).isEqualTo(Text.lower(key)));
    }

    @Test
    void theTableIsIdempotent() {
        for (String preferred : new HashSet<>(Variants.VARIANTS.values())) {
            assertThat(Variants.canonicalSpelling(preferred)).isEqualTo(preferred);
        }
    }

    @Test
    void canonicalSpellingPassesUnknownWordsThrough() {
        assertThat(Variants.canonicalSpelling("appropriation")).isEqualTo("appropriation");
    }

    @Test
    void theRuleCanBeSwitchedOff() {
        String text = "the judgement of the board";
        assertThat(rewrite(text, new CanonConfig().withFoldVariants(false))).isEqualTo(text);
    }

    @Test
    void theRuleRunsInTheDefaultPipeline() {
        assertThat(Profiles.defaultRules().stream().map(Rule::name)).contains("variant-spelling");
    }

    @Test
    void twoSpellingsCanonicalizeAlike() {
        String left = Profiles.canonicalize("(a)  The licence is subject to the judgement of the board.")
                .text();
        String right = Profiles.canonicalize("(a)  The license is subject to the judgment of the board.")
                .text();
        assertThat(left).isEqualTo(right);
    }

    @Test
    void severalVariantsInOneSentence() {
        String text = "the authorised organisation shall practise the programme";
        assertThat(rewrite(text)).isEqualTo("the authorized organization shall practice the program");
    }

    @Test
    void aVariantInsideALongerWordIsNotFolded() {
        String text = "the licences and licencing rules";
        assertThat(rewrite(text)).isEqualTo(text);
    }
}
