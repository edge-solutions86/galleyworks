package io.github.rkaushikdev.galleyworks.match;

import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.LOCAL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.fingerprints;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.galleyworks.ConfigError;
import io.github.rkaushikdev.galleyworks.fingerprint.SegmentFingerprint;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BoilerplateTest {

    private static SegmentFingerprint fake(int index, long... values) {
        return MatchFixtures.record(index, 1, 4, values);
    }

    @Test
    void anEmptyCorpusSuppressesNothing() {
        BoilerplateFilter learned = BoilerplateFilter.learn(List.of());
        assertThat(learned.size()).isZero();
        assertThat(learned.segmentsSeen()).isZero();
    }

    @Test
    void aCommonFingerprintIsSuppressed() {
        List<SegmentFingerprint> records = new ArrayList<>();
        for (int index = 10; index < 30; index++) {
            records.add(fake(index, 1, 2, index));
        }
        BoilerplateFilter learned = BoilerplateFilter.learn(records, 0.2, 2);
        assertThat(learned.contains(1)).isTrue();
        assertThat(learned.contains(2)).isTrue();
        assertThat(learned.contains(15)).isFalse();
    }

    @Test
    void aRareFingerprintSurvives() {
        List<SegmentFingerprint> records = new ArrayList<>();
        for (int index = 10; index < 30; index++) {
            records.add(fake(index, 1, index));
        }
        assertThat(BoilerplateFilter.learn(records, 0.2, 2).apply(List.of(1L, 11L)))
                .containsExactly(11L);
    }

    @Test
    void theAbsoluteFloorProtectsASmallCorpus() {
        List<SegmentFingerprint> records = List.of(fake(0, 1, 2), fake(1, 1, 2), fake(2, 1, 2));
        BoilerplateFilter learned = BoilerplateFilter.learn(records, 0.2, 5);
        assertThat(learned.size()).isZero();
        assertThat(learned.floor()).isEqualTo(5);
    }

    @Test
    void theRatioRaisesTheFloorOnALargeCorpus() {
        List<SegmentFingerprint> records = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            records.add(fake(index, 1));
        }
        BoilerplateFilter learned = BoilerplateFilter.learn(records, 0.5, 2);
        assertThat(learned.floor()).isEqualTo(51);
        assertThat(learned.contains(1)).isTrue();
    }

    @Test
    void repeatsInsideOneSegmentCountOnce() {
        BoilerplateFilter learned = BoilerplateFilter.learn(List.of(fake(0, 7, 7, 7), fake(1, 9)), 0.2, 2);
        assertThat(learned.contains(7)).isFalse();
    }

    @Test
    void applyPreservesOrder() {
        assertThat(BoilerplateFilter.of(Set.of(2L)).apply(List.of(1L, 2L, 3L, 2L, 4L)))
                .containsExactly(1L, 3L, 4L);
    }

    @Test
    void keepsReadsARecord() {
        assertThat(BoilerplateFilter.of(Set.of(5L)).keeps(fake(0, 4, 5, 6))).containsExactly(4L, 6L);
    }

    @Test
    void aDefaultFilterKeepsEverything() {
        BoilerplateFilter learned = new BoilerplateFilter();
        assertThat(learned.apply(List.of(1L, 2L, 3L))).containsExactly(1L, 2L, 3L);
        assertThat(learned.size()).isZero();
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -0.1, 1.5})
    void aBadRatioIsRejected(double ratio) {
        assertThatThrownBy(() -> BoilerplateFilter.learn(List.of(), ratio, 5))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("(0, 1]");
    }

    @Test
    void aBadFloorIsRejected() {
        assertThatThrownBy(() -> BoilerplateFilter.learn(List.of(), 0.2, 0))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("at least one");
    }

    @Test
    void aRealCorpusSuppressesTheSharedRecital() {
        List<SegmentFingerprint> records = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            String body = index % 2 == 1 ? MODEL_CLAUSE : LOCAL_CLAUSE;
            records.addAll(fingerprints(makeBill("wy", String.format("HB%04d", index), body)));
        }
        BoilerplateFilter learned = BoilerplateFilter.learn(records, 0.25, 3);
        assertThat(learned.segmentsSeen()).isEqualTo(records.size());
        assertThat(learned.floor()).isGreaterThanOrEqualTo(3);
    }
}
