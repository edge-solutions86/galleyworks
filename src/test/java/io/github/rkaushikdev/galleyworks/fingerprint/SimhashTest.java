package io.github.rkaushikdev.galleyworks.fingerprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.galleyworks.MatchError;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SimhashTest {

    private static final List<String> CLAUSE =
            List.of("the board shall publish a schedule of appropriations not later than sixty days".split(" "));

    private static List<String> withLast(String word) {
        List<String> changed = new ArrayList<>(CLAUSE.subList(0, CLAUSE.size() - 1));
        changed.add(word);
        return changed;
    }

    private static Map<Long, Double> weights(List<SimHash.Feature> features) {
        Map<Long, Double> map = new HashMap<>();
        features.forEach(feature -> map.put(feature.value(), feature.weight()));
        return map;
    }

    @Test
    void anEmptyFeatureSetSignsAsZero() {
        assertThat(SimHash.simhash(List.of())).isZero();
        assertThat(SimHash.simhashOfTokens(List.of())).isZero();
    }

    @Test
    void aSignatureFitsIn64Bits() {
        // Every long is a 64-bit word; the hex form never needs a seventeenth digit.
        assertThat(Hashing.hex(SimHash.simhashOfTokens(CLAUSE))).hasSize(16);
    }

    @Test
    void signingIsDeterministic() {
        assertThat(SimHash.simhashOfTokens(CLAUSE)).isEqualTo(SimHash.simhashOfTokens(CLAUSE));
    }

    @Test
    void signingIsOrderIndependent() {
        List<String> reversed = new ArrayList<>(CLAUSE);
        Collections.reverse(reversed);
        assertThat(SimHash.simhashOfTokens(CLAUSE)).isEqualTo(SimHash.simhashOfTokens(reversed));
    }

    @Test
    void identicalTextsAreZeroApart() {
        long signature = SimHash.simhashOfTokens(CLAUSE);
        assertThat(SimHash.distance(signature, SimHash.simhashOfTokens(CLAUSE))).isZero();
        assertThat(SimHash.similarity(signature, SimHash.simhashOfTokens(CLAUSE)))
                .isEqualTo(1.0);
    }

    @Test
    void oneChangedWordMovesOnlyAFewBits() {
        int distance = SimHash.distance(SimHash.simhashOfTokens(CLAUSE), SimHash.simhashOfTokens(withLast("months")));
        assertThat(distance).isStrictlyBetween(0, 20);
    }

    @Test
    void unrelatedTextsAreFarApart() {
        List<String> other =
                List.of("an appropriation for the construction of a county fairground building".split(" "));
        int near = SimHash.distance(SimHash.simhashOfTokens(CLAUSE), SimHash.simhashOfTokens(withLast("weeks")));
        int far = SimHash.distance(SimHash.simhashOfTokens(CLAUSE), SimHash.simhashOfTokens(other));
        assertThat(far).isGreaterThan(near);
    }

    @Test
    void negativeWeightsAreRejected() {
        assertThatThrownBy(() -> SimHash.simhash(List.of(new SimHash.Feature(Hashing.hashToken("a"), -1.0))))
                .isInstanceOf(MatchError.class)
                .hasMessageContaining("must not be negative");
    }

    @Test
    void aZeroWeightFeatureDoesNotMoveTheSignature() {
        List<SimHash.Feature> base = CLAUSE.stream()
                .map(token -> new SimHash.Feature(Hashing.hashToken(token), 1.0))
                .toList();
        List<SimHash.Feature> padded = new ArrayList<>(base);
        padded.add(new SimHash.Feature(Hashing.hashToken("zzz"), 0.0));
        assertThat(SimHash.simhash(base)).isEqualTo(SimHash.simhash(padded));
    }

    @Test
    void defaultWeightsAreOne() {
        assertThat(SimHash.weightedFeatures(List.of("board", "shall")).stream().map(SimHash.Feature::weight))
                .containsExactly(1.0, 1.0);
    }

    @Test
    void frequencyWeightingDemotesCommonTokens() {
        Map<Long, Double> features =
                weights(SimHash.weightedFeatures(List.of("the", "escheat"), Map.of("the", 100, "escheat", 1), 100));
        assertThat(features.get(Hashing.hashToken("the"))).isLessThan(features.get(Hashing.hashToken("escheat")));
    }

    @Test
    void frequencyWeightingNeedsACorpusSize() {
        List<SimHash.Feature> features = SimHash.weightedFeatures(List.of("the"), Map.of("the", 100), 0);
        assertThat(features.getFirst().weight()).isEqualTo(1.0);
    }

    @Test
    void weightingChangesWhichBitsWin() {
        List<String> tokens = List.of("the", "board", "the", "escheat");
        Map<String, Integer> frequency = Map.of("the", 500, "board", 200, "escheat", 1);
        long plain = SimHash.simhash(SimHash.weightedFeatures(tokens));
        long weighted = SimHash.simhash(SimHash.weightedFeatures(tokens, frequency, 500));
        assertThat(plain).isNotEqualTo(weighted);
    }

    @Test
    void boilerplateDominatesWithoutWeights() {
        List<String> boilerplate = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            boilerplate.addAll(List.of("notwithstanding", "any", "other", "provision", "of", "law"));
        }
        List<String> left = new ArrayList<>(boilerplate);
        left.add("escheat");
        List<String> right = new ArrayList<>(boilerplate);
        right.add("appropriation");
        assertThat(SimHash.distance(SimHash.simhashOfTokens(left), SimHash.simhashOfTokens(right)))
                .isLessThan(12);
    }
}
