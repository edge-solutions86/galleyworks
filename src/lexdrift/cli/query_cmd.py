"""``lexdrift query`` - ask what an instrument reuses.

The document being asked about is never added to the index. That is the whole
point of a query: a bill under consideration is checked against what is already
enacted, and adding it would make it match itself on the next run.

The exit code is a finding, so ``lexdrift query --fail-over 0.4`` works as a
gate in a drafting pipeline.
"""

from __future__ import annotations

import argparse
from typing import TextIO

from lexdrift.cli.common import add_engine_options, engine_from
from lexdrift.cli.main import EXIT_FINDINGS, EXIT_OK, Command, register
from lexdrift.errors import ConfigError
from lexdrift.ingest.base import SourceDocument
from lexdrift.report.csv_writer import write_csv
from lexdrift.report.json_writer import write_json
from lexdrift.report.model import InstrumentReport
from lexdrift.report.render import render_text

__all__ = ["FORMATS", "configure", "handle", "write_report"]

FORMATS = ("text", "json", "csv")


def configure(parser: argparse.ArgumentParser) -> None:
    add_engine_options(parser, index_required=True)
    parser.add_argument("document", help="the document to check")
    parser.add_argument("--jurisdiction", required=True, help="jurisdiction slug for the document")
    parser.add_argument("--session", default=None, help="session, when the text does not say")
    parser.add_argument("--number", default=None, help="instrument number, when the text omits it")
    parser.add_argument(
        "--output",
        dest="output_format",
        default="text",
        choices=FORMATS,
        help="how to print the report",
    )
    parser.add_argument("--width", type=int, default=100, help="wrap width for the text report")
    parser.add_argument(
        "--fail-over",
        dest="fail_over",
        type=float,
        default=None,
        help="exit 1 when the reuse ratio is at or above this value",
    )


def write_report(report: InstrumentReport, out: TextIO, *, output_format: str, width: int) -> None:
    """Write ``report`` in the requested format."""

    if output_format == "json":
        write_json(report, out)
    elif output_format == "csv":
        write_csv(report, out)
    elif output_format == "text":
        out.write(render_text(report, width=width))
    else:  # pragma: no cover - argparse restricts the choices
        raise ConfigError("unknown output format", output_format=output_format)


def handle(args: argparse.Namespace, out: TextIO) -> int:
    engine = engine_from(args)
    hints = {"jurisdiction": args.jurisdiction}
    if args.session is not None:
        hints["session"] = args.session
    if args.number is not None:
        hints["number"] = args.number
    document = SourceDocument.from_path(args.document, **hints)
    instrument = engine.registry.read(document)
    engine.texts.add(instrument)
    report, _ = engine.analyse(instrument)
    write_report(report, out, output_format=args.output_format, width=args.width)
    if args.fail_over is not None and report.summary.reuse_ratio >= args.fail_over:
        return EXIT_FINDINGS
    return EXIT_OK


register(
    Command(
        name="query",
        help="ask what an instrument reuses from an index",
        configure=configure,
        handler=handle,
    )
)
