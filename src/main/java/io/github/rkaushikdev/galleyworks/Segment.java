package io.github.rkaushikdev.galleyworks;

import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.OptionalInt;

/**
 * One addressable unit of enacted text.
 *
 * <p>A segment never copies the text it covers; it carries a span into its instrument's raw text,
 * so a passage found by the matcher can always be quoted back in the exact words the legislature
 * printed.
 *
 * @param index position in the instrument's segment list
 * @param kind structural role
 * @param path printed label chain
 * @param span the characters it covers in the raw text
 * @param heading the catchline printed with it, or empty
 * @param parent index of the enclosing segment, which always precedes it
 */
public record Segment(
        int index, SegmentKind kind, SegmentPath path, TextSpan span, String heading, OptionalInt parent) {

    /** Validates the index and the parent link. */
    public Segment {
        if (index < 0) {
            throw new IngestError("segment index must be non-negative", Context.of("index", index));
        }
        if (parent.isPresent() && parent.getAsInt() >= index) {
            throw new IngestError(
                    "segment parent must precede the child", Context.of("index", index, "parent", parent.getAsInt()));
        }
    }

    /** A top-level segment with no heading. */
    public Segment(int index, SegmentKind kind, SegmentPath path, TextSpan span) {
        this(index, kind, path, span, "", OptionalInt.empty());
    }

    /** A segment under {@code parent}, with no heading. */
    public Segment(int index, SegmentKind kind, SegmentPath path, TextSpan span, int parent) {
        this(index, kind, path, span, "", OptionalInt.of(parent));
    }

    /** The text this segment covers in {@code instrument}. */
    public String textOf(Instrument instrument) {
        return span.sliceOf(instrument.rawText());
    }
}
