"""Corpus helpers shared by the index, match and diffusion tests."""

from __future__ import annotations

from datetime import date

from lexdrift.fingerprint.records import Fingerprinter, SegmentFingerprint
from lexdrift.ingest.base import SourceDocument
from lexdrift.ingest.enrolled import EnrolledReader
from lexdrift.models import Instrument

MODEL_CLAUSE = (
    "The board shall publish a schedule of appropriations not later than sixty\n"
    "(60) days after the close of each irrigation season. The schedule shall\n"
    "state the priority date of each appropriation, the point of diversion and\n"
    "the acreage irrigated under the appropriation."
)

LOCAL_CLAUSE = (
    "The commission shall record every temporary permit in a register kept at\n"
    "the county seat and shall make the register available for inspection during\n"
    "regular business hours."
)


def make_bill(
    *,
    jurisdiction: str,
    number: str,
    body: str,
    session: str = "2025",
    enacted: date | None = None,
) -> Instrument:
    text = (
        "AN ACT relating to water rights.\n\n"
        "Be It Enacted by the Legislature:\n\n"
        "Section 1.  W.S. 41-3-101 is amended to read:\n\n"
        "41-3-101.  Schedule of appropriations.\n\n"
        f"(a)  {body}\n"
    )
    if enacted is not None:
        text += f"\nSection 2.  This act is effective {enacted:%B} {enacted.day}, {enacted.year}.\n"
    document = SourceDocument(
        text=text,
        origin=f"{jurisdiction}-{number}.txt",
        hints={"jurisdiction": jurisdiction, "session": session, "number": number},
    )
    return EnrolledReader().read(document)


def fingerprints(instrument: Instrument) -> list[SegmentFingerprint]:
    return Fingerprinter().fingerprint(instrument)
