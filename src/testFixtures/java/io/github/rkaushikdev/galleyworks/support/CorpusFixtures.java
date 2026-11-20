package io.github.rkaushikdev.galleyworks.support;

import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.fingerprint.Fingerprinter;
import io.github.rkaushikdev.galleyworks.fingerprint.SegmentFingerprint;
import io.github.rkaushikdev.galleyworks.ingest.EnrolledReader;
import io.github.rkaushikdev.galleyworks.ingest.SourceDocument;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Corpus helpers shared by the index, match and diffusion tests. */
public final class CorpusFixtures {

    public static final String MODEL_CLAUSE =
            "The board shall publish a schedule of appropriations not later than sixty\n"
                    + "(60) days after the close of each irrigation season. The schedule shall\n"
                    + "state the priority date of each appropriation, the point of diversion and\n"
                    + "the acreage irrigated under the appropriation.";

    public static final String LOCAL_CLAUSE =
            "The commission shall record every temporary permit in a register kept at\n"
                    + "the county seat and shall make the register available for inspection during\n"
                    + "regular business hours.";

    private CorpusFixtures() {}

    /** A one-section bill amending W.S. 41-3-101 with {@code body} as its subsection (a). */
    public static Instrument makeBill(String jurisdiction, String number, String body) {
        return makeBill(jurisdiction, number, body, "2025", null);
    }

    /** As {@link #makeBill(String, String, String)}, effective on {@code enacted}. */
    public static Instrument makeBill(String jurisdiction, String number, String body, LocalDate enacted) {
        return makeBill(jurisdiction, number, body, "2025", enacted);
    }

    /** A bill in {@code session}, effective on {@code enacted} when it is given. */
    public static Instrument makeBill(
            String jurisdiction, String number, String body, String session, LocalDate enacted) {
        StringBuilder text = new StringBuilder()
                .append(session)
                .append("\n\n")
                .append("HOUSE BILL NO. ")
                .append(number)
                .append("\n\n")
                .append("AN ACT relating to water rights.\n\n")
                .append("Be It Enacted by the Legislature:\n\n")
                .append("Section 1.  W.S. 41-3-101 is amended to read:\n\n")
                .append("41-3-101.  Schedule of appropriations.\n\n")
                .append("(a)  ")
                .append(body)
                .append('\n');
        if (enacted != null) {
            text.append("\nSection 2.  This act is effective ")
                    .append(enacted.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                    .append(' ')
                    .append(enacted.getDayOfMonth())
                    .append(", ")
                    .append(enacted.getYear())
                    .append(".\n");
        }
        SourceDocument document = new SourceDocument(
                text.toString(),
                jurisdiction + "-" + number + ".txt",
                Map.of("jurisdiction", jurisdiction, "session", session, "number", number));
        return new EnrolledReader().read(document);
    }

    /** The records the default fingerprinter makes of {@code instrument}. */
    public static List<SegmentFingerprint> fingerprints(Instrument instrument) {
        return new Fingerprinter().fingerprint(instrument);
    }
}
