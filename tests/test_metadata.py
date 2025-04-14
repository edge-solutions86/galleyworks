from __future__ import annotations

from datetime import date

import pytest

from lexdrift.identity import Chamber
from lexdrift.ingest.metadata import BillHeader, parse_effective_date, read_header

FRONT = [
    "2025",
    "",
    "STATE OF WYOMING                                   25LSO-0142",
    "",
    "HOUSE BILL NO. HB0142",
    "",
    "Water rights; publication schedule.",
    "",
    "Sponsored by: Joint Agriculture Committee",
]


def test_reads_the_printed_identity() -> None:
    header = read_header(FRONT)
    assert header.number == "HB0142"
    assert header.chamber is Chamber.LOWER
    assert header.session == "2025"
    assert header.sponsor == "Joint Agriculture Committee"
    assert header.is_complete


@pytest.mark.parametrize(
    ("line", "chamber"),
    [
        ("HOUSE BILL NO. HB0142", Chamber.LOWER),
        ("SENATE FILE NO. SF0032", Chamber.UPPER),
        ("SENATE BILL NO. 14", Chamber.UPPER),
        ("COUNCIL BILL NO. 2024-31", Chamber.COUNCIL),
        ("ORDINANCE NO. 1188", Chamber.COUNCIL),
        ("EXECUTIVE ORDER NO. 2025-04", Chamber.EXECUTIVE),
    ],
)
def test_chamber_words(line: str, chamber: Chamber) -> None:
    assert read_header([line]).chamber is chamber


def test_number_strips_spaces_and_dashes() -> None:
    assert read_header(["COUNCIL BILL NO. 2024-31"]).number == "202431"
    assert read_header(["SENATE FILE NO. SF 0032"]).number == "SF0032"


def test_the_first_chamber_word_wins() -> None:
    header = read_header(["HOUSE BILL NO. HB0142", "referred to the SENATE FILE calendar"])
    assert header.chamber is Chamber.LOWER
    assert header.number == "HB0142"


def test_a_number_without_a_chamber_is_ignored() -> None:
    assert read_header(["DOCKET NO. 1234"]).number is None


def test_blank_header_is_incomplete() -> None:
    header = read_header(["", "   "])
    assert header == BillHeader()
    assert not header.is_complete


def test_session_takes_the_first_four_digit_year() -> None:
    assert read_header(["2025 GENERAL SESSION", "amends the 1977 act"]).session == "2025"


def test_a_year_like_number_is_not_confused_with_a_session() -> None:
    assert read_header(["HOUSE BILL NO. HB0142"]).session is None


def test_sponsor_is_trimmed() -> None:
    assert read_header(["Sponsored by:    Senator Case  "]).sponsor == "Senator Case"


def test_effective_date_is_parsed() -> None:
    assert parse_effective_date("This act is effective July 1, 2025.") == date(2025, 7, 1)


def test_effective_date_is_case_insensitive() -> None:
    assert parse_effective_date("EFFECTIVE MARCH 15, 2026") == date(2026, 3, 15)


def test_effective_date_returns_none_when_absent() -> None:
    assert parse_effective_date("This act takes effect immediately.") is None


def test_impossible_dates_are_rejected() -> None:
    assert parse_effective_date("effective February 30, 2025") is None
    assert parse_effective_date("effective Smarch 3, 2025") is None
