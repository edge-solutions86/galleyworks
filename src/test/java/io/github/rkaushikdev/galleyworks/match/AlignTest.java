package io.github.rkaushikdev.galleyworks.match;

import static io.github.rkaushikdev.galleyworks.match.MatchFixtures.clauseRecord;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.LOCAL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.galleyworks.MatchError;
import io.github.rkaushikdev.galleyworks.fingerprint.SegmentFingerprint;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlignTest {

    private static Anchor anchor(int queryPosition, int otherPosition) {
        return new Anchor(
                queryPosition,
                otherPosition,
                queryPosition,
                new TextSpan(queryPosition, queryPosition + 5),
                new TextSpan(otherPosition, otherPosition + 5));
    }

    private static SegmentFingerprint record(long... values) {
        return MatchFixtures.record(0, 10, 8, values);
    }

    @Test
    void sharedValuesBecomeAnchors() {
        List<Anchor> anchors = Align.anchorsBetween(record(10, 20, 30, 40), record(99, 20, 30, 77));
        assertThat(anchors.stream().map(Anchor::value)).containsExactly(20L, 30L);
    }

    @Test
    void anchorsCarryBothSpans() {
        Anchor found = Align.anchorsBetween(record(10, 20), record(20)).getFirst();
        assertThat(found.query().start()).isEqualTo(10);
        assertThat(found.other().start()).isZero();
    }

    @Test
    void noSharedValuesGivesNoAnchors() {
        assertThat(Align.anchorsBetween(record(1, 2), record(3, 4))).isEmpty();
    }

    @Test
    void anchorsComeBackInQueryOrder() {
        List<Integer> positions = Align.anchorsBetween(record(30, 10, 20), record(10, 20, 30)).stream()
                .map(Anchor::queryPosition)
                .toList();
        List<Integer> sorted = new ArrayList<>(positions);
        Collections.sort(sorted);
        assertThat(positions).isEqualTo(sorted);
    }

    @Test
    void aMonotoneRunIsOneChain() {
        List<List<Anchor>> chains = Align.chainAnchors(List.of(anchor(0, 0), anchor(1, 1), anchor(2, 2)), 4);
        assertThat(chains).hasSize(1);
        assertThat(chains.getFirst()).hasSize(3);
    }

    @Test
    void aBackwardJumpStartsANewChain() {
        List<List<Anchor>> chains = Align.chainAnchors(List.of(anchor(0, 10), anchor(1, 11), anchor(2, 3)), 40);
        assertThat(chains.stream().map(List::size)).containsExactly(2, 1);
    }

    @Test
    void aWideGapStartsANewChain() {
        List<List<Anchor>> chains = Align.chainAnchors(List.of(anchor(0, 0), anchor(100, 100)), 10);
        assertThat(chains.stream().map(List::size)).containsExactly(1, 1);
    }

    @Test
    void aGapExactlyAtTheLimitIsKept() {
        assertThat(Align.chainAnchors(List.of(anchor(0, 0), anchor(10, 10)), 10))
                .hasSize(1);
    }

    @Test
    void chainingAnEmptyList() {
        assertThat(Align.chainAnchors(List.of(), 4)).isEmpty();
    }

    @Test
    void aNegativeGapIsRejected() {
        assertThatThrownBy(() -> Align.chainAnchors(List.of(), -1))
                .isInstanceOf(MatchError.class)
                .hasMessageContaining("must not be negative");
    }

    @Test
    void alignNeedsTwoAnchorsByDefault() {
        SegmentFingerprint left = record(1, 2, 3);
        SegmentFingerprint right = record(9, 2, 9);
        assertThat(Align.align(left, right)).isEmpty();
        assertThat(Align.align(left, right, 40, 1)).isNotEmpty();
    }

    @Test
    void minAnchorsMustBePositive() {
        assertThatThrownBy(() -> Align.align(record(1), record(1), 40, 0))
                .isInstanceOf(MatchError.class)
                .hasMessageContaining("at least one");
    }

    @Test
    void aCopiedClauseAlignsAsOnePassage() {
        List<Passage> passages =
                Align.align(clauseRecord(MODEL_CLAUSE, "wy", "HB0142"), clauseRecord(MODEL_CLAUSE, "mt", "HB0311"));
        assertThat(passages).isNotEmpty();
        assertThat(passages.getFirst().anchorCount()).isGreaterThanOrEqualTo(2);
        assertThat(passages.getFirst().length()).isPositive();
    }

    @Test
    void anUnrelatedClauseDoesNotAlign() {
        assertThat(Align.align(clauseRecord(MODEL_CLAUSE, "wy", "HB0142"), clauseRecord(LOCAL_CLAUSE, "co", "SB0007")))
                .isEmpty();
    }

    @Test
    void passagesComeBackLongestFirst() {
        List<Passage> passages =
                Align.align(record(1, 2, 3, 50, 60, 61, 62, 63), record(1, 2, 3, 99, 60, 61, 62, 63), 1, 2);
        List<Integer> lengths = passages.stream().map(Passage::length).toList();
        List<Integer> sorted = new ArrayList<>(lengths);
        sorted.sort(Collections.reverseOrder());
        assertThat(lengths).isEqualTo(sorted);
    }

    @Test
    void aPassageReportsBothSides() {
        Passage passage = Align.align(
                        clauseRecord(MODEL_CLAUSE, "wy", "HB0142"), clauseRecord(MODEL_CLAUSE, "mt", "HB0311"))
                .getFirst();
        assertThat(passage.queryLength()).isPositive();
        assertThat(passage.otherLength()).isPositive();
        assertThat(passage.length()).isEqualTo(Math.min(passage.queryLength(), passage.otherLength()));
    }
}
