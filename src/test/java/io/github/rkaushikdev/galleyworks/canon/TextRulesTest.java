package io.github.rkaushikdev.galleyworks.canon;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.CanonConfig;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.List;
import org.junit.jupiter.api.Test;

class TextRulesTest {

    private static String run(Rule rule, String text) {
        return run(rule, text, new CanonConfig());
    }

    private static String run(Rule rule, String text, CanonConfig config) {
        return CanonPipeline.applyEdits(text, rule.edits(text, config)).text();
    }

    private static List<String> letters(String text) {
        return text.codePoints().mapToObj(Character::toString).toList();
    }

    @Test
    void runsWhereChangedGroupsNeighbours() {
        List<Edit> edits = TextRules.runsWhereChanged("ABCdefGH", letters("abcdefgh"));
        assertThat(edits).containsExactly(new Edit(new TextSpan(0, 3), "abc"), new Edit(new TextSpan(6, 8), "gh"));
    }

    @Test
    void runsWhereChangedHandlesAFullRewrite() {
        assertThat(TextRules.runsWhereChanged("AB", List.of("a", "b")))
                .containsExactly(new Edit(new TextSpan(0, 2), "ab"));
    }

    @Test
    void runsWhereChangedOnAnUnchangedText() {
        assertThat(TextRules.runsWhereChanged("abc", List.of("a", "b", "c"))).isEmpty();
    }

    @Test
    void curlyQuotesAndDashesBecomeAscii() {
        String text = "the “board” shall act — promptly – always";
        assertThat(run(new TextRules.UnicodeFoldRule(), text)).isEqualTo("the \"board\" shall act - promptly - always");
    }

    @Test
    void nonBreakingSpaceBecomesASpace() {
        assertThat(run(new TextRules.UnicodeFoldRule(), "sixty days")).isEqualTo("sixty days");
    }

    @Test
    void aSoftHyphenIsDropped() {
        assertThat(run(new TextRules.UnicodeFoldRule(), "appro­priation")).isEqualTo("appropriation");
    }

    @Test
    void anEllipsisExpands() {
        assertThat(run(new TextRules.UnicodeFoldRule(), "and so on…")).isEqualTo("and so on...");
    }

    @Test
    void compatibilityFormsAreNormalized() {
        assertThat(run(new TextRules.UnicodeFoldRule(), "ﬁle")).isEqualTo("file");
    }

    @Test
    void unicodeFoldingCanBeSwitchedOff() {
        String text = "the “board”";
        assertThat(run(new TextRules.UnicodeFoldRule(), text, new CanonConfig().withFoldUnicode(false)))
                .isEqualTo(text);
    }

    @Test
    void whitespaceRunsCollapse() {
        assertThat(run(new TextRules.WhitespaceRule(), "the   board\n\nshall\tact"))
                .isEqualTo("the board shall act");
    }

    @Test
    void aSingleSpaceIsLeftAlone() {
        assertThat(new TextRules.WhitespaceRule().edits("a b c", new CanonConfig()))
                .isEmpty();
    }

    @Test
    void aLoneNewlineBecomesASpace() {
        assertThat(run(new TextRules.WhitespaceRule(), "a\nb")).isEqualTo("a b");
    }

    @Test
    void caseFoldingLowercases() {
        assertThat(run(new TextRules.CaseFoldRule(), "The BOARD Shall")).isEqualTo("the board shall");
    }

    @Test
    void caseFoldingCanBeSwitchedOff() {
        assertThat(run(new TextRules.CaseFoldRule(), "The BOARD", new CanonConfig().withFoldCase(false)))
                .isEqualTo("The BOARD");
    }

    @Test
    void theThreeRulesComposeAndStillQuoteTheSource() {
        CanonPipeline pipeline = new CanonPipeline(
                List.of(new TextRules.UnicodeFoldRule(), new TextRules.WhitespaceRule(), new TextRules.CaseFoldRule()));
        String source = "The “BOARD”   shall\npublish — promptly.";
        CanonResult result = pipeline.run(source);
        assertThat(result.text()).isEqualTo("the \"board\" shall publish - promptly.");
        assertThat(result.applied()).containsExactly("unicode-fold", "whitespace", "case-fold");
        int start = result.text().indexOf("board");
        assertThat(result.quote(new TextSpan(start, start + 5))).isEqualTo("BOARD");
    }

    @Test
    void quotingWorksAfterACollapsedRun() {
        CanonPipeline pipeline =
                new CanonPipeline(List.of(new TextRules.WhitespaceRule(), new TextRules.CaseFoldRule()));
        CanonResult result = pipeline.run("the board    SHALL publish");
        int start = result.text().indexOf("publish");
        assertThat(result.quote(new TextSpan(start, start + 7))).isEqualTo("publish");
    }

    @Test
    void disabledRulesDoNotAppearInTheTrail() {
        CanonPipeline pipeline =
                new CanonPipeline(List.of(new TextRules.UnicodeFoldRule(), new TextRules.CaseFoldRule()));
        CanonConfig config = new CanonConfig().withFoldCase(false).withFoldUnicode(false);
        assertThat(pipeline.run("The BOARD", config).applied()).isEmpty();
    }
}
