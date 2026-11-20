package io.github.rkaushikdev.galleyworks.canon;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.CanonConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class NumeralsTest {

    private static String rewrite(String text) {
        return rewrite(text, new CanonConfig());
    }

    private static String rewrite(String text, CanonConfig config) {
        return CanonPipeline.applyEdits(text, new Numerals.NumeralRule().edits(text, config))
                .text();
    }

    @Test
    void aDoubledNumeralKeepsOnlyTheDigits() {
        assertThat(rewrite("not later than sixty (60) days")).isEqualTo("not later than 60 days");
    }

    @Test
    void aDoubledNumeralWithAHyphenatedWord() {
        assertThat(rewrite("within twenty-one (21) days")).isEqualTo("within 21 days");
    }

    @Test
    void moneyWrittenTwiceCollapses() {
        assertThat(rewrite("a fee of one hundred dollars ($100.00)")).isEqualTo("a fee of usd 100");
    }

    @Test
    void bareMoneyIsNormalized() {
        assertThat(rewrite("a fee of $1,500.00")).isEqualTo("a fee of usd 1500");
    }

    @Test
    void moneyWithoutCents() {
        assertThat(rewrite("$250")).isEqualTo("usd 250");
    }

    @Test
    void moneyKeepsRealCents() {
        assertThat(rewrite("$0.75")).isEqualTo("usd 0.75");
    }

    @Test
    void percentWrittenTwiceCollapses() {
        assertThat(rewrite("a rate of five percent (5%)")).isEqualTo("a rate of 5 pct");
    }

    @Test
    void perCentumIsRecognised() {
        assertThat(rewrite("sixty-five per centum (65%)")).isEqualTo("65 pct");
    }

    @Test
    void aBarePercentageIsNormalized() {
        assertThat(rewrite("increase of 7.5%")).isEqualTo("increase of 7.5 pct");
    }

    @Test
    void datesBecomeIso() {
        assertThat(rewrite("effective July 1, 2025")).isEqualTo("effective date 2025-07-01");
    }

    @Test
    void datesWithoutAComma() {
        assertThat(rewrite("on March 15 2026")).isEqualTo("on date 2026-03-15");
    }

    @Test
    void ordinalsLoseTheirSuffix() {
        assertThat(rewrite("the 1st day and the 22nd day")).isEqualTo("the 1 day and the 22 day");
    }

    @Test
    void moneyWinsOverTheDoubledNumeralPattern() {
        assertThat(rewrite("one hundred dollars ($100.00) per acre")).isEqualTo("usd 100 per acre");
    }

    @Test
    void percentWinsOverTheDoubledNumeralPattern() {
        assertThat(rewrite("five percent (5%) of the total")).isEqualTo("5 pct of the total");
    }

    @Test
    void editsNeverOverlap() {
        String text = "sixty (60) days, one hundred dollars ($100.00), and five percent (5%)";
        List<Edit> edits = Numerals.normalizeNumerals(text);
        for (int i = 1; i < edits.size(); i++) {
            assertThat(edits.get(i - 1).span().end())
                    .isLessThanOrEqualTo(edits.get(i).span().start());
        }
    }

    @Test
    void severalQuantitiesInOneSentence() {
        String text = "pay one hundred dollars ($100.00) within thirty (30) days at five percent (5%)";
        assertThat(rewrite(text)).isEqualTo("pay usd 100 within 30 days at 5 pct");
    }

    @Test
    void plainProseIsUntouched() {
        String text = "The board shall publish a schedule.";
        assertThat(rewrite(text)).isEqualTo(text);
    }

    @Test
    void aLoneNumberIsLeftAlone() {
        assertThat(rewrite("section 4 applies")).isEqualTo("section 4 applies");
    }

    @Test
    void theRuleCanBeSwitchedOff() {
        String text = "sixty (60) days";
        assertThat(rewrite(text, new CanonConfig().withNormalizeNumbers(false))).isEqualTo(text);
    }
}
