package io.github.rkaushikdev.galleyworks.support;

import io.github.rkaushikdev.galleyworks.ingest.SourceDocument;
import java.util.LinkedHashMap;
import java.util.Map;

/** The water bill most ingest and CLI tests read, and the hints it is filed under. */
public final class Fixtures {

    public static final String WATER_BILL =
            """
            2025

            STATE OF WYOMING                                   25LSO-0142

            HOUSE BILL NO. HB0142

            Water rights; publication schedule.

            AN ACT relating to water rights; requiring the board to publish a schedule of
            appropriations; specifying contents of the schedule; and providing for an
            effective date.

            Be It Enacted by the Legislature of the State of Wyoming:

            Section 1.  W.S. 41-3-101 is amended to read:

            41-3-101.  Schedule of appropriations.

            (a)  The board shall publish a schedule of water rights not later than sixty
            (60) days after the close of each irrigation season.

                (i)  The schedule shall include the priority date of each appropriation;

                (ii)  The schedule shall be published on the board's website.

            (b)  This section does not apply to a temporary appropriation.

            Section 2.  This act is effective July 1, 2025.
            """;

    public static final Map<String, Object> DEFAULT_HINTS =
            Map.of("jurisdiction", "wy", "session", "2025", "number", "HB0142", "chamber", "lower");

    private Fixtures() {}

    /** The water bill as a source document filed under the default hints. */
    public static SourceDocument waterBill() {
        return new SourceDocument(WATER_BILL, "hb0142.txt", DEFAULT_HINTS);
    }

    /** {@code text} filed under the default hints, with {@code hints} laid over them. */
    public static SourceDocument makeSource(String text, Map<String, Object> hints) {
        Map<String, Object> merged = new LinkedHashMap<>(DEFAULT_HINTS);
        merged.putAll(hints);
        return new SourceDocument(text, "t.txt", merged);
    }

    /** {@code text} filed under the default hints. */
    public static SourceDocument makeSource(String text) {
        return makeSource(text, Map.of());
    }
}
