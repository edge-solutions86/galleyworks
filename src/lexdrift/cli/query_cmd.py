"""``lexdrift query`` - ask what an instrument reuses.

The document being asked about is never added to the index. That is the whole
point of a query: a bill under consideration is checked against what is already
enacted, and adding it would make it match itself on the next run.

The exit code is a finding, so ``lexdrift query --fail-over 0.4`` works as a
gate in a drafting pipeline.
"""

from __future__ import annotations

import click

from lexdrift.cli.common import build_engine, display_options, engine_options, narrow
from lexdrift.cli.main import EXIT_FINDINGS, EXIT_OK, Runtime, cli
from lexdrift.ingest.base import SourceDocument
from lexdrift.report.writers import format_names, write_report

__all__ = ["query"]


@cli.command(name="query", short_help="ask what an instrument reuses from an index")
@click.argument("document")
@engine_options(index_required=True)
@display_options
@click.option("--jurisdiction", required=True, help="jurisdiction slug for the document")
@click.option("--session", default=None, help="session, when the text does not say")
@click.option("--number", default=None, help="instrument number, when the text omits it")
@click.option(
    "--output",
    "output_format",
    default="text",
    type=click.Choice(format_names()),
    show_default=True,
    help="how to print the report",
)
@click.option("--width", type=int, default=100, show_default=True, help="text wrap width")
@click.option(
    "--fail-over",
    "fail_over",
    type=float,
    default=None,
    help="exit 1 when the reuse ratio is at or above this value",
)
@click.pass_obj
def query(
    runtime: Runtime,
    document: str,
    index_path: str | None,
    config_path: str | None,
    profile: str | None,
    reading: str | None,
    jurisdiction: str,
    session: str | None,
    number: str | None,
    output_format: str,
    width: int,
    fail_over: float | None,
    min_confidence: str | None,
    crossing_only: bool,
    top: int | None,
) -> int:
    """Check DOCUMENT against an index without adding it."""

    engine = build_engine(index_path, config_path, profile, reading)
    hints: dict[str, str] = {"jurisdiction": jurisdiction}
    if session is not None:
        hints["session"] = session
    if number is not None:
        hints["number"] = number
    source = SourceDocument.from_path(document, **hints)
    instrument = engine.registry.read(source)
    engine.texts.add(instrument)
    report, _ = engine.analyse(instrument)
    shown = narrow(report, min_confidence=min_confidence, crossing_only=crossing_only, top=top)
    write_report(shown, runtime.out, output_format=output_format, width=width)
    if fail_over is not None and report.summary.reuse_ratio >= fail_over:
        return EXIT_FINDINGS
    return EXIT_OK
