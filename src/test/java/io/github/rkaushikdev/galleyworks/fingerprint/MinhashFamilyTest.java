package io.github.rkaushikdev.galleyworks.fingerprint;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.LshConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The statistical properties the affine family has to keep. Swapping the permutation construction
 * is the kind of change that passes every example test and quietly ruins the estimator, so these
 * measure the estimate rather than the mechanism.
 */
class MinhashFamilyTest {

    private static MinHashSigner signer() {
        return signer(256);
    }

    private static MinHashSigner signer(int permutations) {
        return new MinHashSigner(new LshConfig(permutations, permutations / 4, 0x5EED));
    }

    /** A block of distinct 64-bit values with no structure of its own. */
    private static List<Long> universe(int start, int count) {
        List<Long> values = new ArrayList<>();
        for (int n = 0; n < count; n++) {
            values.add(0x9E3779B97F4A7C15L * (start + n));
        }
        return values;
    }

    private static double trueJaccard(List<Long> left, List<Long> right) {
        Set<Long> union = new HashSet<>(left);
        union.addAll(right);
        Set<Long> shared = new HashSet<>(left);
        shared.retainAll(new HashSet<>(right));
        return (double) shared.size() / union.size();
    }

    private static double estimate(MinHashSigner family, List<Long> left, List<Long> right) {
        return MinHash.jaccardEstimate(family.sign(left), family.sign(right));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 25, 50, 75, 100})
    void theEstimateTracksTheTruth(int overlap) {
        List<Long> left = universe(0, 100);
        List<Long> right = universe(100 - overlap, 100);
        assertThat(Math.abs(estimate(signer(), left, right) - trueJaccard(left, right)))
                .isLessThan(0.12);
    }

    @Test
    void identicalSetsEstimateExactlyOne() {
        List<Long> left = universe(0, 200);
        assertThat(estimate(signer(), left, left)).isEqualTo(1.0);
    }

    @Test
    void disjointSetsEstimateNearZero() {
        assertThat(estimate(signer(), universe(0, 200), universe(500, 200))).isLessThan(0.05);
    }

    @Test
    void theEstimateIsMonotoneInTheOverlap() {
        List<Long> left = universe(0, 100);
        List<Double> estimates = new ArrayList<>();
        for (int overlap : new int[] {10, 40, 70, 100}) {
            estimates.add(estimate(signer(), left, universe(100 - overlap, 100)));
        }
        assertThat(estimates).isSorted();
    }

    @Test
    void aWiderSignatureIsNotWorse() {
        List<Long> left = universe(0, 100);
        List<Long> right = universe(50, 100);
        double truth = trueJaccard(left, right);
        double narrow = Math.abs(estimate(signer(64), left, right) - truth);
        double wide = Math.abs(estimate(signer(512), left, right) - truth);
        assertThat(wide).isLessThanOrEqualTo(narrow + 0.1);
    }

    @Test
    void twoSeedsEstimateTheSameTruth() {
        List<Long> left = universe(0, 100);
        List<Long> right = universe(60, 100);
        double truth = trueJaccard(left, right);
        for (long seed : new long[] {1, 99}) {
            MinHashSigner family = new MinHashSigner(new LshConfig().withSeed(seed));
            assertThat(Math.abs(estimate(family, left, right) - truth)).isLessThan(0.15);
        }
    }

    @Test
    void signaturesFromDifferentSeedsAreNotComparable() {
        List<Long> left = universe(0, 100);
        Signature first = new MinHashSigner(new LshConfig().withSeed(1)).sign(left);
        Signature second = new MinHashSigner(new LshConfig().withSeed(99)).sign(left);
        assertThat(MinHash.jaccardEstimate(first, second)).isLessThan(0.1);
    }

    @Test
    void orderAndDuplicationDoNotMatter() {
        List<Long> values = universe(0, 100);
        List<Long> shuffled = new ArrayList<>(values.subList(50, 100));
        shuffled.addAll(values.subList(0, 50));
        shuffled.addAll(values.subList(0, 10));
        assertThat(signer(64).sign(values)).isEqualTo(signer(64).sign(shuffled));
    }

    @Test
    void aLargeSetStillSignsQuicklyEnoughToTest() {
        Signature signature = signer(64).sign(universe(0, 5000));
        assertThat(signature.size()).isEqualTo(64);
        assertThat(signature.stream().distinct().count()).isGreaterThan(32);
    }
}
