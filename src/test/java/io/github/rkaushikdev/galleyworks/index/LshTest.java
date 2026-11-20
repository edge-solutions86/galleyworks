package io.github.rkaushikdev.galleyworks.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.galleyworks.ConfigError;
import io.github.rkaushikdev.galleyworks.LshConfig;
import io.github.rkaushikdev.galleyworks.MatchError;
import io.github.rkaushikdev.galleyworks.fingerprint.Signature;
import java.util.HashSet;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class LshTest {

    private static final LshConfig BANDING = new LshConfig(32, 8, 0x5EED);

    private static long[] signature() {
        return LongStream.range(0, 32).toArray();
    }

    @Test
    void oneKeyPerBand() {
        List<BandKey> keys = Lsh.bandKeys(Signature.of(signature()), BANDING);
        assertThat(keys).hasSize(8);
        assertThat(new HashSet<>(keys)).hasSize(8);
    }

    @Test
    void keysAreSixteenBytes() {
        assertThat(Lsh.bandKeys(Signature.of(signature()), BANDING))
                .allSatisfy(key -> assertThat(key.length()).isEqualTo(16));
    }

    @Test
    void aWidthMismatchIsRejected() {
        assertThatThrownBy(() -> Lsh.bandKeys(Signature.of(1, 2, 3), BANDING))
                .isInstanceOf(MatchError.class)
                .hasMessageContaining("does not match the banding");
    }

    @Test
    void identicalSignaturesShareEveryKey() {
        assertThat(Lsh.bandKeys(Signature.of(signature()), BANDING))
                .isEqualTo(Lsh.bandKeys(Signature.of(signature()), BANDING));
    }

    @Test
    void oneChangedRowBreaksOnlyItsBand() {
        long[] changed = signature();
        changed[0] = 999;
        List<BandKey> left = Lsh.bandKeys(Signature.of(signature()), BANDING);
        List<BandKey> right = Lsh.bandKeys(Signature.of(changed), BANDING);
        int differing = 0;
        for (int i = 0; i < left.size(); i++) {
            if (!left.get(i).equals(right.get(i))) {
                differing++;
            }
        }
        assertThat(differing).isEqualTo(1);
    }

    @Test
    void theBandIndexIsPartOfTheKey() {
        assertThat(Lsh.bandKey(0, Signature.of(1, 2))).isNotEqualTo(Lsh.bandKey(1, Signature.of(1, 2)));
    }

    @Test
    void probabilityEndpoints() {
        assertThat(Lsh.candidateProbability(0.0, 8, 4)).isEqualTo(0.0);
        assertThat(Lsh.candidateProbability(1.0, 8, 4)).isEqualTo(1.0);
    }

    @Test
    void probabilityRisesWithSimilarity() {
        assertThat(Lsh.candidateProbability(0.3, 8, 4)).isLessThan(Lsh.candidateProbability(0.8, 8, 4));
    }

    @Test
    void moreBandsCatchMorePairs() {
        assertThat(Lsh.candidateProbability(0.5, 16, 2)).isGreaterThan(Lsh.candidateProbability(0.5, 4, 8));
    }

    @Test
    void probabilityRejectsBadInputs() {
        assertThatThrownBy(() -> Lsh.candidateProbability(1.5, 8, 4))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("[0, 1]");
        assertThatThrownBy(() -> Lsh.candidateProbability(0.5, 0, 4))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("must both be positive");
    }

    @Test
    void solvingLandsNearTheAskedThreshold() {
        LshConfig config = Lsh.solveBanding(0.8, 128, 0x5EED);
        assertThat(config.permutations()).isEqualTo(128);
        assertThat(Math.abs(config.approximateThreshold() - 0.8)).isLessThan(0.1);
    }

    @Test
    void aLowerThresholdAsksForMoreBands() {
        assertThat(Lsh.solveBanding(0.4, 128, 0x5EED).bands())
                .isGreaterThan(Lsh.solveBanding(0.9, 128, 0x5EED).bands());
    }

    @Test
    void theSolvedBandingDividesTheWidth() {
        for (double threshold : new double[] {0.2, 0.35, 0.5, 0.65, 0.85}) {
            LshConfig config = Lsh.solveBanding(threshold, 128, 0x5EED);
            assertThat(config.permutations() % config.bands()).isZero();
        }
    }

    @Test
    void theSeedIsCarriedThrough() {
        assertThat(Lsh.solveBanding(0.5, 64, 99).seed()).isEqualTo(99);
    }

    @Test
    void anImpossibleThresholdIsRejected() {
        assertThatThrownBy(() -> Lsh.solveBanding(0.0))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("strictly inside");
        assertThatThrownBy(() -> Lsh.solveBanding(1.0))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("strictly inside");
    }
}
