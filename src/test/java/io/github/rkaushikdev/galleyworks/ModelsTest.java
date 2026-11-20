package io.github.rkaushikdev.galleyworks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelsTest {

    private static final String TEXT =
            "AN ACT relating to water rights. Section 1. The board shall publish a schedule.";

    private static InstrumentKey key() {
        return new InstrumentKey("wy", "2025", Chamber.LOWER, "HB0142");
    }

    @Test
    void segmentKindDepthIsOrdered() {
        assertThat(SegmentKind.SECTION.depth()).isLessThan(SegmentKind.SUBSECTION.depth());
        assertThat(SegmentKind.SUBSECTION.depth()).isLessThan(SegmentKind.PARAGRAPH.depth());
    }

    @Test
    void onlyEnactedSubstanceIsBody() {
        assertThat(SegmentKind.ENACTING.isBody()).isFalse();
        assertThat(SegmentKind.PREAMBLE.isBody()).isFalse();
        assertThat(SegmentKind.TITLE.isBody()).isFalse();
        assertThat(SegmentKind.SECTION.isBody()).isTrue();
        assertThat(SegmentKind.SUBSECTION.isBody()).isTrue();
    }

    @Test
    void segmentPathBuildsAndPrints() {
        SegmentPath path = SegmentPath.ROOT.child("3").child("a").child("ii");
        assertThat(path.depth()).isEqualTo(3);
        assertThat(path.dotted()).isEqualTo("3.a.ii");
        assertThat(path.printed()).isEqualTo("3(a)(ii)");
        assertThat(path).hasToString("3(a)(ii)");
    }

    @Test
    void emptySegmentPathPrintsEmpty() {
        assertThat(SegmentPath.ROOT.printed()).isEmpty();
    }

    @Test
    void segmentPathRejectsBlankLabels() {
        assertThatThrownBy(() -> SegmentPath.ROOT.child("  "))
                .isInstanceOf(IngestError.class)
                .hasMessageContaining("label is empty");
    }

    @Test
    void segmentPathAncestry() {
        SegmentPath parent = SegmentPath.of("3");
        SegmentPath child = SegmentPath.of("3", "a");
        assertThat(parent.isAncestorOf(child)).isTrue();
        assertThat(child.isAncestorOf(parent)).isFalse();
        assertThat(parent.isAncestorOf(parent)).isFalse();
    }

    @Test
    void segmentRejectsForwardParent() {
        assertThatThrownBy(() -> new Segment(1, SegmentKind.SECTION, SegmentPath.of("1"), new TextSpan(0, 5), 4))
                .isInstanceOf(IngestError.class)
                .hasMessageContaining("must precede the child");
    }

    @Test
    void segmentRejectsNegativeIndex() {
        assertThatThrownBy(() -> new Segment(-1, SegmentKind.SECTION, SegmentPath.ROOT, new TextSpan(0, 1)))
                .isInstanceOf(IngestError.class)
                .hasMessageContaining("non-negative");
    }

    @Test
    void instrumentRejectsSpansPastTheText() {
        Segment bad = new Segment(0, SegmentKind.SECTION, SegmentPath.of("1"), new TextSpan(0, 500));
        assertThatThrownBy(() -> new Instrument(key(), "Water rights", TEXT, List.of(bad)))
                .isInstanceOf(IngestError.class)
                .hasMessageContaining("past the end");
    }

    @Test
    void instrumentNavigation() {
        Segment enacting = new Segment(0, SegmentKind.ENACTING, SegmentPath.ROOT, new TextSpan(0, 32));
        Segment section = new Segment(1, SegmentKind.SECTION, SegmentPath.of("1"), new TextSpan(33, TEXT.length()));
        Segment child =
                new Segment(2, SegmentKind.SUBSECTION, SegmentPath.of("1", "a"), new TextSpan(45, TEXT.length()), 1);
        Instrument doc = Instrument.builder(key(), "Water rights", TEXT)
                .segments(List.of(enacting, section, child))
                .enactedOn(LocalDate.of(2025, 3, 4))
                .build();
        assertThat(doc.bodySegments().stream().map(Segment::index)).containsExactly(1, 2);
        assertThat(doc.segmentAt(2)).isSameAs(child);
        assertThat(doc.childrenOf(1)).containsExactly(child);
        assertThat(doc.effectiveDate()).contains(LocalDate.of(2025, 3, 4));
        assertThat(doc.textFor(List.of(section)).getFirst()).startsWith("Section 1.");
    }

    @Test
    void effectiveDateFallsBackToIntroduction() {
        Instrument doc = Instrument.builder(key(), "t", TEXT)
                .introducedOn(LocalDate.of(2025, 1, 9))
                .build();
        assertThat(doc.effectiveDate()).contains(LocalDate.of(2025, 1, 9));
        assertThat(Instrument.builder(key(), "t", TEXT).build().effectiveDate()).isEmpty();
    }

    @Test
    void segmentAtReportsAMissingIndex() {
        Instrument doc = Instrument.builder(key(), "t", TEXT).build();
        assertThatThrownBy(() -> doc.segmentAt(7))
                .isInstanceOf(IngestError.class)
                .hasMessageContaining("no such segment");
    }

    @Test
    void segmentTextReadsFromTheOwningInstrument() {
        Segment section = new Segment(0, SegmentKind.SECTION, SegmentPath.of("1"), new TextSpan(33, 43));
        Instrument doc = new Instrument(key(), "t", TEXT, List.of(section));
        assertThat(section.textOf(doc)).isEqualTo("Section 1.");
    }
}
