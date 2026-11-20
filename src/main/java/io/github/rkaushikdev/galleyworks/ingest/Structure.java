package io.github.rkaushikdev.galleyworks.ingest;

import io.github.rkaushikdev.galleyworks.ParseError;
import io.github.rkaushikdev.galleyworks.Segment;
import io.github.rkaushikdev.galleyworks.SegmentKind;
import io.github.rkaushikdev.galleyworks.SegmentPath;
import io.github.rkaushikdev.galleyworks.ingest.Blocks.SectionBlock;
import io.github.rkaushikdev.galleyworks.ingest.EnrolledLex.LexLine;
import io.github.rkaushikdev.galleyworks.ingest.EnrolledLex.LineKind;
import io.github.rkaushikdev.galleyworks.util.Text;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Nesting labelled lines into a segment tree.
 *
 * <p>A labelled line owns everything up to the next line at its own depth or shallower, so a
 * subsection's span contains its paragraphs. That is what a reader of the statute means by
 * "subsection (a)", and it is what a reuse report has to quote.
 */
public final class Structure {

    private static final Map<LineKind, SegmentKind> KIND_FOR_LINE = Map.of(
            LineKind.SUBSECTION, SegmentKind.SUBSECTION,
            LineKind.PARAGRAPH, SegmentKind.PARAGRAPH,
            LineKind.SUBPARAGRAPH, SegmentKind.SUBPARAGRAPH,
            LineKind.ITEM, SegmentKind.ITEM);

    private Structure() {}

    /** An open label list. */
    record Frame(SegmentKind kind, int index, String lastLabel, int indent, SegmentPath path) {}

    private static Optional<String> successor(SegmentKind kind, String label) {
        try {
            if (kind == SegmentKind.PARAGRAPH) {
                return Optional.of(Labels.romanSuccessor(label));
            }
            if (kind == SegmentKind.SUBSECTION) {
                return Optional.of(Labels.alphaSuccessor(label));
            }
        } catch (ParseError exc) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * Decide whether an ambiguous label continues a list or opens a nested one. Continuation wins
     * over nesting: a subsection list sitting at {@code (h)} claims {@code (i)} before any roman
     * reading is considered.
     */
    static SegmentKind resolveAmbiguous(String label, List<Frame> frames, int indent) {
        for (int i = frames.size() - 1; i >= 0; i--) {
            Frame frame = frames.get(i);
            if (successor(frame.kind(), frame.lastLabel()).filter(label::equals).isPresent()) {
                return frame.kind();
            }
        }
        Optional<Frame> openSubsection = frames.stream()
                .filter(frame -> frame.kind() == SegmentKind.SUBSECTION)
                .findFirst();
        if (openSubsection.isEmpty()) {
            return SegmentKind.SUBSECTION;
        }
        if (label.equals("i") || indent > openSubsection.get().indent()) {
            return SegmentKind.PARAGRAPH;
        }
        return SegmentKind.SUBSECTION;
    }

    /** Build the labelled descendants of one section, in document order. */
    public static List<Segment> nestSection(
            String text, SectionBlock block, int firstIndex, int parent, SegmentPath parentPath) {
        List<LexLine> labelled = block.lines().stream()
                .filter(line -> KIND_FOR_LINE.containsKey(line.kind()) || line.kind() == LineKind.AMBIGUOUS_LABEL)
                .toList();
        if (labelled.isEmpty()) {
            return List.of();
        }
        List<SegmentKind> kinds = assignKinds(labelled);
        List<Segment> segments = new ArrayList<>();
        List<Frame> frames = new ArrayList<>();
        for (int position = 0; position < labelled.size(); position++) {
            LexLine line = labelled.get(position);
            SegmentKind kind = kinds.get(position);
            while (!frames.isEmpty() && frames.getLast().kind().depth() >= kind.depth()) {
                frames.removeLast();
            }
            Frame owner = frames.isEmpty() ? null : frames.getLast();
            int index = firstIndex + position;
            SegmentPath path = (owner != null ? owner.path() : parentPath).child(line.label());
            segments.add(new Segment(
                    index,
                    kind,
                    path,
                    spanFor(text, block, labelled, kinds, position),
                    "",
                    OptionalInt.of(owner != null ? owner.index() : parent)));
            frames.add(new Frame(kind, index, line.label(), line.indent(), path));
        }
        return segments;
    }

    private static List<SegmentKind> assignKinds(List<LexLine> labelled) {
        List<SegmentKind> kinds = new ArrayList<>();
        List<Frame> frames = new ArrayList<>();
        for (LexLine line : labelled) {
            SegmentKind kind = line.kind() == LineKind.AMBIGUOUS_LABEL
                    ? resolveAmbiguous(line.label(), frames, line.indent())
                    : KIND_FOR_LINE.get(line.kind());
            kinds.add(kind);
            while (!frames.isEmpty() && frames.getLast().kind().depth() >= kind.depth()) {
                frames.removeLast();
            }
            frames.add(new Frame(kind, kinds.size() - 1, line.label(), line.indent(), SegmentPath.ROOT));
        }
        return kinds;
    }

    private static TextSpan spanFor(
            String text, SectionBlock block, List<LexLine> labelled, List<SegmentKind> kinds, int position) {
        int start = labelled.get(position).span().start();
        int end = block.span().end();
        int ownDepth = kinds.get(position).depth();
        for (int later = position + 1; later < labelled.size(); later++) {
            if (kinds.get(later).depth() <= ownDepth) {
                end = labelled.get(later).span().start();
                break;
            }
        }
        while (end > start && Text.isSpace(text.charAt(end - 1))) {
            end--;
        }
        return new TextSpan(start, end);
    }
}
