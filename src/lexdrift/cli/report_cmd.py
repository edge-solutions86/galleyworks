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
from typing import TextIO

import click

from lexdrift.cli.common import build_engine, engine_options
from lexdrift.cli.ingest_cmd import load_corpus
from lexdrift.cli.main import EXIT_FINDINGS, EXIT_OK, Runtime, cli
from lexdrift.cli.query_cmd import FORMATS, write_report
from lexdrift.errors import ConfigError
from lexdrift.identity import InstrumentKey
from lexdrift.report.model import InstrumentReport

__all__ = ["file_name_for", "report"]

_UNSAFE = re.compile(r"[^A-Za-z0-9._-]+")
_SUFFIX = {"text": "txt", "json": "json", "csv": "csv"}


def file_name_for(key: InstrumentKey, output_format: str) -> str:
    """A file name that survives every filesystem we care about."""

    stem = _UNSAFE.sub("-", key.slug).strip("-")
    try:
        return f"{stem}.{_SUFFIX[output_format]}"
    except KeyError as exc:
        raise ConfigError("unknown output format", output_format=output_format) from exc


def _emit(built: InstrumentReport, stream: TextIO, output_format: str, width: int) -> None:
    write_report(built, stream, output_format=output_format, width=width)


@cli.command(name="report", short_help="run a corpus and write one report per instrument")
@click.argument("source")
@engine_options()
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
    type=click.Choice(list(FORMATS)),
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
    out_dir: str | None,
    output_format: str,
    only_findings: bool,
    width: int,
) -> int:
    """Run the corpus at SOURCE and report every instrument."""

    engine = build_engine(index_path, config_path, profile)
    result = engine.run(load_corpus(source))
    destination = None if out_dir is None else Path(out_dir)
    if destination is not None:
        destination.mkdir(parents=True, exist_ok=True)
    written = 0
    findings = 0
    for built in result.reports:
        if built.is_empty:
            if only_findings:
                continue
        else:
            findings += 1
        if destination is None:
            _emit(built, runtime.out, output_format, width)
        else:
            path = destination / file_name_for(built.key, output_format)
            with path.open("w", encoding="utf-8", newline="\n") as stream:
                _emit(built, stream, output_format, width)
            written += 1
    if destination is not None:
        runtime.line(f"{written} reports written to {destination}")
    runtime.line(
        f"{result.instrument_count} instruments, {findings} with reuse, {len(result.graph)} edges"
    )
    return EXIT_FINDINGS if findings else EXIT_OK
