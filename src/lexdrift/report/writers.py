"""The report format registry.

Format dispatch used to be an if-chain in the query command and a second, almost
identical one in the report command, and adding markdown meant editing both and
a third list of file suffixes. All three now come from one table, so a format is
added in one place and every command grows it at once.
"""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from typing import TextIO

from lexdrift.errors import ConfigError
from lexdrift.report.csv_writer import write_csv
from lexdrift.report.json_writer import write_json
from lexdrift.report.markdown_writer import write_markdown
from lexdrift.report.model import InstrumentReport
from lexdrift.report.render import render_text

__all__ = ["FORMATS", "ReportWriter", "format_names", "suffix_for", "write_report"]

Emit = Callable[[InstrumentReport, TextIO, int], None]


def _emit_text(report: InstrumentReport, stream: TextIO, width: int) -> None:
    stream.write(render_text(report, width=width))


def _emit_json(report: InstrumentReport, stream: TextIO, width: int) -> None:
    write_json(report, stream)


def _emit_csv(report: InstrumentReport, stream: TextIO, width: int) -> None:
    write_csv(report, stream)


def _emit_markdown(report: InstrumentReport, stream: TextIO, width: int) -> None:
    write_markdown(report, stream)


@dataclass(frozen=True, slots=True)
class ReportWriter:
    """One output format."""

    name: str
    suffix: str
    emit: Emit
    description: str


FORMATS: dict[str, ReportWriter] = {
    writer.name: writer
    for writer in (
        ReportWriter("text", "txt", _emit_text, "plain text for a terminal"),
        ReportWriter("json", "json", _emit_json, "versioned json for a program"),
        ReportWriter("csv", "csv", _emit_csv, "one row per passage"),
        ReportWriter("markdown", "md", _emit_markdown, "markdown for a ticket"),
    )
}


def format_names() -> list[str]:
    """Every format name, in a stable order."""

    return list(FORMATS)


def _writer(name: str) -> ReportWriter:
    try:
        return FORMATS[name]
    except KeyError as exc:
        raise ConfigError(
            "unknown output format", output_format=name, known=",".join(format_names())
        ) from exc


def suffix_for(name: str) -> str:
    """The file suffix a format writes."""

    return _writer(name).suffix


def write_report(
    report: InstrumentReport, stream: TextIO, *, output_format: str = "text", width: int = 100
) -> None:
    """Write ``report`` in the named format."""

    _writer(output_format).emit(report, stream, width)
