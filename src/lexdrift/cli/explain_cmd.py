"""``lexdrift explain`` - the case for each finding, without the quotations.

``query`` prints the evidence; this prints the argument. It is what goes into a
memo: one line per finding saying what matched what and how strongly, then the
reasons, and nothing else.

Quotations are deliberately absent. A passage runs to several lines and three
findings fill a screen, which is exactly the problem this command exists to
avoid.
"""

from __future__ import annotations

import click

from lexdrift.cli.common import build_engine, display_options, engine_options, narrow
from lexdrift.cli.main import EXIT_FINDINGS, EXIT_OK, Runtime, cli
from lexdrift.ingest.base import SourceDocument
from lexdrift.match.explain import Confidence
from lexdrift.report.model import InstrumentReport

__all__ = ["explain", "render_case"]


def render_case(report: InstrumentReport) -> list[str]:
    """One block per finding, argument only."""

    summary = report.summary
    lines = [
        f"{report.key.slug}: {summary.matched_segments} of {summary.total_segments} "
        f"segments matched, {summary.reuse_ratio:.1%} of the document reused"
    ]
    if report.is_empty:
        lines.append("  no reuse found")
        return lines
    for position, match in enumerate(report.matches, start=1):
        lines.append("")
        lines.append(f"{position}. {match.headline}")
        lines.append(
            f"   {match.characters} characters over {len(match.passages)} passages, "
            f"containment {match.containment:.2f}"
        )
        lines.extend(f"   - {reason}" for reason in match.reasons)
    return lines


@cli.command(name="explain", short_help="print the case for each finding")
@click.argument("document")
@engine_options(index_required=True)
@display_options
@click.option("--jurisdiction", required=True, help="jurisdiction slug for the document")
@click.option("--session", default=None, help="session, when the text does not say")
@click.option("--number", default=None, help="instrument number, when the text omits it")
@click.pass_obj
def explain(
    runtime: Runtime,
    document: str,
    index_path: str | None,
    config_path: str | None,
    profile: str | None,
    reading: str | None,
    min_confidence: str | None,
    crossing_only: bool,
    top: int | None,
    jurisdiction: str,
    session: str | None,
    number: str | None,
) -> int:
    """Explain what DOCUMENT reuses, without quoting it."""

    engine = build_engine(index_path, config_path, profile, reading)
    hints: dict[str, str] = {"jurisdiction": jurisdiction}
    if session is not None:
        hints["session"] = session
    if number is not None:
        hints["number"] = number
    instrument = engine.registry.read(SourceDocument.from_path(document, **hints))
    engine.texts.add(instrument)
    report, _ = engine.analyse(instrument)
    shown = narrow(report, min_confidence=min_confidence, crossing_only=crossing_only, top=top)
    for line in render_case(shown):
        runtime.line(line)
    strong = any(match.confidence is Confidence.STRONG for match in shown.matches)
    return EXIT_FINDINGS if strong else EXIT_OK
