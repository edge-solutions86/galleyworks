package io.github.rkaushikdev.galleyworks.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.rkaushikdev.galleyworks.MatchError;
import io.github.rkaushikdev.galleyworks.match.Scoring.PairScore;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class ScoringTest {

    private static List<Long> set(long... values) {
        return LongStream.of(values).boxed().toList();
    }

    @Test
    void jaccardEndpoints() {
        assertThat(Scoring.jaccard(set(1, 2, 3), set(1, 2, 3))).isEqualTo(1.0);
        assertThat(Scoring.jaccard(set(1, 2), set(3, 4))).isEqualTo(0.0);
        assertThat(Scoring.jaccard(set(), set())).isEqualTo(0.0);
    }

    @Test
    void jaccardIsSymmetric() {
        assertThat(Scoring.jaccard(set(1, 2, 3), set(2, 3, 4))).isEqualTo(Scoring.jaccard(set(2, 3, 4), set(1, 2, 3)));
    }

    @Test
    void jaccardCountsTheUnion() {
        assertThat(Scoring.jaccard(set(1, 2, 3, 4), set(3, 4, 5, 6))).isCloseTo(2.0 / 6, within(1e-9));
    }

    @Test
    void containmentIsAsymmetric() {
        List<Long> small = set(1, 2);
        List<Long> large = set(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(Scoring.containment(small, large)).isEqualTo(1.0);
        assertThat(Scoring.containment(large, small)).isCloseTo(0.25, within(1e-9));
    }

    @Test
    void containmentOfAnEmptySetIsZero() {
        assertThat(Scoring.containment(set(), set(1, 2))).isEqualTo(0.0);
    }

    @Test
    void overlapUsesTheSmallerSet() {
        assertThat(Scoring.overlapCoefficient(set(1, 2), set(1, 2, 3, 4))).isEqualTo(1.0);
        assertThat(Scoring.overlapCoefficient(set(1, 2), set())).isEqualTo(0.0);
    }

    @Test
    void duplicatesDoNotChangeAScore() {
        assertThat(Scoring.jaccard(set(1, 1, 2), set(1, 2))).isEqualTo(1.0);
        assertThat(Scoring.containment(set(1, 1, 2, 2), set(1, 2))).isEqualTo(1.0);
    }

    @Test
    void scorePairReportsEveryNumberOnce() {
        PairScore score = Scoring.scorePair(set(1, 2, 3, 4), set(3, 4, 5));
        assertThat(score.shared()).isEqualTo(2);
        assertThat(score.querySize()).isEqualTo(4);
        assertThat(score.otherSize()).isEqualTo(3);
        assertThat(score.jaccard()).isCloseTo(2.0 / 5, within(1e-9));
        assertThat(score.queryContainment()).isCloseTo(0.5, within(1e-9));
        assertThat(score.otherContainment()).isCloseTo(2.0 / 3, within(1e-9));
    }

    @Test
    void scorePairAgreesWithTheStandaloneFunctions() {
        List<Long> left = set(1, 2, 3, 4);
        List<Long> right = set(3, 4, 5);
        PairScore score = Scoring.scorePair(left, right);
        assertThat(score.jaccard()).isEqualTo(Scoring.jaccard(left, right));
        assertThat(score.queryContainment()).isEqualTo(Scoring.containment(left, right));
        assertThat(score.otherContainment()).isEqualTo(Scoring.containment(right, left));
    }

    @Test
    void theBestContainmentIsTheLargerSide() {
        PairScore score = Scoring.scorePair(set(1, 2, 3, 4), set(3, 4, 5));
        assertThat(score.bestContainment()).isEqualTo(score.otherContainment());
    }

    @Test
    void directionFollowsContainment() {
        assertThat(Scoring.scorePair(set(1, 2), set(1, 2, 3, 4)).direction()).isEqualTo("query-into-other");
        assertThat(Scoring.scorePair(set(1, 2, 3, 4), set(1, 2)).direction()).isEqualTo("other-into-query");
        assertThat(Scoring.scorePair(set(1, 2), set(1, 2)).direction()).isEqualTo("even");
    }

    @Test
    void aDisjointPairIsEmpty() {
        PairScore score = Scoring.scorePair(set(1, 2), set(3, 4));
        assertThat(score.isEmpty()).isTrue();
        assertThat(score.jaccard()).isEqualTo(0.0);
        assertThat(score.direction()).isEqualTo("even");
    }

    @Test
    void scoringAnEmptySetIsRejected() {
        assertThatThrownBy(() -> Scoring.scorePair(set(), set(1)))
                .isInstanceOf(MatchError.class)
                .hasMessageContaining("empty fingerprint set");
        assertThatThrownBy(() -> Scoring.scorePair(set(1), set()))
                .isInstanceOf(MatchError.class)
                .hasMessageContaining("empty fingerprint set");
    }

    @Test
    void aSmallOrdinanceInsideALongAct() {
        PairScore score = Scoring.scorePair(
                LongStream.range(0, 10).boxed().toList(),
                LongStream.range(0, 200).boxed().toList());
        assertThat(score.queryContainment()).isEqualTo(1.0);
        assertThat(score.jaccard()).isLessThan(0.06);
    }
}
