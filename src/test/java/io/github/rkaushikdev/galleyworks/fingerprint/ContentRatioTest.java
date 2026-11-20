package io.github.rkaushikdev.galleyworks.fingerprint;

import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.fingerprints;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.EngineConfig;
import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.MatchConfig;
import io.github.rkaushikdev.galleyworks.index.MemoryIndex;
import io.github.rkaushikdev.galleyworks.match.Matcher;
import org.junit.jupiter.api.Test;

class ContentRatioTest {

    private static final String RECITAL = "Notwithstanding any other provision of law and subject to the provisions "
            + "of this act, in and of the same, to and for such of them as may be within "
            + "or without it, and not otherwise.";

    private static SegmentFingerprint recordFor(String body) {
        return new Fingerprinter()
                .fingerprint(makeBill("wy", "HB0142", body)).stream()
                        .filter(record -> record.path().equals("1(a)"))
                        .findFirst()
                        .orElseThrow();
    }

    private static MemoryIndex indexed(String body) {
        MemoryIndex index = new MemoryIndex();
        index.addAll(fingerprints(makeBill("wy", "HB0142", body)));
        return index;
    }

    private static EngineConfig contentFloor(double floor) {
        return new EngineConfig().withMatch(new MatchConfig().withMinContentRatio(floor));
    }

    @Test
    void aNormalClauseHasContent() {
        SegmentFingerprint record = recordFor(MODEL_CLAUSE);
        assertThat(record.contentRatio()).isGreaterThan(0.4);
        assertThat(record.isRecital()).isFalse();
    }

    @Test
    void aRecitalIsMostlyFunctionWords() {
        SegmentFingerprint record = recordFor(RECITAL);
        assertThat(record.contentRatio()).isLessThan(0.25);
        assertThat(record.isRecital()).isTrue();
    }

    @Test
    void theRatioIsRecordedOnEverySegment() {
        assertThat(new Fingerprinter().fingerprint(makeBill("wy", "HB0142", MODEL_CLAUSE)))
                .allSatisfy(record -> assertThat(record.contentRatio()).isBetween(0.0, 1.0));
    }

    @Test
    void weightingSeparatesTwoRecitalHeavySegments() {
        SegmentFingerprint left = recordFor(RECITAL + " The board shall publish a schedule of appropriations.");
        SegmentFingerprint right = recordFor(RECITAL + " The county shall keep a register of well permits.");
        assertThat(SimHash.distance(left.simhash(), right.simhash())).isGreaterThan(8);
    }

    @Test
    void aRecitalSegmentIsSkippedByTheMatcher() {
        // The enclosing section still carries the catchline and the amended citation, so it has
        // content; only the recital subsection is skipped.
        Instrument probe = makeBill("mt", "HB0311", RECITAL);
        assertThat(new Matcher(indexed(RECITAL)).matchInstrument(probe))
                .allSatisfy(match -> assertThat(match.queryPath()).isNotEqualTo("1(a)"));
    }

    @Test
    void loweringTheFloorLetsTheRecitalThrough() {
        Instrument probe = makeBill("mt", "HB0311", RECITAL);
        assertThat(new Matcher(indexed(RECITAL), contentFloor(0.0)).matchInstrument(probe))
                .isNotEmpty();
    }

    @Test
    void aRealClauseStillMatches() {
        assertThat(new Matcher(indexed(MODEL_CLAUSE)).matchInstrument(makeBill("mt", "HB0311", MODEL_CLAUSE)))
                .isNotEmpty();
    }

    @Test
    void raisingTheFloorRejectsEverything() {
        assertThat(new Matcher(indexed(MODEL_CLAUSE), contentFloor(1.0))
                        .matchInstrument(makeBill("mt", "HB0311", MODEL_CLAUSE)))
                .isEmpty();
    }

    @Test
    void theFloorIsPartOfTheMatchConfig() {
        assertThat(new EngineConfig().match().minContentRatio()).isEqualTo(0.25);
        assertThat(new EngineConfig().digest()).isEqualTo(contentFloor(0.9).digest());
    }
}
