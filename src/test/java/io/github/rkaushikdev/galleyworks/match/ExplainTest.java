package io.github.rkaushikdev.galleyworks.match;

import static io.github.rkaushikdev.galleyworks.match.MatchFixtures.passage;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.Chamber;
import io.github.rkaushikdev.galleyworks.Engine;
import io.github.rkaushikdev.galleyworks.InstrumentKey;
import io.github.rkaushikdev.galleyworks.MatchConfig;
import io.github.rkaushikdev.galleyworks.RunResult;
import io.github.rkaushikdev.galleyworks.index.SegmentRef;
import io.github.rkaushikdev.galleyworks.support.GoldenCorpus;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class ExplainTest {

    private static SegmentMatch synthetic(List<Passage> passages) {
        return synthetic(passages, 9, "wy");
    }

    private static SegmentMatch synthetic(List<Passage> passages, int shared) {
        return synthetic(passages, shared, "wy");
    }

    private static SegmentMatch synthetic(List<Passage> passages, int shared, String otherJurisdiction) {
        InstrumentKey queryKey = new InstrumentKey("mt", "2025", Chamber.LOWER, "HB0311");
        InstrumentKey otherKey = new InstrumentKey(otherJurisdiction, "2025", Chamber.LOWER, "HB0142");
        List<Long> left = LongStream.range(0, 10).boxed().toList();
        List<Long> right = LongStream.range(10 - shared, 20 - shared).boxed().toList();
        return new SegmentMatch(
                new SegmentRef(queryKey, 0),
                "1(a)",
                new SegmentRef(otherKey, 0),
                "1(a)",
                Scoring.scorePair(left, right),
                passages);
    }

    private static boolean anyReason(Explanation explanation, String needle) {
        return explanation.reasons().stream().anyMatch(reason -> reason.contains(needle));
    }

    @Test
    void aWholesaleCopyReadsAsStrong() {
        SegmentMatch match = synthetic(
                List.of(passage(0, 800, 0, 800), passage(900, 1400, 900, 1400), passage(1500, 1900, 1500, 1900)), 10);
        Explanation result = Explanation.explain(match);
        assertThat(result.confidence()).isSameAs(Confidence.STRONG);
        assertThat(result.isStrong()).isTrue();
        assertThat(result.headline()).contains("strong evidence");
    }

    @Test
    void aSingleShortPassageReadsAsWeak() {
        assertThat(Explanation.explain(synthetic(List.of(passage(0, 95, 0, 95)), 4))
                        .confidence())
                .isSameAs(Confidence.WEAK);
    }

    @Test
    void theHeadlineNamesBothPaths() {
        assertThat(Explanation.explain(synthetic(List.of(passage(0, 400, 0, 400))))
                        .headline())
                .contains("1(a) reuses wy/2025/lower/HB0142 1(a)");
    }

    @Test
    void theContainmentIsAlwaysStated() {
        Explanation result = Explanation.explain(synthetic(List.of(passage(0, 400, 0, 400))));
        assertThat(anyReason(result, "shared")).isTrue();
        assertThat(anyReason(result, "longest shared passage")).isTrue();
    }

    @Test
    void reorderedPassagesWeakenTheCase() {
        SegmentMatch ordered = synthetic(List.of(passage(0, 500, 0, 500), passage(600, 1100, 600, 1100)), 10);
        SegmentMatch jumbled = synthetic(List.of(passage(0, 500, 600, 1100), passage(600, 1100, 0, 500)), 10);
        assertThat(anyReason(Explanation.explain(ordered), "same order")).isTrue();
        assertThat(anyReason(Explanation.explain(jumbled), "different order")).isTrue();
    }

    @Test
    void aSameJurisdictionMatchIsDiscounted() {
        SegmentMatch across = synthetic(List.of(passage(0, 800, 0, 800)), 10);
        SegmentMatch within = synthetic(List.of(passage(0, 800, 0, 800)), 10, "mt");
        assertThat(anyReason(Explanation.explain(across), "different jurisdictions"))
                .isTrue();
        assertThat(anyReason(Explanation.explain(within), "same jurisdiction")).isTrue();
    }

    @Test
    void manyPassagesAreMentioned() {
        SegmentMatch match =
                synthetic(List.of(passage(0, 200, 0, 200), passage(300, 500, 300, 500), passage(600, 800, 600, 800)));
        assertThat(anyReason(Explanation.explain(match), "3 separate places")).isTrue();
    }

    @Test
    void aStricterConfigRaisesTheBar() {
        SegmentMatch match = synthetic(List.of(passage(0, 400, 0, 400)), 8);
        Explanation loose = Explanation.explain(match, new MatchConfig().withMinPassageChars(50));
        Explanation strict = Explanation.explain(match, new MatchConfig().withMinPassageChars(400));
        assertThat(loose.confidence() != strict.confidence() || !loose.reasons().equals(strict.reasons()))
                .isTrue();
    }

    @Test
    void aRealMatchIsExplained() {
        RunResult result = new Engine().runOver(GoldenCorpus.goldenInstruments());
        SegmentMatch match = result.matches().stream()
                .filter(SegmentMatch::crossesJurisdictions)
                .findFirst()
                .orElseThrow();
        Explanation explanation = Explanation.explain(match);
        assertThat(explanation.headline()).isNotEmpty();
        assertThat(explanation.reasons().size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void everyRealMatchGetsAConfidence() {
        Engine engine = new Engine();
        engine.runOver(GoldenCorpus.goldenInstruments());
        assertThat(engine.matcher()).isInstanceOf(Matcher.class);
        RunResult result = new Engine().runOver(GoldenCorpus.goldenInstruments());
        assertThat(result.matches()).allSatisfy(match -> assertThat(Set.of(Confidence.values()))
                .contains(Explanation.explain(match).confidence()));
    }
}
