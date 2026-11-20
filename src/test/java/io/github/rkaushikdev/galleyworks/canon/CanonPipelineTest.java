package io.github.rkaushikdev.galleyworks.canon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.galleyworks.CanonConfig;
import io.github.rkaushikdev.galleyworks.CanonError;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CanonPipelineTest {

    /** Test rule: replace every occurrence of a needle. */
    private record Replace(String name, String needle, String replacement) implements Rule {

        @Override
        public List<Edit> edits(String text, CanonConfig config) {
            List<Edit> edits = new ArrayList<>();
            int start = text.indexOf(needle);
            while (start >= 0) {
                edits.add(new Edit(new TextSpan(start, start + needle.length()), replacement));
                start = text.indexOf(needle, start + needle.length());
            }
            return edits;
        }
    }

    private static final class Silent implements Rule {

        @Override
        public String name() {
            return "silent";
        }

        @Override
        public List<Edit> edits(String text, CanonConfig config) {
            return List.of();
        }
    }

    @Test
    void editReportsItsShape() {
        Edit edit = new Edit(new TextSpan(0, 5), "");
        assertThat(edit.isDeletion()).isTrue();
        assertThat(edit.delta()).isEqualTo(-5);
        assertThat(new Edit(new TextSpan(0, 1), "abc").isDeletion()).isFalse();
    }

    @Test
    void applyEditsWithNothingReturnsTheText() {
        Rewrite rewrite = CanonPipeline.applyEdits("unchanged", List.of());
        assertThat(rewrite.text()).isEqualTo("unchanged");
        assertThat(rewrite.mapping().toSource(4)).isEqualTo(4);
    }

    @Test
    void applyEditsReplacesAndMaps() {
        Rewrite rewrite = CanonPipeline.applyEdits("sixty days", List.of(new Edit(new TextSpan(0, 5), "ninety")));
        assertThat(rewrite.text()).isEqualTo("ninety days");
        assertThat(rewrite.mapping().toSource(7)).isEqualTo(6);
    }

    @Test
    void applyEditsSortsItsInput() {
        List<Edit> edits = List.of(new Edit(new TextSpan(6, 10), "Z"), new Edit(new TextSpan(0, 5), "A"));
        assertThat(CanonPipeline.applyEdits("hello world", edits).text()).isEqualTo("A Zd");
    }

    @Test
    void overlappingEditsAreRejected() {
        List<Edit> edits = List.of(new Edit(new TextSpan(0, 6), "x"), new Edit(new TextSpan(3, 9), "y"));
        assertThatThrownBy(() -> CanonPipeline.applyEdits("abcdefghij", edits, "demo"))
                .isInstanceOf(CanonError.class)
                .hasMessageContaining("overlapping edits")
                .satisfies(caught -> assertThat(((CanonError) caught).context()).containsEntry("rule", "demo"));
    }

    @Test
    void anEditPastTheEndIsRejected() {
        assertThatThrownBy(() -> CanonPipeline.applyEdits("short", List.of(new Edit(new TextSpan(0, 40), "x"))))
                .isInstanceOf(CanonError.class)
                .hasMessageContaining("past the end");
    }

    @Test
    void abuttingEditsAreAllowed() {
        List<Edit> edits = List.of(new Edit(new TextSpan(0, 3), "A"), new Edit(new TextSpan(3, 6), "B"));
        assertThat(CanonPipeline.applyEdits("abcdef", edits).text()).isEqualTo("AB");
    }

    @Test
    void pipelineReportsItsRules() {
        CanonPipeline pipeline = new CanonPipeline(List.of(new Replace("one", "a", "b"), new Silent()));
        assertThat(pipeline.ruleNames()).containsExactly("one", "silent");
    }

    @Test
    void duplicateRuleNamesAreRejected() {
        assertThatThrownBy(
                        () -> new CanonPipeline(List.of(new Replace("same", "a", "b"), new Replace("same", "c", "d"))))
                .isInstanceOf(CanonError.class)
                .hasMessageContaining("duplicate rule names");
    }

    @Test
    void pipelineRunsRulesInOrder() {
        CanonPipeline pipeline =
                new CanonPipeline(List.of(new Replace("first", "cat", "dog"), new Replace("second", "dog", "fox")));
        CanonResult result = pipeline.run("the cat sat");
        assertThat(result.text()).isEqualTo("the fox sat");
        assertThat(result.applied()).containsExactly("first", "second");
    }

    @Test
    void aRuleThatDoesNothingIsNotRecorded() {
        CanonPipeline pipeline = new CanonPipeline(List.of(new Silent(), new Replace("real", "a", "b")));
        assertThat(pipeline.run("a").applied()).containsExactly("real");
        assertThat(pipeline.run("zzz").applied()).isEmpty();
    }

    @Test
    void resultQuotesTheOriginalWords() {
        CanonPipeline pipeline = new CanonPipeline(List.of(new Replace("fold", "SIXTY", "sixty")));
        CanonResult result = pipeline.run("not later than SIXTY days");
        int start = result.text().indexOf("sixty");
        assertThat(result.quote(new TextSpan(start, start + 5))).isEqualTo("SIXTY");
    }

    @Test
    void mappingSurvivesSeveralRules() {
        CanonPipeline pipeline = new CanonPipeline(
                List.of(new Replace("a", "  ", " "), new Replace("b", "SHALL", "shall"), new Replace("c", "!", "")));
        CanonResult result = pipeline.run("the  board  SHALL  act!");
        assertThat(result.text()).isEqualTo("the board shall act");
        int start = result.text().indexOf("shall");
        assertThat(result.quote(new TextSpan(start, start + 5))).isEqualTo("SHALL");
    }

    @Test
    void rulesSatisfyTheProtocol() {
        assertThat(new Replace("x", "a", "b")).isInstanceOf(Rule.class);
        assertThat(new Silent()).isInstanceOf(Rule.class);
    }

    @Test
    void pipelineAcceptsAnExplicitConfig() {
        CanonPipeline pipeline = new CanonPipeline(List.of(new Replace("x", "a", "b")));
        assertThat(pipeline.run("aaa", new CanonConfig().withFoldCase(false)).text())
                .isEqualTo("bbb");
    }
}
