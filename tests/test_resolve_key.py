from __future__ import annotations

from datetime import date

import pytest

from lexdrift.errors import IngestError
from lexdrift.identity import Chamber
from lexdrift.ingest.base import SourceDocument, resolve_key
from lexdrift.ingest.enrolled import EnrolledReader
from lexdrift.ingest.metadata import BillHeader

FULL_HEADER = BillHeader(number="HB0142", chamber=Chamber.LOWER, session="2025")


def source(**hints: object) -> SourceDocument:
    return SourceDocument(text="x", origin="t.txt", hints=dict(hints))


def test_hints_alone_are_enough() -> None:
    key = resolve_key(source(jurisdiction="wy", session="2025", number="HB0142"))
    assert key.slug == "wy/2025/lower/HB0142"


def test_the_header_fills_in_what_the_hints_omit() -> None:
    key = resolve_key(source(jurisdiction="wy"), FULL_HEADER)
    assert key.slug == "wy/2025/lower/HB0142"


def test_hints_win_over_the_header() -> None:
    key = resolve_key(source(jurisdiction="wy", number="HB0999", chamber="upper"), FULL_HEADER)
    assert key.number == "HB0999"
    assert key.chamber is Chamber.UPPER


def test_jurisdiction_is_never_inferred() -> None:
    with pytest.raises(IngestError, match="missing identity hints") as caught:
        resolve_key(source(), FULL_HEADER)
    assert caught.value.context["missing"] == "jurisdiction"


def test_every_missing_field_is_named() -> None:
    with pytest.raises(IngestError) as caught:
        resolve_key(source())
    assert caught.value.context["missing"] == "jurisdiction,session,number"


def test_chamber_defaults_to_lower_when_nothing_says_otherwise() -> None:
    key = resolve_key(source(jurisdiction="wy", session="2025", number="HB1"))
    assert key.chamber is Chamber.LOWER


def test_chamber_comes_from_the_header_when_hints_are_silent() -> None:
    header = BillHeader(number="SF0032", chamber=Chamber.UPPER, session="2025")
    assert resolve_key(source(jurisdiction="wy"), header).chamber is Chamber.UPPER


def test_an_unknown_chamber_hint_is_rejected() -> None:
    with pytest.raises(IngestError, match="unknown chamber"):
        resolve_key(source(jurisdiction="wy", session="2025", number="HB1", chamber="senate"))


def test_the_reader_identifies_a_bill_from_its_front_matter(water_bill_text: str) -> None:
    document = SourceDocument(text=water_bill_text, origin="x", hints={"jurisdiction": "wy"})
    instrument = EnrolledReader().read(document)
    assert instrument.key.slug == "wy/2025/lower/HB0142"


def test_the_reader_reads_the_effective_date(water_bill_text: str) -> None:
    document = SourceDocument(text=water_bill_text, origin="x", hints={"jurisdiction": "wy"})
    instrument = EnrolledReader().read(document)
    assert instrument.enacted_on == date(2025, 7, 1)
    assert instrument.effective_date == date(2025, 7, 1)


def test_a_bill_with_no_effective_clause_has_no_date(water_bill_text: str) -> None:
    trimmed = water_bill_text.replace("This act is effective July 1, 2025.", "This act is void.")
    document = SourceDocument(text=trimmed, origin="x", hints={"jurisdiction": "wy"})
    assert EnrolledReader().read(document).enacted_on is None
