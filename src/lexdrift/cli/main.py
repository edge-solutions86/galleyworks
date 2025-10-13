"""The command line entry point.

Subcommands are registered rather than hard coded, so a command lives beside the
code it drives instead of in one growing dispatch function.

Exit codes are part of the contract:

* ``0`` the command ran and found nothing worth flagging
* ``1`` the command ran and the result is a finding, for use in a CI gate
* ``2`` the arguments were wrong
* ``3`` a lexdrift error, with its stable code on stderr
"""

from __future__ import annotations

import argparse
import sys
from collections.abc import Callable, Sequence
from dataclasses import dataclass
from typing import TextIO

from lexdrift import __version__
from lexdrift.errors import LexdriftError
from lexdrift.util.logging import configure_logging

__all__ = ["Command", "COMMANDS", "build_parser", "main", "register"]

EXIT_OK = 0
EXIT_FINDINGS = 1
EXIT_USAGE = 2
EXIT_ERROR = 3

Handler = Callable[[argparse.Namespace, TextIO], int]


@dataclass(frozen=True, slots=True)
class Command:
    """One subcommand."""

    name: str
    help: str
    configure: Callable[[argparse.ArgumentParser], None]
    handler: Handler


COMMANDS: list[Command] = []


def register(command: Command) -> Command:
    """Add a subcommand to the CLI."""

    if any(existing.name == command.name for existing in COMMANDS):
        raise ValueError(f"duplicate command {command.name!r}")
    COMMANDS.append(command)
    return command


def build_parser() -> argparse.ArgumentParser:
    """Build the top level parser with every registered subcommand."""

    parser = argparse.ArgumentParser(
        prog="lexdrift",
        description="Passage-reuse detection for legislative instruments.",
    )
    parser.add_argument("--version", action="version", version=f"lexdrift {__version__}")
    parser.add_argument(
        "--log-level",
        default="warning",
        choices=("debug", "info", "warning", "error"),
        help="how much to write to stderr",
    )
    parser.add_argument(
        "--log-format",
        default="plain",
        choices=("plain", "json"),
        help="stderr log layout",
    )
    subparsers = parser.add_subparsers(dest="command", metavar="command")
    for command in COMMANDS:
        sub = subparsers.add_parser(command.name, help=command.help)
        command.configure(sub)
        sub.set_defaults(_handler=command.handler)
    return parser


def main(argv: Sequence[str] | None = None, stream: TextIO | None = None) -> int:
    """Run the CLI, returning the process exit code."""

    out = stream or sys.stdout
    parser = build_parser()
    args = parser.parse_args(argv)
    if getattr(args, "_handler", None) is None:
        parser.print_help(out)
        return EXIT_USAGE
    configure_logging(level=args.log_level, json_output=args.log_format == "json")
    handler: Handler = args._handler
    try:
        return handler(args, out)
    except LexdriftError as exc:
        print(f"{exc.code}: {exc}", file=sys.stderr)
        return EXIT_ERROR


def run() -> None:  # pragma: no cover - console script shim
    raise SystemExit(main())
