package io.github.rkaushikdev.galleyworks.match;

import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.fingerprints;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.Engine;
import io.github.rkaushikdev.galleyworks.EngineConfig;
import io.github.rkaushikdev.galleyworks.MatchConfig;
import io.github.rkaushikdev.galleyworks.RunResult;
import io.github.rkaushikdev.galleyworks.index.MemoryIndex;
import io.github.rkaushikdev.galleyworks.support.GoldenCorpus;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DistinctAnchorsTest {

    private static final MatchConfig FLOOR_TWO =
            new MatchConfig().withMinPassageChars(90).withMinDistinctAnchors(2);

    private static Passage passage(int length, long... values) {
        List<Anchor> anchors = new ArrayList<>();
        for (int position = 0; position < values.length; position++) {
            TextSpan span = new TextSpan(position * 10, position * 10 + 8);
            anchors.add(new Anchor(position, position, values[position], span, span));
        }
        return new Passage(new TextSpan(0, length), new TextSpan(0, length), anchors);
    }

    private static MemoryIndex wyoming() {
        MemoryIndex index = new MemoryIndex();
        index.addAll(fingerprints(makeBill("wy", "HB0142", MODEL_CLAUSE)));
        return index;
    }

    @Test
    void aLongPassageOnDistinctAnchorsIsReportable() {
        assertThat(Matcher.passageIsReportable(passage(200, 1, 2, 3), FLOOR_TWO))
                .isTrue();
    }

    @Test
    void aShortPassageIsNot() {
        assertThat(Matcher.passageIsReportable(passage(50, 1, 2, 3), FLOOR_TWO)).isFalse();
    }

    @Test
    void aLongPassageOnOneRepeatedAnchorIsNot() {
        assertThat(Matcher.passageIsReportable(passage(500, 7, 7, 7, 7), FLOOR_TWO))
                .isFalse();
    }

    @Test
    void theFloorCanBeLowered() {
        MatchConfig settings = new MatchConfig().withMinPassageChars(90).withMinDistinctAnchors(1);
        assertThat(Matcher.passageIsReportable(passage(500, 7, 7, 7), settings)).isTrue();
    }

    @Test
    void theDefaultFloorIsTwo() {
        assertThat(new MatchConfig().minDistinctAnchors()).isEqualTo(2);
    }

    @Test
    void aMatchReportsItsDistinctAnchorCount() {
        SegmentMatch match = new Matcher(wyoming())
                .matchInstrument(makeBill("mt", "HB0311", MODEL_CLAUSE))
                .getFirst();
        assertThat(match.distinctAnchors()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void aRealCopyStillMatches() {
        assertThat(new Matcher(wyoming()).matchInstrument(makeBill("mt", "HB0311", MODEL_CLAUSE)))
                .isNotEmpty();
    }

    @Test
    void aHighFloorRejectsEverything() {
        EngineConfig config = new EngineConfig().withMatch(new MatchConfig().withMinDistinctAnchors(1000));
        assertThat(new Matcher(wyoming(), config).matchInstrument(makeBill("mt", "HB0311", MODEL_CLAUSE)))
                .isEmpty();
    }

    @Test
    void theGoldenFamilySurvivesTheFloor() {
        RunResult result = new Engine().runOver(GoldenCorpus.goldenInstruments());
        assertThat(result.matches()).isNotEmpty().allSatisfy(match -> assertThat(match.distinctAnchors())
                .isGreaterThanOrEqualTo(2));
    }

    @Test
    void theFloorIsNotPartOfTheIndexDigest() {
        EngineConfig strict = new EngineConfig().withMatch(new MatchConfig().withMinDistinctAnchors(9));
        assertThat(new EngineConfig().digest()).isEqualTo(strict.digest());
    }
}
