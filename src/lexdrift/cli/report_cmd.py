"""``lexdrift report`` - run a whole corpus and write one report per instrument.

This is the batch counterpart of ``query``: instead of asking about one document
against a fixed index, it walks a corpus in order, indexing as it goes, so each
instrument is checked against everything that came before it. That ordering is
what makes the output readable as a history rather than as a pile of symmetric
pairs.
"""

from __future__ import annotations

import argparse
import re
from pathlib import Path
from typing import TextIO

from lexdrift.cli.common import add_engine_options, engine_from
from lexdrift.cli.ingest_cmd import load_corpus
from lexdrift.cli.main import EXIT_FINDINGS, EXIT_OK, Command, register
from lexdrift.cli.query_cmd import FORMATS
from lexdrift.errors import ConfigError
from lexdrift.identity import InstrumentKey
from lexdrift.report.csv_writer import write_csv
from lexdrift.report.json_writer import write_json
from lexdrift.report.model import InstrumentReport
from lexdrift.report.render import render_text

__all__ = ["configure", "file_name_for", "handle"]

_UNSAFE = re.compile(r"[^A-Za-z0-9._-]+")
_SUFFIX = {"text": "txt", "json": "json", "csv": "csv"}


def file_name_for(key: InstrumentKey, output_format: str) -> str:
    """A file name that survives every filesystem we care about."""

    stem = _UNSAFE.sub("-", key.slug).strip("-")
    try:
        return f"{stem}.{_SUFFIX[output_format]}"
    except KeyError as exc:
        raise ConfigError("unknown output format", output_format=output_format) from exc


def configure(parser: argparse.ArgumentParser) -> None:
    add_engine_options(parser)
    parser.add_argument("source", help="a corpus directory or a manifest.yaml")
    parser.add_argument(
        "--out",
        dest="out_dir",
        default=None,
        help="write one file per instrument into this directory",
    )
    parser.add_argument(
        "--output",
        dest="output_format",
        default="text",
        choices=FORMATS,
        help="report format",
    )
    parser.add_argument(
        "--only-findings",
        action="store_true",
        help="skip instruments with no reuse",
    )
    parser.add_argument("--width", type=int, default=100, help="wrap width for text reports")


def _write(report: InstrumentReport, stream: TextIO, args: argparse.Namespace) -> None:
    if args.output_format == "json":
        write_json(report, stream)
    elif args.output_format == "csv":
        write_csv(report, stream)
    else:
        stream.write(render_text(report, width=args.width))


def handle(args: argparse.Namespace, out: TextIO) -> int:
    engine = engine_from(args)
    result = engine.run(load_corpus(args.source))
    destination = None if args.out_dir is None else Path(args.out_dir)
    if destination is not None:
        destination.mkdir(parents=True, exist_ok=True)
    written = 0
    findings = 0
    for report in result.reports:
        if report.is_empty:
            if args.only_findings:
                continue
        else:
            findings += 1
        if destination is None:
            _write(report, out, args)
        else:
            path = destination / file_name_for(report.key, args.output_format)
            with path.open("w", encoding="utf-8", newline="\n") as handle_out:
                _write(report, handle_out, args)
            written += 1
    if destination is not None:
        print(f"{written} reports written to {destination}", file=out)
    print(
        f"{result.instrument_count} instruments, {findings} with reuse, {len(result.graph)} edges",
        file=out,
    )
    return EXIT_FINDINGS if findings else EXIT_OK


register(
    Command(
        name="report",
        help="run a corpus and write one report per instrument",
        configure=configure,
        handler=handle,
    )
)
