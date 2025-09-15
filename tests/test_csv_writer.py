from __future__ import annotations

import csv
import io
from datetime import UTC, datetime

from conftest_corpus import LOCAL_CLAUSE, MODEL_CLAUSE, fingerprints, make_bill
from lexdrift.index.memory import MemoryIndex
from lexdrift.match.engine import Matcher
from lexdrift.report.csv_writer import COLUMNS, flatten, write_csv
from lexdrift.report.model import InstrumentReport, MappingTextSource, build_report

FIXED = datetime(2026, 1, 2, 3, 4, 5, tzinfo=UTC)


def report() -> InstrumentReport:
    source_bill = make_bill(jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE)
    probe = make_bill(jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE)
    index = MemoryIndex()
    index.add_all(fingerprints(source_bill))
    matches = Matcher(index).match_instrument(probe)
    return build_report(probe, matches, MappingTextSource([source_bill]), now=FIXED)


def rendered(**kwargs: bool) -> str:
    stream = io.StringIO()
    write_csv(report(), stream, **kwargs)
    return stream.getvalue()


def test_one_row_per_passage() -> None:
    built = report()
    expected = sum(len(match.passages) for match in built.matches)
    assert len(flatten(built)) == expected


def test_every_column_is_filled() -> None:
    row = flatten(report())[0]
    assert set(row) == set(COLUMNS)
    assert all(value != "" for value in row.values())


def test_the_header_names_the_columns() -> None:
    first = rendered().splitlines()[0]
    assert first.split(",") == list(COLUMNS)


def test_the_header_can_be_suppressed() -> None:
    assert not rendered(header=False).startswith("query_instrument")


def test_the_output_parses_as_csv() -> None:
    rows = list(csv.DictReader(io.StringIO(rendered())))
    assert rows
    assert rows[0]["query_instrument"] == "mt/2025/lower/HB0311"
    assert rows[0]["other_jurisdiction"] == "wy"


def test_write_csv_returns_the_row_count() -> None:
    stream = io.StringIO()
    written = write_csv(report(), stream)
    assert written == len(list(csv.DictReader(io.StringIO(stream.getvalue()))))


def test_quotations_are_one_line() -> None:
    for row in flatten(report()):
        assert "\n" not in str(row["query_text"])


def test_a_long_quotation_is_truncated() -> None:
    built = report()
    rows = flatten(built)
    assert all(len(str(row["query_text"])) <= 400 for row in rows)


def test_spans_are_integers() -> None:
    row = flatten(report())[0]
    assert int(row["query_end"]) > int(row["query_start"])
    assert int(row["characters"]) > 0


def test_scores_are_fixed_width() -> None:
    row = flatten(report())[0]
    assert str(row["containment"]).count(".") == 1
    assert len(str(row["containment"]).split(".")[1]) == 6


def test_an_empty_report_writes_only_a_header() -> None:
    probe = make_bill(jurisdiction="co", number="SB0007", body=LOCAL_CLAUSE)
    stream = io.StringIO()
    written = write_csv(build_report(probe, [], now=FIXED), stream)
    assert written == 0
    assert stream.getvalue().strip() == ",".join(COLUMNS)


def test_rows_use_unix_line_endings() -> None:
    assert "\r\n" not in rendered()
