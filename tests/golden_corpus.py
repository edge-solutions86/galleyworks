"""A small corpus with a known answer.

Six instruments in two families and one singleton.

The state family: Wyoming enacts a scheduling provision in 2023, Montana enacts
a lightly reworded version of it in 2024, and North Dakota enacts a version with
one subsection dropped in 2025. Wyoming is the root.

The municipal family: Laramie adopts a short term rental ordinance in 2024 and
Cheyenne adopts the same text in 2025 with one number changed. It is here to
check that a second family is found separately rather than merged into the
first, and that municipal section numbering does not read as a state citation.

Colorado enacts something unrelated and belongs to neither.

The texts are held here as Python rather than as data files so that a change to
them shows up in a diff as a change to the fixture, and so that the golden test
cannot drift out of step with a file nobody remembers writing.
"""

from __future__ import annotations

from datetime import date

from lexdrift.ingest.base import SourceDocument
from lexdrift.ingest.enrolled import EnrolledReader
from lexdrift.models import Instrument

WYOMING = """2023

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
"""

MONTANA = """2024

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
"""

NORTH_DAKOTA = """2025

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
"""

COLORADO = """2025

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
"""

LARAMIE = """2024

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
"""

CHEYENNE = """2025

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
"""


CORPUS: tuple[tuple[str, str, str, str, date], ...] = (
    ("wy", "2023", "HB0142", WYOMING, date(2023, 7, 1)),
    ("mt", "2024", "HB0311", MONTANA, date(2024, 10, 1)),
    ("nd", "2025", "HB0044", NORTH_DAKOTA, date(2025, 8, 1)),
    ("co", "2025", "SB0007", COLORADO, date(2025, 9, 1)),
    ("laramie", "2024", "2024-31", LARAMIE, date(2024, 4, 1)),
    ("cheyenne", "2025", "2025-08", CHEYENNE, date(2025, 7, 1)),
)


def golden_instruments() -> list[Instrument]:
    """Parse the corpus in enactment order."""

    reader = EnrolledReader()
    found: list[Instrument] = []
    for jurisdiction, session, number, text, _ in CORPUS:
        document = SourceDocument(
            text=text,
            origin=f"{jurisdiction}_{session}_{number}.txt",
            hints={"jurisdiction": jurisdiction, "session": session, "number": number},
        )
        found.append(reader.read(document))
    return found
