package io.github.rkaushikdev.galleyworks.match;

import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.LOCAL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.fingerprints;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.EngineConfig;
import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.MatchConfig;
import io.github.rkaushikdev.galleyworks.fingerprint.SegmentFingerprint;
import io.github.rkaushikdev.galleyworks.index.MemoryIndex;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchEngineTest {

    static final String REWORDED = MODEL_CLAUSE
            .replace("sixty\n(60) days", "ninety (90) days")
            .replace("the acreage irrigated under the appropriation", "the acreage under cultivation in the district");

    private static MemoryIndex indexed(String[]... bills) {
        return MatchFixtures.indexed(new EngineConfig(), bills);
    }

    private static MemoryIndex indexed(EngineConfig config, String[]... bills) {
        return MatchFixtures.indexed(config, bills);
    }

    private static String[] bill(String jurisdiction, String number, String body) {
        return new String[] {jurisdiction, number, body};
    }

    private static Instrument montana() {
        return makeBill("mt", "HB0311", MODEL_CLAUSE);
    }

    private static <T extends Comparable<T>> List<T> descending(List<T> values) {
        List<T> sorted = new ArrayList<>(values);
        sorted.sort(Collections.reverseOrder());
        return sorted;
    }

    @Test
    void aCopiedClauseIsMatched() {
        List<SegmentMatch> matches =
                new Matcher(indexed(bill("wy", "HB0142", MODEL_CLAUSE))).matchInstrument(montana());
        assertThat(matches).isNotEmpty().allSatisfy(match -> assertThat(match.crossesJurisdictions())
                .isTrue());
    }

    @Test
    void anUnrelatedBillMatchesNothing() {
        assertThat(new Matcher(indexed(bill("wy", "HB0142", MODEL_CLAUSE)))
                        .matchInstrument(makeBill("co", "SB0007", LOCAL_CLAUSE)))
                .isEmpty();
    }

    @Test
    void aMatchReportsBothPaths() {
        SegmentMatch match = new Matcher(indexed(bill("wy", "HB0142", MODEL_CLAUSE)))
                .matchInstrument(montana())
                .getFirst();
        assertThat(match.queryPath()).isNotEmpty();
        assertThat(match.otherPath()).isNotEmpty();
        assertThat(match.other().key().jurisdiction()).isEqualTo("wy");
    }

    @Test
    void aMatchCarriesReadablePassages() {
        Instrument probe = montana();
        SegmentMatch match = new Matcher(indexed(bill("wy", "HB0142", MODEL_CLAUSE)))
                .matchInstrument(probe)
                .getFirst();
        assertThat(match.passageCount()).isGreaterThanOrEqualTo(1);
        assertThat(match.longestPassage()).isGreaterThanOrEqualTo(90);
        String quoted = match.passages().getFirst().query().sliceOf(probe.rawText());
        assertThat(quoted.contains("board") || quoted.contains("schedule")).isTrue();
    }

    @Test
    void aRewordedClauseStillMatchesWithALowerScore() {
        MemoryIndex index = indexed(bill("wy", "HB0142", MODEL_CLAUSE));
        List<SegmentMatch> exact = new Matcher(index).matchInstrument(montana());
        List<SegmentMatch> partial = new Matcher(index).matchInstrument(makeBill("mt", "HB0312", REWORDED));
        assertThat(partial).isNotEmpty();
        double bestPartial = partial.stream()
                .mapToDouble(m -> m.score().bestContainment())
                .max()
                .orElseThrow();
        double bestExact = exact.stream()
                .mapToDouble(m -> m.score().bestContainment())
                .max()
                .orElseThrow();
        assertThat(bestPartial).isLessThan(bestExact);
    }

    @Test
    void aHighContainmentFloorRejectsTheRewordedClause() {
        EngineConfig config = new EngineConfig().withMatch(new MatchConfig().withMinContainment(0.99));
        Matcher matcher = new Matcher(indexed(bill("wy", "HB0142", MODEL_CLAUSE)), config);
        assertThat(matcher.matchInstrument(makeBill("mt", "HB0312", REWORDED))).isEmpty();
    }

    @Test
    void aLongPassageFloorRejectsShortOverlaps() {
        EngineConfig config = new EngineConfig().withMatch(new MatchConfig().withMinPassageChars(100000));
        Matcher matcher = new Matcher(indexed(bill("wy", "HB0142", MODEL_CLAUSE)), config);
        assertThat(matcher.matchInstrument(montana())).isEmpty();
    }

    @Test
    void matchesAreOrderedByScoreByDefault() {
        MemoryIndex index = indexed(bill("wy", "HB0142", MODEL_CLAUSE), bill("nd", "HB0044", REWORDED));
        List<Double> scores = new Matcher(index)
                .matchInstrument(montana()).stream()
                        .map(match -> match.score().bestContainment())
                        .toList();
        assertThat(scores).isEqualTo(descending(scores));
    }

    @Test
    void matchesCanBeOrderedByPassageLength() {
        EngineConfig config = new EngineConfig().withMatch(new MatchConfig().withOrder("length"));
        MemoryIndex index = indexed(config, bill("wy", "HB0142", MODEL_CLAUSE), bill("nd", "HB0044", REWORDED));
        List<Integer> lengths = new Matcher(index, config)
                .matchInstrument(montana()).stream()
                        .map(SegmentMatch::longestPassage)
                        .toList();
        assertThat(lengths).isEqualTo(descending(lengths));
    }

    @Test
    void theCandidateCapIsHonoured() {
        EngineConfig config = new EngineConfig().withMatch(new MatchConfig().withMaxCandidates(1));
        MemoryIndex index = indexed(config, bill("wy", "HB0142", MODEL_CLAUSE), bill("nd", "HB0044", MODEL_CLAUSE));
        Matcher matcher = new Matcher(index, config);
        for (SegmentFingerprint record : fingerprints(montana())) {
            assertThat(matcher.matchRecord(record).size()).isLessThanOrEqualTo(1);
        }
    }

    @Test
    void matchingRecordsDirectlyAgreesWithMatchingTheInstrument() {
        Matcher matcher = new Matcher(indexed(bill("wy", "HB0142", MODEL_CLAUSE)));
        Instrument probe = montana();
        List<SegmentMatch> byInstrument = matcher.matchInstrument(probe);
        List<SegmentMatch> byRecord = matcher.matchRecords(fingerprints(probe));
        assertThat(byInstrument.stream().map(SegmentMatch::other).toList())
                .isEqualTo(byRecord.stream().map(SegmentMatch::other).toList());
    }

    @Test
    void matchingAgainstAnEmptyIndex() {
        assertThat(new Matcher(new MemoryIndex()).matchInstrument(montana())).isEmpty();
    }

    @Test
    void aBillMatchedAgainstItselfFindsNoSelfMatch() {
        List<SegmentMatch> matches = new Matcher(indexed(bill("wy", "HB0142", MODEL_CLAUSE)))
                .matchInstrument(makeBill("wy", "HB0142", MODEL_CLAUSE));
        assertThat(matches).allSatisfy(match -> assertThat(match.query()).isNotEqualTo(match.other()));
    }
}
