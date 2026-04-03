"""``lexdrift report`` - run a whole corpus and write one report per instrument.

This is the batch counterpart of ``query``: instead of asking about one document
against a fixed index, it walks a corpus in order, indexing as it goes, so each
instrument is checked against everything that came before it. That ordering is
what makes the output readable as a history rather than as a pile of symmetric
pairs.
"""

from __future__ import annotations

import re
from pathlib import Path

import click

from lexdrift.cli.common import build_engine, display_options, engine_options, narrow
from lexdrift.cli.ingest_cmd import load_corpus
from lexdrift.cli.main import EXIT_FINDINGS, EXIT_OK, Runtime, cli
from lexdrift.identity import InstrumentKey
from lexdrift.report.writers import format_names, suffix_for, write_report

__all__ = ["file_name_for", "report"]

_UNSAFE = re.compile(r"[^A-Za-z0-9._-]+")


def file_name_for(key: InstrumentKey, output_format: str) -> str:
    """A file name that survives every filesystem we care about."""

    stem = _UNSAFE.sub("-", key.slug).strip("-")
    return f"{stem}.{suffix_for(output_format)}"


@cli.command(name="report", short_help="run a corpus and write one report per instrument")
@click.argument("source")
@engine_options()
@display_options
@click.option(
    "--out",
    "out_dir",
    type=click.Path(file_okay=False),
    default=None,
    help="write one file per instrument into this directory",
)
@click.option(
    "--output",
    "output_format",
    default="text",
    type=click.Choice(format_names()),
    show_default=True,
    help="report format",
)
@click.option("--only-findings", is_flag=True, help="skip instruments with no reuse")
@click.option("--width", type=int, default=100, show_default=True, help="text wrap width")
@click.pass_obj
def report(
    runtime: Runtime,
    source: str,
    index_path: str | None,
    config_path: str | None,
    profile: str | None,
    reading: str | None,
    out_dir: str | None,
    output_format: str,
    only_findings: bool,
    width: int,
    min_confidence: str | None,
    crossing_only: bool,
    top: int | None,
) -> int:
    """Run the corpus at SOURCE and report every instrument."""

    engine = build_engine(index_path, config_path, profile, reading)
    result = engine.run(load_corpus(source))
    destination = None if out_dir is None else Path(out_dir)
    if destination is not None:
        destination.mkdir(parents=True, exist_ok=True)
    written = 0
    findings = 0
    for raw in result.reports:
        built = narrow(raw, min_confidence=min_confidence, crossing_only=crossing_only, top=top)
        if built.is_empty:
            if only_findings:
                continue
        else:
            findings += 1
        if destination is None:
            write_report(built, runtime.out, output_format=output_format, width=width)
        else:
            path = destination / file_name_for(built.key, output_format)
            with path.open("w", encoding="utf-8", newline="\n") as stream:
                write_report(built, stream, output_format=output_format, width=width)
            written += 1
    if destination is not None:
        runtime.line(f"{written} reports written to {destination}")
    runtime.line(
        f"{result.instrument_count} instruments, {findings} with reuse, {len(result.graph)} edges"
    )
    return EXIT_FINDINGS if findings else EXIT_OK
