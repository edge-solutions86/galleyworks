"""JSON output.

The shape is versioned and flat enough to query with ``jq``. Spans are objects
rather than two-element arrays, because a report is read by people as often as
by programs and ``{"start": 412, "end": 690}`` needs no key.

Passage text is included by default. It is the bulk of the payload, so
``include_text=False`` is there for the case where the consumer already holds
both documents and only wants the offsets.
"""

from __future__ import annotations

import json
from typing import Any, TextIO

from lexdrift.report.model import InstrumentReport, ReportedMatch, ReportedPassage
from lexdrift.util.spans import TextSpan

__all__ = ["FORMAT_VERSION", "dumps", "to_mapping", "write_json"]

FORMAT_VERSION = 1


def _span(span: TextSpan) -> dict[str, int]:
    return {"start": span.start, "end": span.end, "length": span.length}


def _passage(passage: ReportedPassage, *, include_text: bool) -> dict[str, Any]:
    payload: dict[str, Any] = {
        "query": _span(passage.query_span),
        "other": _span(passage.other_span),
        "characters": passage.characters,
        "resolved": passage.resolved,
    }
    if include_text:
        payload["query_text"] = passage.query_text
        payload["other_text"] = passage.other_text
    return payload


def _match(match: ReportedMatch, *, include_text: bool) -> dict[str, Any]:
    return {
        "query_path": match.query_path,
        "other": {
            "instrument": match.other_key.slug,
            "jurisdiction": match.other_key.jurisdiction,
            "session": match.other_key.session,
            "number": match.other_key.number,
            "path": match.other_path,
        },
        "containment": round(match.containment, 6),
        "jaccard": round(match.jaccard, 6),
        "characters": match.characters,
        "resolved": match.resolved,
        "passages": [_passage(passage, include_text=include_text) for passage in match.passages],
    }


def to_mapping(report: InstrumentReport, *, include_text: bool = True) -> dict[str, Any]:
    """Render a report as plain JSON-ready data."""

    summary = report.summary
    return {
        "format": FORMAT_VERSION,
        "instrument": {
            "key": report.key.slug,
            "jurisdiction": report.key.jurisdiction,
            "session": report.key.session,
            "chamber": report.key.chamber.value,
            "number": report.key.number,
            "title": report.title,
        },
        "generated_at": report.generated_at.isoformat(),
        "summary": {
            "matched_segments": summary.matched_segments,
            "total_segments": summary.total_segments,
            "reused_characters": summary.reused_characters,
            "document_characters": summary.document_characters,
            "other_instruments": summary.other_instruments,
            "reuse_ratio": round(summary.reuse_ratio, 6),
        },
        "matches": [_match(match, include_text=include_text) for match in report.matches],
    }


def dumps(report: InstrumentReport, *, indent: int | None = 2, include_text: bool = True) -> str:
    """Serialize a report to a JSON string."""

    return json.dumps(
        to_mapping(report, include_text=include_text),
        indent=indent,
        ensure_ascii=False,
        sort_keys=False,
    )


def write_json(
    report: InstrumentReport,
    stream: TextIO,
    *,
    indent: int | None = 2,
    include_text: bool = True,
) -> None:
    """Write a report as JSON, newline terminated."""

    stream.write(dumps(report, indent=indent, include_text=include_text))
    stream.write("\n")
