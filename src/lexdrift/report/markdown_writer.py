"""Markdown output.

A reuse report is circulated before it is acted on, and the people it goes to
read it in a ticket or a pull request rather than in a terminal. Markdown is
what those render.

Quotations are fenced rather than indented. Legislative text starts with ``(a)``
and a four-space indent inside a list turns that into a nested list, which
renumbers the subsection in the rendered output. Fencing is the only form that
survives every renderer intact.
"""

from __future__ import annotations

import re
from typing import TextIO

from lexdrift.report.model import InstrumentReport, ReportedMatch

__all__ = ["to_markdown", "write_markdown"]

_WHITESPACE = re.compile(r"\s+")
_FENCE = "```"


def _flow(text: str) -> str:
    return _WHITESPACE.sub(" ", text).strip()


def _escape_cell(text: str) -> str:
    return _flow(text).replace("|", r"\|")


def _match_section(match: ReportedMatch, *, index: int) -> list[str]:
    lines = [
        f"### {index}. {match.query_path} into {match.other_key.slug} {match.other_path}",
        "",
        "| measure | value |",
        "| --- | --- |",
        f"| containment | {match.containment:.2f} |",
        f"| jaccard | {match.jaccard:.2f} |",
        f"| characters | {match.characters} |",
        f"| passages | {len(match.passages)} |",
        f"| other jurisdiction | {_escape_cell(match.other_key.jurisdiction)} |",
        "",
    ]
    for position, passage in enumerate(match.passages, start=1):
        lines.append(f"**Passage {position}** ({passage.characters} characters)")
        lines.append("")
        lines.append(_FENCE)
        lines.append(_flow(passage.query_text) or "(empty)")
        lines.append(_FENCE)
        lines.append("")
        lines.append(_FENCE)
        lines.append(_flow(passage.other_text) or "(not available)")
        lines.append(_FENCE)
        lines.append("")
    return lines


def to_markdown(report: InstrumentReport, *, max_passages: int = 3) -> str:
    """Render a report as markdown."""

    summary = report.summary
    lines = [
        f"# Reuse report: {report.key.slug}",
        "",
        _flow(report.title),
        "",
        f"Generated {report.generated_at.isoformat(timespec='seconds')}.",
        "",
        "## Summary",
        "",
        "| measure | value |",
        "| --- | --- |",
        f"| segments matched | {summary.matched_segments} of {summary.total_segments} |",
        f"| characters reused | {summary.reused_characters} |",
        f"| document characters | {summary.document_characters} |",
        f"| reuse ratio | {summary.reuse_ratio:.1%} |",
        f"| other instruments | {summary.other_instruments} |",
        "",
    ]
    if report.is_empty:
        lines.append("No reuse found.")
        return "\n".join(lines) + "\n"
    lines.append("## Findings")
    lines.append("")
    for index, match in enumerate(report.matches, start=1):
        trimmed = ReportedMatch(
            query_path=match.query_path,
            other_key=match.other_key,
            other_path=match.other_path,
            containment=match.containment,
            jaccard=match.jaccard,
            passages=match.passages[:max_passages],
        )
        lines.extend(_match_section(trimmed, index=index))
        hidden = len(match.passages) - len(trimmed.passages)
        if hidden > 0:
            lines.append(f"_{hidden} further passages omitted._")
            lines.append("")
    return "\n".join(lines).rstrip() + "\n"


def write_markdown(report: InstrumentReport, stream: TextIO, *, max_passages: int = 3) -> None:
    """Write a report as markdown."""

    stream.write(to_markdown(report, max_passages=max_passages))
