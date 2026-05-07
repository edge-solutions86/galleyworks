"""What every report writer needs and each one had its own copy of.

Four writers, four private ``_flow`` functions collapsing whitespace, three
different ideas of how to trim a passage list, and two spellings of a span in
JSON. They agreed by accident and would have drifted apart at the first change.

Nothing here is clever. It is here so that a passage quoted in the text report
and the same passage quoted in the markdown one are the same string.
"""

from __future__ import annotations

import re
from dataclasses import replace

from lexdrift.report.model import ReportedMatch
from lexdrift.util.spans import TextSpan

__all__ = ["ELLIPSIS", "flow", "quote", "span_payload", "trim_passages"]

ELLIPSIS = "…"

_WHITESPACE = re.compile(r"\s+")


def flow(text: str) -> str:
    """Collapse every run of whitespace so a quotation fits on one line."""

    return _WHITESPACE.sub(" ", text).strip()


def quote(text: str, *, limit: int | None = None, empty: str = "") -> str:
    """A one-line quotation, truncated to ``limit`` characters."""

    flowed = flow(text)
    if not flowed:
        return empty
    if limit is None or len(flowed) <= limit:
        return flowed
    return flowed[: max(1, limit - 1)] + ELLIPSIS


def span_payload(span: TextSpan) -> dict[str, int]:
    """The JSON shape of a span, spelled one way."""

    return {"start": span.start, "end": span.end, "length": span.length}


def trim_passages(match: ReportedMatch, limit: int | None) -> tuple[ReportedMatch, int]:
    """Return the match with at most ``limit`` passages, and how many were hidden."""

    if limit is None or len(match.passages) <= limit:
        return match, 0
    kept = match.passages[:limit]
    return replace(match, passages=kept), len(match.passages) - len(kept)
