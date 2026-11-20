package io.github.rkaushikdev.galleyworks.support;

import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.ingest.EnrolledReader;
import io.github.rkaushikdev.galleyworks.ingest.SourceDocument;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A small corpus with a known answer: six instruments in two families and one singleton.
 *
 * <p>The state family: Wyoming enacts a scheduling provision in 2023, Montana enacts a lightly
 * reworded version of it in 2024, and North Dakota enacts a version with one subsection dropped in
 * 2025. Wyoming is the root. The municipal family: Laramie adopts a short term rental ordinance in
 * 2024 and Cheyenne adopts the same text in 2025 with one number changed, which checks that a second
 * family is found separately and that municipal section numbering does not read as a state
 * citation. Colorado enacts something unrelated and belongs to neither. The texts live in code so
 * a change to them shows up in a diff as a change to the fixture.
 */
public final class GoldenCorpus {

    public static final String WYOMING =
            """
            2023

            STATE OF WYOMING                                   23LSO-0142

            HOUSE BILL NO. HB0142

            Water rights; publication schedule.

            AN ACT relating to water rights; requiring the board to publish a schedule of
            appropriations; specifying the contents of the schedule; and providing for an
            effective date.

            Be It Enacted by the Legislature of the State of Wyoming:

            Section 1.  W.S. 41-3-101 is amended to read:

            41-3-101.  Schedule of appropriations.

            (a)  The board shall publish a schedule of appropriations of record not later
            than sixty (60) days after the close of each irrigation season. The schedule
            shall state the priority date of each appropriation, the point of diversion,
            the acreage irrigated and the name of the appropriator of record.

            (b)  The board shall make the schedule available for public inspection at the
            office of the division superintendent and shall publish it on the website
            maintained by the board.

                (i)  The board may charge a fee, not to exceed twenty-five dollars
                ($25.00), for a certified copy of the schedule.

                (ii)  No fee may be charged for inspection of the schedule at the office
                of the division superintendent.

            Section 2.  This act is effective July 1, 2023.
            """;

    public static final String MONTANA =
            """
            2024

            HOUSE BILL NO. HB0311

            AN ACT relating to water rights; requiring the department to publish a schedule
            of appropriations; and providing an effective date.

            Be It Enacted by the Legislature of the State of Montana:

            Section 1.  85-2-101 is amended to read:

            85-2-101.  Schedule of appropriations.

            (a)  The department shall publish a schedule of appropriations of record not
            later than sixty (60) days after the close of each irrigation season. The
            schedule shall state the priority date of each appropriation, the point of
            diversion, the acreage irrigated and the name of the appropriator of record.

            (b)  The department shall make the schedule available for public inspection at
            the office of the water commissioner and shall publish it on the website
            maintained by the department.

                (i)  The department may charge a fee, not to exceed twenty-five dollars
                ($25.00), for a certified copy of the schedule.

                (ii)  No fee may be charged for inspection of the schedule at the office
                of the water commissioner.

            Section 2.  This act is effective October 1, 2024.
            """;

    public static final String NORTH_DAKOTA =
            """
            2025

            HOUSE BILL NO. HB0044

            AN ACT relating to water appropriations; requiring publication of a schedule.

            Be It Enacted by the Legislative Assembly of the State of North Dakota:

            Section 1.  61-04-01 is amended to read:

            61-04-01.  Schedule of appropriations.

            (a)  The commission shall publish a schedule of appropriations of record not
            later than ninety (90) days after the close of each irrigation season. The
            schedule shall state the priority date of each appropriation, the point of
            diversion, the acreage irrigated and the name of the appropriator of record.

            Section 2.  This act is effective August 1, 2025.
            """;

    public static final String COLORADO =
            """
            2025

            SENATE BILL NO. SB0007

            AN ACT concerning county fair premiums.

            Be It Enacted by the General Assembly of the State of Colorado:

            Section 1.  35-65-101 is amended to read:

            35-65-101.  County fair premiums.

            (a)  A board of county commissioners may appropriate money for premiums to be
            awarded at a county fair held within the county. Premiums awarded under this
            section are payable only to an exhibitor who resides in the county and who
            entered the exhibit in the exhibitor's own name.

            Section 2.  This act is effective September 1, 2025.
            """;

    public static final String LARAMIE =
            """
            2024

            ORDINANCE NO. 2024-31

            AN ORDINANCE concerning short term rentals.

            Be It Enacted by the Governing Body of the City of Laramie:

            Section 1.  15.04.020 is amended to read:

            15.04.020.  Short term rental permits.

            (a)  No dwelling unit may be offered as a short term rental unless the owner
            holds a current permit issued under this chapter. An application for a permit
            shall state the address of the dwelling unit, the number of bedrooms offered,
            the name of a local contact person available at all hours and proof of
            liability insurance in an amount set by resolution of the governing body.

            (b)  A permit expires one (1) year after the date of issuance and is not
            transferable to another owner or to another dwelling unit.

            Section 2.  This ordinance is effective April 1, 2024.
            """;

    public static final String CHEYENNE =
            """
            2025

            ORDINANCE NO. 2025-08

            AN ORDINANCE concerning short term rentals.

            Be It Enacted by the Governing Body of the City of Cheyenne:

            Section 1.  9.28.040 is amended to read:

            9.28.040.  Short term rental permits.

            (a)  No dwelling unit may be offered as a short term rental unless the owner
            holds a current permit issued under this chapter. An application for a permit
            shall state the address of the dwelling unit, the number of bedrooms offered,
            the name of a local contact person available at all hours and proof of
            liability insurance in an amount set by resolution of the governing body.

            (b)  A permit expires two (2) years after the date of issuance and is not
            transferable to another owner or to another dwelling unit.

            Section 2.  This ordinance is effective July 1, 2025.
            """;

    /**
     * One corpus entry.
     *
     * @param jurisdiction the enacting jurisdiction
     * @param session the session
     * @param number the printed number
     * @param text the instrument as printed
     * @param enacted when it took effect
     */
    public record Entry(String jurisdiction, String session, String number, String text, LocalDate enacted) {}

    public static final List<Entry> CORPUS = List.of(
            new Entry("wy", "2023", "HB0142", WYOMING, LocalDate.of(2023, 7, 1)),
            new Entry("mt", "2024", "HB0311", MONTANA, LocalDate.of(2024, 10, 1)),
            new Entry("nd", "2025", "HB0044", NORTH_DAKOTA, LocalDate.of(2025, 8, 1)),
            new Entry("co", "2025", "SB0007", COLORADO, LocalDate.of(2025, 9, 1)),
            new Entry("laramie", "2024", "2024-31", LARAMIE, LocalDate.of(2024, 4, 1)),
            new Entry("cheyenne", "2025", "2025-08", CHEYENNE, LocalDate.of(2025, 7, 1)));

    private GoldenCorpus() {}

    /** Parse the corpus in enactment order. */
    public static List<Instrument> goldenInstruments() {
        EnrolledReader reader = new EnrolledReader();
        List<Instrument> found = new ArrayList<>();
        for (Entry entry : CORPUS) {
            SourceDocument document = new SourceDocument(
                    entry.text(),
                    entry.jurisdiction() + "_" + entry.session() + "_" + entry.number() + ".txt",
                    Map.of("jurisdiction", entry.jurisdiction(), "session", entry.session(), "number", entry.number()));
            found.add(reader.read(document));
        }
        return found;
    }
}
