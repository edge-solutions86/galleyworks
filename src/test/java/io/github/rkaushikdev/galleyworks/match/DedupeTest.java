package io.github.rkaushikdev.galleyworks.match;

import static io.github.rkaushikdev.galleyworks.match.MatchFixtures.passage;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.fingerprints;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.galleyworks.Chamber;
import io.github.rkaushikdev.galleyworks.InstrumentKey;
import io.github.rkaushikdev.galleyworks.MatchError;
import io.github.rkaushikdev.galleyworks.index.MemoryIndex;
import io.github.rkaushikdev.galleyworks.index.SegmentRef;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.List;
import org.junit.jupiter.api.Test;

class DedupeTest {

    private static SegmentMatch match(int queryIndex, int otherIndex, List<Passage> passages) {
        InstrumentKey left = new InstrumentKey("mt", "2025", Chamber.LOWER, "HB0311");
        InstrumentKey right = new InstrumentKey("wy", "2025", Chamber.LOWER, "HB0142");
        return new SegmentMatch(
                new SegmentRef(left, queryIndex),
                String.valueOf(queryIndex),
                new SegmentRef(right, otherIndex),
                String.valueOf(otherIndex),
                Scoring.scorePair(List.of(1L, 2L, 3L), List.of(1L, 2L, 3L)),
                passages);
    }

    @Test
    void overlapOfIdenticalSpansIsOne() {
        assertThat(Dedupe.overlapFraction(new TextSpan(0, 10), new TextSpan(0, 10)))
                .isEqualTo(1.0);
    }

    @Test
    void overlapUsesTheShorterSpan() {
        assertThat(Dedupe.overlapFraction(new TextSpan(0, 100), new TextSpan(10, 20)))
                .isEqualTo(1.0);
    }

    @Test
    void disjointSpansDoNotOverlap() {
        assertThat(Dedupe.overlapFraction(new TextSpan(0, 10), new TextSpan(20, 30)))
                .isEqualTo(0.0);
    }

    @Test
    void anEmptySpanDoesNotOverlap() {
        assertThat(Dedupe.overlapFraction(new TextSpan(5, 5), new TextSpan(0, 10)))
                .isEqualTo(0.0);
    }

    @Test
    void aContainedPassageIsDropped() {
        Passage outer = passage(0, 100, 0, 100);
        Passage inner = passage(10, 60, 10, 60);
        assertThat(Dedupe.dedupePassages(List.of(inner, outer))).containsExactly(outer);
    }

    @Test
    void disjointPassagesAreBothKept() {
        assertThat(Dedupe.dedupePassages(List.of(passage(0, 50, 0, 50), passage(200, 260, 200, 260))))
                .hasSize(2);
    }

    @Test
    void overlapOnOnlyOneSideKeepsBoth() {
        assertThat(Dedupe.dedupePassages(List.of(passage(0, 100, 0, 100), passage(10, 90, 500, 580))))
                .hasSize(2);
    }

    @Test
    void survivingPassagesComeBackInQueryOrder() {
        List<Passage> kept = Dedupe.dedupePassages(List.of(passage(300, 400, 300, 400), passage(0, 200, 0, 200)));
        assertThat(kept.stream().map(p -> p.query().start())).containsExactly(0, 300);
    }

    @Test
    void aBadOverlapThresholdIsRejected() {
        assertThatThrownBy(() -> Dedupe.dedupePassages(List.of(), 0.0))
                .isInstanceOf(MatchError.class)
                .hasMessageContaining("(0, 1]");
    }

    @Test
    void aDuplicateMatchIsDropped() {
        SegmentMatch parent = match(0, 0, List.of(passage(0, 200, 0, 200)));
        SegmentMatch child = match(1, 1, List.of(passage(20, 180, 20, 180)));
        assertThat(Dedupe.dedupeMatches(List.of(child, parent))).containsExactly(parent);
    }

    @Test
    void matchesOnDifferentInstrumentsAreBothKept() {
        SegmentMatch left = match(0, 0, List.of(passage(0, 200, 0, 200)));
        SegmentMatch right = new SegmentMatch(
                left.query(),
                left.queryPath(),
                new SegmentRef(new InstrumentKey("nd", "2025", Chamber.LOWER, "HB0044"), 0),
                "0",
                left.score(),
                left.passages());
        assertThat(Dedupe.dedupeMatches(List.of(left, right))).hasSize(2);
    }

    @Test
    void matchesCoveringDifferentRegionsAreBothKept() {
        SegmentMatch first = match(0, 0, List.of(passage(0, 200, 0, 200)));
        SegmentMatch second = match(2, 2, List.of(passage(900, 1100, 900, 1100)));
        assertThat(Dedupe.dedupeMatches(List.of(first, second))).hasSize(2);
    }

    @Test
    void dedupingAnEmptyList() {
        assertThat(Dedupe.dedupeMatches(List.of())).isEmpty();
        assertThat(Dedupe.dedupePassages(List.of())).isEmpty();
    }

    @Test
    void realMatchesCollapseToOneFinding() {
        MemoryIndex index = new MemoryIndex();
        index.addAll(fingerprints(makeBill("wy", "HB0142", MODEL_CLAUSE)));
        List<SegmentMatch> matches = new Matcher(index).matchInstrument(makeBill("mt", "HB0311", MODEL_CLAUSE));
        assertThat(Dedupe.dedupeMatches(matches).size()).isLessThanOrEqualTo(matches.size());
        assertThat(Dedupe.dedupeMatches(matches)).isNotEmpty();
    }
}
