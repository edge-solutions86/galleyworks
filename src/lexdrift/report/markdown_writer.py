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

from typing import TextIO

from lexdrift.report.model import InstrumentReport, ReportedMatch
from lexdrift.report.shared import flow, quote, trim_passages

__all__ = ["to_markdown", "write_markdown"]

_FENCE = "```"


def _escape_cell(text: str) -> str:
    return flow(text).replace("|", r"\|")


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
        f"| confidence | {match.confidence.value} |",
        "",
    ]
    if match.reasons:
        lines.append("Why this was reported:")
        lines.append("")
        lines.extend(f"- {flow(reason)}" for reason in match.reasons)
        lines.append("")
    for position, passage in enumerate(match.passages, start=1):
        lines.append(f"**Passage {position}** ({passage.characters} characters)")
        lines.append("")
        lines.append(_FENCE)
        lines.append(quote(passage.query_text, empty="(empty)"))
        lines.append(_FENCE)
        lines.append("")
        lines.append(_FENCE)
        lines.append(quote(passage.other_text, empty="(not available)"))
        lines.append(_FENCE)
        lines.append("")
    return lines


def to_markdown(report: InstrumentReport, *, max_passages: int = 3) -> str:
    """Render a report as markdown."""

    summary = report.summary
    lines = [
        f"# Reuse report: {report.key.slug}",
        "",
        flow(report.title),
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
        trimmed, hidden = trim_passages(match, max_passages)
        lines.extend(_match_section(trimmed, index=index))
        if hidden > 0:
            lines.append(f"_{hidden} further passages omitted._")
            lines.append("")
    return "\n".join(lines).rstrip() + "\n"


def write_markdown(report: InstrumentReport, stream: TextIO, *, max_passages: int = 3) -> None:
    """Write a report as markdown."""

    stream.write(to_markdown(report, max_passages=max_passages))
