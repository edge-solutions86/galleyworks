package io.github.rkaushikdev.galleyworks.canon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.galleyworks.CanonConfig;
import io.github.rkaushikdev.galleyworks.ConfigError;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import org.junit.jupiter.api.Test;

class ProfilesTest {

    private static final String ACT = "Be It Enacted by the Legislature of the State of Wyoming:  "
            + "(a)  The “board” means the state board.  The \"board\" shall publish "
            + "under W.S. 41-3-101(a) not later than sixty (60) days, "
            + "and shall charge one hundred dollars ($100.00) effective July 1, 2025.";

    @Test
    void ruleOrderIsFixed() {
        assertThat(Profiles.defaultRules().stream().map(Rule::name))
                .containsExactly(
                        "unicode-fold",
                        "whitespace",
                        "defined-terms",
                        "citations",
                        "cross-references",
                        "numerals",
                        "enumeration",
                        "enacting-clause",
                        "case-fold",
                        "variant-spelling",
                        "morphology");
    }

    @Test
    void thePipelineHoldsEveryRule() {
        assertThat(Profiles.buildPipeline().ruleNames())
                .isEqualTo(Profiles.defaultRules().stream().map(Rule::name).toList());
    }

    @Test
    void theDefaultProfileFoldsTheWholeAct() {
        CanonResult result = Profiles.canonicalize(ACT);
        assertThat(result.text())
                .doesNotContain("be it enacted")
                .doesNotContain("(a) the")
                .contains("cite wy 41-3-101(a)")
                .contains("60 days")
                .contains("usd 100")
                .contains("date 2025-07-01")
                .doesNotContain("\"");
    }

    @Test
    void theResultStillQuotesTheSource() {
        CanonResult result = Profiles.canonicalize(ACT);
        int start = result.text().indexOf("shall publish");
        assertThat(result.quote(new TextSpan(start, start + 13))).isEqualTo("shall publish");
    }

    @Test
    void theVerbatimProfileChangesOnlyTypography() {
        CanonResult result = Profiles.canonicalize(ACT, Profiles.profile("verbatim"));
        assertThat(result.text())
                .contains("Be It Enacted")
                .contains("W.S. 41-3-101(a)")
                .contains("sixty (60) days")
                .doesNotContain("(a)  The");
    }

    @Test
    void theMunicipalProfileLeavesCitationsAlone() {
        CanonResult result =
                Profiles.canonicalize("adopted under section 15.04.020 today", Profiles.profile("municipal"));
        assertThat(result.text()).contains("section 15.04.020");
    }

    @Test
    void theFederalProfileKeepsTheEnactingClause() {
        CanonResult result =
                Profiles.canonicalize("Be it enacted by the Congress: the rate is 5%.", Profiles.profile("federal"));
        assertThat(result.text()).contains("be it enacted").contains("5%");
    }

    @Test
    void profilesAreNamedConsistently() {
        Profiles.PROFILES.forEach((name, config) -> assertThat(config.profile()).isEqualTo(name));
    }

    @Test
    void anUnknownProfileNamesTheKnownOnes() {
        assertThatThrownBy(() -> Profiles.profile("nowhere"))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("unknown canonicalization profile")
                .satisfies(caught -> assertThat(
                                (String) ((ConfigError) caught).context().get("known"))
                        .contains("verbatim"));
    }

    @Test
    void twoStylingsOfOneClauseCanonicalizeAlike() {
        String first = Profiles.canonicalize("(a)  The board shall act under W.S. 41-3-101 within sixty (60) days.")
                .text();
        String second = Profiles.canonicalize("(c)  The BOARD shall act under section 41-3-101 within 60 days.")
                .text();
        assertThat(first.replace("cite wy", "cite X")).isEqualTo(second.replace("cite local", "cite X"));
    }

    @Test
    void anEmptyDocumentSurvives() {
        CanonResult result = Profiles.canonicalize("");
        assertThat(result.text()).isEmpty();
        assertThat(result.applied()).isEmpty();
    }

    @Test
    void aCustomConfigIsHonoured() {
        CanonResult result = Profiles.canonicalize("The BOARD shall act.", new CanonConfig().withFoldCase(false));
        assertThat(result.text()).contains("BOARD");
    }
}
