"""The command line entry point.

Built on click. The hand-rolled registry this replaced had to reimplement
subcommand help, option grouping and error formatting, and it still could not do
nested groups, which ``lexdrift index`` and ``lexdrift index vacuum`` now need.

Exit codes are part of the contract and did not change:

* ``0`` the command ran and found nothing worth flagging
* ``1`` the command ran and the result is a finding, for use in a CI gate
* ``2`` the arguments were wrong
* ``3`` a lexdrift error, with its stable code on stderr

Output goes to a stream carried on the click context rather than to
``sys.stdout`` directly, so a caller can run the CLI in process and read what it
produced.
"""

from __future__ import annotations

import sys
from collections.abc import Sequence
from dataclasses import dataclass
from typing import TextIO

import click

from lexdrift import __version__
from lexdrift.errors import LexdriftError
from lexdrift.util.logging import configure_logging

__all__ = ["EPILOG", "Runtime", "cli", "main"]

EXIT_OK = 0
EXIT_FINDINGS = 1
EXIT_USAGE = 2
EXIT_ERROR = 3


@dataclass(slots=True)
class Runtime:
    """What every subcommand needs from the top level."""

    out: TextIO

    def line(self, text: str = "") -> None:
        print(text, file=self.out)


EPILOG = """A corpus is a directory of documents named <jurisdiction>_<session>_<number>.txt,
or a manifest.yaml naming them explicitly.

Exit codes: 0 nothing to flag, 1 a finding, 2 bad arguments, 3 a lexdrift error.
"""


@click.group(
    name="lexdrift",
    help="Passage-reuse detection for legislative instruments.",
    epilog=EPILOG,
    invoke_without_command=True,
    context_settings={"help_option_names": ["-h", "--help"], "max_content_width": 100},
)
@click.version_option(__version__, "--version", prog_name="lexdrift")
@click.option(
    "--log-level",
    default="warning",
    type=click.Choice(["debug", "info", "warning", "error"]),
    show_default=True,
    help="how much to write to stderr",
)
@click.option(
    "--log-format",
    default="plain",
    type=click.Choice(["plain", "json"]),
    show_default=True,
    help="stderr log layout",
)
@click.pass_context
def cli(ctx: click.Context, log_level: str, log_format: str) -> int:
    """Top level group."""

    configure_logging(level=log_level, json_output=log_format == "json")
    if ctx.invoked_subcommand is None:
        runtime = ctx.find_object(Runtime)
        stream = sys.stdout if runtime is None else runtime.out
        stream.write(ctx.get_help())
        stream.write("\n")
        return EXIT_USAGE
    return EXIT_OK


def main(argv: Sequence[str] | None = None, stream: TextIO | None = None) -> int:
    """Run the CLI in process, returning the exit code."""

    from lexdrift.cli import build_cli

    build_cli()
    runtime = Runtime(out=stream or sys.stdout)
    try:
        result = cli.main(
            args=list(argv or []),
            obj=runtime,
            standalone_mode=False,
            prog_name="lexdrift",
        )
    except click.UsageError as exc:
        print(exc.format_message(), file=sys.stderr)
        return EXIT_USAGE
    except click.exceptions.Exit as exc:
        return int(exc.exit_code)
    except click.Abort:
        return EXIT_USAGE
    except LexdriftError as exc:
        print(f"{exc.code}: {exc}", file=sys.stderr)
        return EXIT_ERROR
    return EXIT_OK if result is None else int(result)


def run() -> None:  # pragma: no cover - console script shim
    raise SystemExit(main(sys.argv[1:]))
