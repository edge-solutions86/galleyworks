package io.github.rkaushikdev.galleyworks.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.Segment;
import io.github.rkaushikdev.galleyworks.SegmentKind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AmendmentReaderTest {

    private static final String DRAFT =
            """
            AN ACT relating to water rights.

            Be It Enacted by the Legislature of the State of Wyoming:

            Section 1.  W.S. 41-3-101 is amended to read:

            41-3-101.  Schedule of appropriations.

            (a)  The board shall publish a schedule not later than {-sixty (60)-}{+ninety (90)+}
            days after the close of each irrigation season.

            (b)  This section does not apply to a temporary appropriation.
            """;

    private static final Map<String, Object> HINTS =
            Map.of("jurisdiction", "wy", "session", "2025", "number", "HB0142");

    private static SourceDocument draft() {
        return draft(DRAFT);
    }

    private static SourceDocument draft(String text) {
        return new SourceDocument(text, "hb0142-amd.txt", HINTS);
    }

    private static List<String> paths(Instrument instrument) {
        return instrument.segments().stream()
                .map(segment -> segment.path().printed())
                .toList();
    }

    @Test
    void theReaderNamesItsReading() {
        assertThat(new AmendmentReader().name()).isEqualTo("amendment-proposed");
        assertThat(new AmendmentReader(Reading.CURRENT).name()).isEqualTo("amendment-current");
    }

    @Test
    void sniffNeedsBothMarkupAndStructure() {
        assertThat(new AmendmentReader().sniff(draft())).isCloseTo(1.0, within(1e-9));
        assertThat(new AmendmentReader().sniff(draft("no markup here\n"))).isEqualTo(0.0);
        assertThat(new AmendmentReader().sniff(draft("{-struck-} but no sections\n")))
                .isEqualTo(0.0);
    }

    @Test
    void amendmentOutsniffsThePlainEnrolledReader() {
        assertThat(ReaderRegistry.defaultRegistry().detect(draft()).name()).isEqualTo("amendment-proposed");
    }

    @Test
    void theProposedReadingDropsStruckWords() {
        Instrument instrument = new AmendmentReader().read(draft());
        Segment section = instrument.segments().stream()
                .filter(segment -> segment.kind() == SegmentKind.SECTION)
                .findFirst()
                .orElseThrow();
        String body = section.span().sliceOf(instrument.rawText());
        assertThat(body).contains("{+ninety (90)+}");
        assertThat(body.split("sixty", -1)).hasSize(2);
    }

    @Test
    void theRawTextIsAlwaysTheMarkup() {
        Instrument instrument = new AmendmentReader().read(draft());
        assertThat(instrument.rawText()).isEqualTo(DRAFT);
        assertThat(instrument.sourceFormat()).isEqualTo("amendment-proposed");
    }

    @Test
    void bothReadingsFindTheSameStructure() {
        Instrument proposed = new AmendmentReader().read(draft());
        Instrument current = new AmendmentReader(Reading.CURRENT).read(draft());
        assertThat(paths(proposed)).isEqualTo(paths(current));
        assertThat(proposed.segments().stream().map(Segment::kind).toList())
                .isEqualTo(current.segments().stream().map(Segment::kind).toList());
    }

    @Test
    void spansLandInsideTheMarkup() {
        for (Reading reading : Reading.values()) {
            Instrument instrument = new AmendmentReader(reading).read(draft());
            for (Segment segment : instrument.segments()) {
                assertThat(segment.span().end())
                        .isLessThanOrEqualTo(instrument.rawText().length());
                assertThat(segment.span().sliceOf(instrument.rawText()).strip()).isNotEmpty();
            }
        }
    }

    @Test
    void aSubsectionSpanStillCoversItsOwnText() {
        Instrument instrument = new AmendmentReader().read(draft());
        Segment first = instrument.segments().stream()
                .filter(segment -> segment.kind() == SegmentKind.SUBSECTION)
                .findFirst()
                .orElseThrow();
        assertThat(first.span().sliceOf(instrument.rawText()))
                .startsWith("(a)")
                .doesNotContain("temporary appropriation");
    }

    @Test
    void identityAndTitleSurviveTheRoundTrip() {
        Instrument instrument = new AmendmentReader().read(draft());
        assertThat(instrument.key().slug()).isEqualTo("wy/2025/lower/HB0142");
        assertThat(instrument.title()).startsWith("AN ACT relating to water rights");
    }

    @Test
    void aDocumentWithoutMarkupIsLeftToTheEnrolledReader() {
        String plain = DRAFT.replace("{-sixty (60)-}{+ninety (90)+}", "sixty (60)");
        assertThat(ReaderRegistry.defaultRegistry().detect(draft(plain)).name()).isEqualTo("enrolled");
    }
}
