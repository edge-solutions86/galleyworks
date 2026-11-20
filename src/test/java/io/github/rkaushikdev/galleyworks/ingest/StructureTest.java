package io.github.rkaushikdev.galleyworks.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.Segment;
import io.github.rkaushikdev.galleyworks.SegmentKind;
import io.github.rkaushikdev.galleyworks.SegmentPath;
import io.github.rkaushikdev.galleyworks.ingest.Blocks.SectionBlock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class StructureTest {

    private static final String NESTED =
            """
            Section 1.  W.S. 1-1-101 is amended to read:

            (a)  The board shall act.

                (i)  by publishing a notice; and

                (ii)  by mailing the notice.

                    (A)  first class postage; or

                        (1)  metered mail is allowed.

            (b)  This section is directory.
            """;

    private static final String LONG_LIST =
            """
            Section 1.  W.S. 1-1-101 is amended to read:

            (f)  Sixth.

            (g)  Seventh.

            (h)  Eighth.

            (i)  Ninth, not roman one.

            (j)  Tenth.
            """;

    private static final String EARLY_ROMAN =
            """
            Section 1.  W.S. 1-1-101 is amended to read:

            (a)  The board shall act.

            (i)  by publishing a notice; and

            (ii)  by mailing the notice.
            """;

    private static List<Segment> build(String text) {
        SectionBlock block =
                Blocks.splitSections(text, EnrolledLex.lexLines(text)).getFirst();
        return Structure.nestSection(text, block, 1, 0, SegmentPath.of("1"));
    }

    private static List<String> printed(List<Segment> segments) {
        return segments.stream().map(segment -> segment.path().printed()).toList();
    }

    @Test
    void nestingAssignsKindsByDepth() {
        assertThat(build(NESTED).stream().map(Segment::kind))
                .containsExactly(
                        SegmentKind.SUBSECTION,
                        SegmentKind.PARAGRAPH,
                        SegmentKind.PARAGRAPH,
                        SegmentKind.SUBPARAGRAPH,
                        SegmentKind.ITEM,
                        SegmentKind.SUBSECTION);
    }

    @Test
    void pathsStackUnderTheSection() {
        assertThat(printed(build(NESTED)))
                .containsExactly("1(a)", "1(a)(i)", "1(a)(ii)", "1(a)(ii)(A)", "1(a)(ii)(A)(1)", "1(b)");
    }

    @Test
    void parentsPointAtTheEnclosingSegment() {
        Map<String, Segment> byPath = build(NESTED).stream()
                .collect(Collectors.toMap(segment -> segment.path().printed(), Function.identity()));
        assertThat(byPath.get("1(a)").parent()).hasValue(0);
        assertThat(byPath.get("1(a)(i)").parent()).hasValue(byPath.get("1(a)").index());
        assertThat(byPath.get("1(a)(ii)(A)").parent())
                .hasValue(byPath.get("1(a)(ii)").index());
        assertThat(byPath.get("1(b)").parent()).hasValue(0);
    }

    @Test
    void aSubsectionSpanContainsItsParagraphs() {
        List<Segment> segments = build(NESTED);
        Segment subsection = segments.get(0);
        Segment paragraph = segments.get(1);
        assertThat(subsection.span().contains(paragraph.span())).isTrue();
        assertThat(subsection.span().sliceOf(NESTED))
                .contains("by mailing the notice")
                .doesNotContain("This section is directory");
    }

    @Test
    void spansAreRightTrimmed() {
        for (Segment segment : build(NESTED)) {
            String sliced = segment.span().sliceOf(NESTED);
            assertThat(sliced).isEqualTo(sliced.stripTrailing());
        }
    }

    @Test
    void iAfterHContinuesTheAlphabeticList() {
        List<Segment> segments = build(LONG_LIST);
        assertThat(segments).allSatisfy(segment -> assertThat(segment.kind()).isSameAs(SegmentKind.SUBSECTION));
        assertThat(segments).hasSize(5);
        assertThat(printed(segments)).containsExactly("1(f)", "1(g)", "1(h)", "1(i)", "1(j)");
    }

    @Test
    void iAfterAOpensARomanList() {
        List<Segment> segments = build(EARLY_ROMAN);
        assertThat(segments.stream().map(Segment::kind))
                .containsExactly(SegmentKind.SUBSECTION, SegmentKind.PARAGRAPH, SegmentKind.PARAGRAPH);
        assertThat(segments.get(1).parent()).hasValue(segments.get(0).index());
    }

    @Test
    void aSectionWithNoLabelsHasNoChildren() {
        assertThat(build("Section 2.  This act is effective July 1, 2025.\n")).isEmpty();
    }

    @Test
    void vContinuesARomanList() {
        String text = "Section 1.  W.S. 1-1-101 is amended to read:\n\n"
                + "(a)  Lead in.\n\n"
                + "    (iii)  third;\n\n"
                + "    (iv)  fourth;\n\n"
                + "    (v)  fifth.\n";
        List<Segment> segments = build(text);
        assertThat(segments.subList(1, segments.size())).hasSize(3).allSatisfy(segment -> assertThat(segment.kind())
                .isSameAs(SegmentKind.PARAGRAPH));
    }

    @Test
    void indentDecidesWhenNoSequenceMatches() {
        String text = "Section 1.  W.S. 1-1-101 is amended to read:\n\n"
                + "(a)  Lead in.\n\n"
                + "        (x)  a stray tenth.\n";
        assertThat(build(text).get(1).kind()).isSameAs(SegmentKind.PARAGRAPH);
    }
}
