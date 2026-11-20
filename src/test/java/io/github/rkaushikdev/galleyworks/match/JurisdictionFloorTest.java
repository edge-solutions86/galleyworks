package io.github.rkaushikdev.galleyworks.match;

import static io.github.rkaushikdev.galleyworks.match.MatchEngineTest.REWORDED;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.Chamber;
import io.github.rkaushikdev.galleyworks.EngineConfig;
import io.github.rkaushikdev.galleyworks.InstrumentKey;
import io.github.rkaushikdev.galleyworks.MatchConfig;
import io.github.rkaushikdev.galleyworks.index.MemoryIndex;
import io.github.rkaushikdev.galleyworks.index.SegmentRef;
import org.junit.jupiter.api.Test;

class JurisdictionFloorTest {

    private static SegmentRef ref(String jurisdiction) {
        return ref(jurisdiction, "HB0142");
    }

    private static SegmentRef ref(String jurisdiction, String number) {
        return new SegmentRef(new InstrumentKey(jurisdiction, "2025", Chamber.LOWER, number), 0);
    }

    private static MemoryIndex wyoming() {
        return MatchFixtures.indexed(new EngineConfig(), new String[] {"wy", "HB0142", MODEL_CLAUSE});
    }

    private static EngineConfig sameJurisdictionFloor(double floor) {
        return new EngineConfig().withMatch(new MatchConfig().withSameJurisdictionContainment(floor));
    }

    @Test
    void aCrossJurisdictionPairUsesThePlainFloor() {
        MatchConfig settings = new MatchConfig().withMinContainment(0.35).withSameJurisdictionContainment(0.6);
        assertThat(Matcher.containmentFloor(ref("wy"), ref("mt"), settings)).isEqualTo(0.35);
    }

    @Test
    void aSameJurisdictionPairUsesTheHigherFloor() {
        MatchConfig settings = new MatchConfig().withMinContainment(0.35).withSameJurisdictionContainment(0.6);
        assertThat(Matcher.containmentFloor(ref("wy"), ref("wy", "HB0999"), settings))
                .isEqualTo(0.6);
    }

    @Test
    void theHigherFloorNeverLowersTheBar() {
        MatchConfig settings = new MatchConfig().withMinContainment(0.8).withSameJurisdictionContainment(0.2);
        assertThat(Matcher.containmentFloor(ref("wy"), ref("wy", "HB0999"), settings))
                .isEqualTo(0.8);
    }

    @Test
    void theDefaultIsHigherWithinAJurisdiction() {
        MatchConfig settings = new MatchConfig();
        assertThat(settings.sameJurisdictionContainment()).isGreaterThan(settings.minContainment());
    }

    @Test
    void aRewordedClauseStillCrossesAStateLine() {
        assertThat(new Matcher(wyoming()).matchInstrument(makeBill("mt", "HB0311", REWORDED)))
                .isNotEmpty();
    }

    @Test
    void theFloorSilencesAPartialCopyInsideOneState() {
        // The reworded clause scores about 0.75, which clears the cross border floor and not this one.
        MemoryIndex index = wyoming();
        var probe = makeBill("wy", "HB0999", REWORDED);
        assertThat(new Matcher(index, sameJurisdictionFloor(0.85)).matchInstrument(probe))
                .isEmpty();
        assertThat(new Matcher(index).matchInstrument(probe)).isNotEmpty();
    }

    @Test
    void theSameProbeStillCrossesABorderAtThatFloor() {
        assertThat(new Matcher(wyoming(), sameJurisdictionFloor(0.85))
                        .matchInstrument(makeBill("mt", "HB0311", REWORDED)))
                .isNotEmpty();
    }

    @Test
    void aWholesaleCopyIsReportedEvenInsideOneState() {
        assertThat(new Matcher(wyoming()).matchInstrument(makeBill("wy", "HB0999", MODEL_CLAUSE)))
                .isNotEmpty();
    }

    @Test
    void theFloorCanBeLevelled() {
        assertThat(new Matcher(wyoming(), sameJurisdictionFloor(0.0))
                        .matchInstrument(makeBill("wy", "HB0999", REWORDED)))
                .isNotEmpty();
    }

    @Test
    void theFloorIsNotPartOfTheIndexDigest() {
        assertThat(new EngineConfig().digest())
                .isEqualTo(sameJurisdictionFloor(0.9).digest());
    }
}
