package io.github.rkaushikdev.galleyworks.fingerprint;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.EngineConfig;
import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.ShingleConfig;
import io.github.rkaushikdev.galleyworks.ingest.EnrolledReader;
import io.github.rkaushikdev.galleyworks.ingest.SourceDocument;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class FingerprintRecordsTest {

    private static final String BODY = "(a)  The board shall publish a schedule of appropriations not later than\n"
            + "sixty (60) days after the close of each irrigation season, and shall include\n"
            + "the priority date of each appropriation in the schedule it publishes.";

    private static Instrument bill(String body) {
        return bill(body, "HB0142");
    }

    private static Instrument bill(String body, String number) {
        String text = "AN ACT relating to water rights.\n\n"
                + "Be It Enacted by the Legislature of the State of Wyoming:\n\n"
                + "Section 1.  W.S. 41-3-101 is amended to read:\n\n"
                + body
                + "\n";
        return new EnrolledReader()
                .read(new SourceDocument(
                        text, "t.txt", Map.of("jurisdiction", "wy", "session", "2025", "number", number)));
    }

    private static List<SegmentFingerprint> records(Instrument instrument) {
        return new Fingerprinter().fingerprint(instrument);
    }

    private static SegmentFingerprint subsection(Instrument instrument) {
        return records(instrument).stream()
                .filter(record -> record.path().equals("1(a)"))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void everyBodySegmentGetsARecord() {
        assertThat(records(bill(BODY)).stream().map(SegmentFingerprint::path).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("1", "1(a)");
    }

    @Test
    void recordsCarryTheInstrumentKey() {
        assertThat(records(bill(BODY)))
                .allSatisfy(record -> assertThat(record.key().slug()).isEqualTo("wy/2025/lower/HB0142"));
    }

    @Test
    void tokenCountsArePositive() {
        assertThat(records(bill(BODY)))
                .allSatisfy(record -> assertThat(record.tokenCount()).isGreaterThan(10));
    }

    @Test
    void fingerprintSpansLandInTheRawText() {
        Instrument instrument = bill(BODY);
        for (SegmentFingerprint record : records(instrument)) {
            for (Shingle shingle : record.selected()) {
                assertThat(shingle.span().end())
                        .isLessThanOrEqualTo(instrument.rawText().length());
                assertThat(shingle.span().sliceOf(instrument.rawText()).strip()).isNotEmpty();
            }
        }
    }

    @Test
    void fingerprintSpansQuoteRealWords() {
        Instrument instrument = bill(BODY);
        String quoted = subsection(instrument).selected().stream()
                .map(shingle -> shingle.span().sliceOf(instrument.rawText()))
                .collect(Collectors.joining(" "));
        assertThat(quoted.contains("board") || quoted.contains("schedule") || quoted.contains("appropriation"))
                .isTrue();
    }

    @Test
    void canonicalTextIsFolded() {
        SegmentFingerprint record = records(bill(BODY)).getFirst();
        assertThat(record.canonical()).contains("60 days").contains("cite wy 41-3-101");
    }

    @Test
    void winnowingKeepsAFractionOfTheShingles() {
        SegmentFingerprint record = subsection(bill(BODY));
        assertThat(record.selected().size()).isStrictlyBetween(0, record.tokenCount());
    }

    @Test
    void identicalBodiesShareTheirSignature() {
        SegmentFingerprint left = records(bill(BODY)).getFirst();
        SegmentFingerprint right = records(bill(BODY, "HB0999")).getFirst();
        assertThat(left.signature()).isEqualTo(right.signature());
        assertThat(left.simhash()).isEqualTo(right.simhash());
    }

    @Test
    void aRewordedBodyDiverges() {
        String other = BODY.replace("sixty (60) days", "ninety (90) days").replace("priority date", "diversion point");
        SegmentFingerprint left = records(bill(BODY)).getFirst();
        SegmentFingerprint right = records(bill(other)).getFirst();
        assertThat(left.signature()).isNotEqualTo(right.signature());
        assertThat(MinHash.jaccardEstimate(left.signature(), right.signature())).isStrictlyBetween(0.0, 1.0);
    }

    @Test
    void fingerprintValuesAndLookup() {
        SegmentFingerprint record = records(bill(BODY)).getFirst();
        long value = record.fingerprintValues().getFirst();
        assertThat(record.spanOf(value)).isPresent();
        assertThat(record.spanOf(0)).isEmpty();
    }

    @Test
    void theDigestFollowsTheConfiguration() {
        Fingerprinter plain = new Fingerprinter();
        Fingerprinter wider = new Fingerprinter(new EngineConfig().withShingle(new ShingleConfig().withWidth(9)));
        assertThat(plain.digest()).isNotEqualTo(wider.digest());
    }

    @Test
    void aNarrowBodyStillFingerprints() {
        List<SegmentFingerprint> records = records(bill("(a)  The board shall act promptly."));
        assertThat(records).isNotEmpty().allSatisfy(record -> assertThat(record.selected())
                .isNotEmpty());
    }
}
