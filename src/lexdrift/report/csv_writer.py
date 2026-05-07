"""CSV output, one row per passage.

The row is the passage rather than the match because that is the unit an analyst
sorts and filters on: "show me every passage over four hundred characters that
crossed a state line". A match-level row would have to fold the passages into a
count and throw the spans away.

Quotations are collapsed to a single line. A newline inside a CSV field is legal
and every spreadsheet handles it, but a legislative passage is four lines of
wrapped text and a row that spans four screen lines makes the file unreadable.
"""

from __future__ import annotations

import csv
from typing import TextIO

from lexdrift.report.model import InstrumentReport
from lexdrift.report.shared import quote

__all__ = ["COLUMNS", "flatten", "write_csv"]

COLUMNS = (
    "query_instrument",
    "query_jurisdiction",
    "query_path",
    "other_instrument",
    "other_jurisdiction",
    "other_path",
    "containment",
    "jaccard",
    "characters",
    "query_start",
    "query_end",
    "other_start",
    "other_end",
    "resolved",
    "query_text",
)

_MAX_QUOTE = 400


def flatten(report: InstrumentReport) -> list[dict[str, object]]:
    """One dictionary per passage, keyed by :data:`COLUMNS`."""

    rows: list[dict[str, object]] = []
    for match in report.matches:
        for passage in match.passages:
            rows.append(
                {
                    "query_instrument": report.key.slug,
                    "query_jurisdiction": report.key.jurisdiction,
                    "query_path": match.query_path,
                    "other_instrument": match.other_key.slug,
                    "other_jurisdiction": match.other_key.jurisdiction,
                    "other_path": match.other_path,
                    "containment": f"{match.containment:.6f}",
                    "jaccard": f"{match.jaccard:.6f}",
                    "characters": passage.characters,
                    "query_start": passage.query_span.start,
                    "query_end": passage.query_span.end,
                    "other_start": passage.other_span.start,
                    "other_end": passage.other_span.end,
                    "resolved": "true" if passage.resolved else "false",
                    "query_text": quote(passage.query_text, limit=_MAX_QUOTE),
                }
            )
    return rows


def write_csv(report: InstrumentReport, stream: TextIO, *, header: bool = True) -> int:
    """Write the report as CSV, returning the number of rows written."""

    writer = csv.DictWriter(stream, fieldnames=list(COLUMNS), lineterminator="\n")
    if header:
        writer.writeheader()
    rows = flatten(report)
    writer.writerows(rows)
    return len(rows)
