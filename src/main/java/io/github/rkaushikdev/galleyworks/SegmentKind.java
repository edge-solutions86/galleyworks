package io.github.rkaushikdev.galleyworks;

import java.util.Arrays;

/** The structural role of a segment inside an instrument. */
public enum SegmentKind {
    PREAMBLE("preamble", 0, false),
    ENACTING("enacting", 0, false),
    TITLE("title", 1, false),
    ARTICLE("article", 2, true),
    SECTION("section", 3, true),
    SUBSECTION("subsection", 4, true),
    PARAGRAPH("paragraph", 5, true),
    SUBPARAGRAPH("subparagraph", 6, true),
    ITEM("item", 7, true);

    private final String value;
    private final int depth;
    private final boolean body;

    SegmentKind(String value, int depth, boolean body) {
        this.value = value;
        this.depth = depth;
        this.body = body;
    }

    /** The lowercase name written in reports and the index. */
    public String value() {
        return value;
    }

    /** Nesting depth used when a parser has to decide where a label belongs. */
    public int depth() {
        return depth;
    }

    /**
     * True for enacted substance.
     *
     * <p>The long title, the preamble and the enacting clause all describe the act rather than
     * enact anything, and every act in a jurisdiction phrases them alike. Fingerprinting them would
     * report that similarity as reuse.
     */
    public boolean isBody() {
        return body;
    }

    /** The kind whose value is {@code value}. */
    public static SegmentKind fromValue(String value) {
        return Arrays.stream(values())
                .filter(candidate -> candidate.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("'" + value + "' is not a valid SegmentKind"));
    }

    @Override
    public String toString() {
        return value;
    }
}
