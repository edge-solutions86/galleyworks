"""Plain text rendering for the terminal.

The layout is a header, a summary line, then one block per finding: which
subsection of the query instrument, which instrument and subsection it matches,
the scores, and the passage quoted from both. Quotations are wrapped and
indented so the eye can find the boundary between them.

There is no colour and no box drawing. The output is read as often through
``less`` and in a CI log as in a terminal that supports either.
"""

from __future__ import annotations

import re
import textwrap

from lexdrift.report.model import InstrumentReport, ReportedMatch, ReportedPassage

__all__ = ["render_text"]

_WHITESPACE = re.compile(r"\s+")
_RULE = "-"


def _flow(text: str) -> str:
    return _WHITESPACE.sub(" ", text).strip()


def _quote(label: str, text: str, *, width: int, indent: str = "    ") -> list[str]:
    if not text:
        return [f"{indent}{label}: (not available)"]
    body = textwrap.wrap(
        _flow(text),
        width=max(width - len(indent) - 2, 20),
        initial_indent=f"{indent}{label}: ",
        subsequent_indent=indent + " " * (len(label) + 2),
    )
    return body


def _passage_block(passage: ReportedPassage, *, width: int, position: int) -> list[str]:
    header = (
        f"    passage {position}: {passage.characters} characters, "
        f"query {passage.query_span.start}-{passage.query_span.end}, "
        f"other {passage.other_span.start}-{passage.other_span.end}"
    )
    lines = [header]
    lines.extend(_quote("query", passage.query_text, width=width, indent="      "))
    lines.extend(_quote("other", passage.other_text, width=width, indent="      "))
    return lines


def _match_block(match: ReportedMatch, *, width: int, limit: int) -> list[str]:
    lines = [
        f"  {match.query_path} matches {match.other_key.slug} at {match.other_path}",
        f"    containment {match.containment:.2f}  jaccard {match.jaccard:.2f}  "
        f"{match.characters} characters over {len(match.passages)} passages",
    ]
    for position, passage in enumerate(match.passages[:limit], start=1):
        lines.extend(_passage_block(passage, width=width, position=position))
    hidden = len(match.passages) - limit
    if hidden > 0:
        lines.append(f"    ... {hidden} further passages")
    return lines


def _fit(lines: list[str], width: int) -> list[str]:
    """Wrap any line that overruns ``width``, keeping its indentation.

    Headers carry instrument slugs and paths, so they overrun on a narrow
    terminal exactly as quotations do. Wrapping them here rather than at each
    call site is what makes the width promise hold for the whole document.
    """

    fitted: list[str] = []
    for line in lines:
        if len(line) <= width or not line.strip():
            fitted.append(line)
            continue
        indent = line[: len(line) - len(line.lstrip())]
        fitted.extend(
            textwrap.wrap(
                line.strip(),
                width=max(width, 24),
                initial_indent=indent,
                subsequent_indent=indent + "  ",
            )
        )
    return fitted


def render_text(
    report: InstrumentReport,
    *,
    width: int = 100,
    max_matches: int | None = None,
    max_passages: int = 3,
) -> str:
    """Render a report for a terminal."""

    summary = report.summary
    lines = [
        f"lexdrift report for {report.key.slug}",
        _flow(report.title)[:width],
        _RULE * min(width, 78),
        f"generated {report.generated_at.isoformat(timespec='seconds')}",
        (
            f"{summary.matched_segments} of {summary.total_segments} segments matched, "
            f"{summary.reused_characters} characters reused "
            f"({summary.reuse_ratio:.1%} of the document) "
            f"across {summary.other_instruments} other instruments"
        ),
    ]
    if report.is_empty:
        lines.append("no reuse found")
        return "\n".join(_fit(lines, width)) + "\n"
    shown = report.matches if max_matches is None else report.matches[:max_matches]
    for match in shown:
        lines.append("")
        lines.extend(_match_block(match, width=width, limit=max_passages))
    remaining = len(report.matches) - len(shown)
    if remaining > 0:
        lines.append("")
        lines.append(f"... {remaining} further matches")
    return "\n".join(_fit(lines, width)) + "\n"
