"""What every command promises about its arguments.

These were three near-identical tests scattered across three files, each of
which happened to pass on the click version installed locally and none of which
passed on the one CI resolved. Gathering them here makes the promise one thing
that is checked once for every command that makes it.

The dependency pins are checked in the same place, because the reason the
promise broke was a dependency range wide enough to admit a behaviour change.
"""

from __future__ import annotations

import io
import tomllib
from pathlib import Path

import pytest

from lexdrift.cli import build_cli
from lexdrift.cli.main import EXIT_ERROR, EXIT_OK, EXIT_USAGE, cli, main

ROOT = Path(__file__).resolve().parent.parent
PROJECT = tomllib.loads((ROOT / "pyproject.toml").read_text(encoding="utf-8"))["project"]
DEV_PINS = dict(
    line.split("==", 1)
    for line in (ROOT / "requirements-dev.txt").read_text(encoding="utf-8").splitlines()
    if "==" in line and not line.startswith("#")
)

# Commands that cannot do anything useful without being told where the index is.
INDEX_REQUIRED = ("check", "query", "serve", "stats")
# Commands that read a corpus and therefore need a source argument.
SOURCE_REQUIRED = ("ingest", "index", "report", "diffusion", "manifest")


@pytest.fixture(autouse=True)
def _loaded() -> None:
    build_cli()


def run(*argv: str) -> tuple[int, str]:
    stream = io.StringIO()
    code = main(list(argv), stream)
    return code, stream.getvalue()


@pytest.mark.parametrize("name", INDEX_REQUIRED)
def test_the_index_option_is_required(name: str) -> None:
    code, _ = run(name)
    assert code == EXIT_USAGE, f"{name} ran without --index"


@pytest.mark.parametrize("name", SOURCE_REQUIRED)
def test_the_source_argument_is_required(name: str) -> None:
    assert run(name)[0] == EXIT_USAGE, f"{name} ran without a source"


@pytest.mark.parametrize("name", sorted(("query", "explain", "diff")))
def test_the_jurisdiction_option_is_required(name: str) -> None:
    assert run(name, "a.txt", "b.txt")[0] == EXIT_USAGE


def test_an_unknown_option_is_a_usage_error() -> None:
    assert run("config", "--nonesuch")[0] == EXIT_USAGE


def test_an_unknown_command_is_a_usage_error() -> None:
    assert run("nonesuch")[0] == EXIT_USAGE


def test_no_arguments_prints_help() -> None:
    code, output = run()
    assert code == EXIT_USAGE
    assert "Usage: lexdrift" in output


def test_help_is_not_an_error() -> None:
    assert run("--help")[0] == EXIT_OK
    assert run("config", "--help")[0] == EXIT_OK


def test_a_missing_file_is_an_engine_error_not_a_usage_one(tmp_path: Path) -> None:
    index_path = tmp_path / "index.sqlite"
    run("index", "--index", str(index_path), str(tmp_path))
    code, _ = run("query", "--index", str(index_path), "--jurisdiction", "wy", "absent.txt")
    assert code == EXIT_ERROR


@pytest.mark.parametrize("name", sorted(("click", "pydantic", "PyYAML")))
def test_every_runtime_dependency_is_pinned_in_ci(name: str) -> None:
    assert name in DEV_PINS, f"{name} floats in CI"


def test_the_runtime_bounds_are_narrow() -> None:
    for spec in PROJECT["dependencies"]:
        assert "<" in spec, spec
        upper = spec.split("<")[1]
        assert upper.count(".") >= 1, f"{spec} allows a whole major version"


def test_the_pinned_versions_satisfy_the_declared_bounds() -> None:
    for spec in PROJECT["dependencies"]:
        name = spec.split(">")[0].split("<")[0].strip()
        assert name in DEV_PINS, name
        lower = spec.split(">=")[1].split(",")[0]
        assert DEV_PINS[name] >= lower


def test_every_command_is_covered_by_one_of_these_lists() -> None:
    named = (
        set(INDEX_REQUIRED)
        | set(SOURCE_REQUIRED)
        | {
            "config",
            "completion",
            "diff",
            "explain",
        }
    )
    assert set(cli.commands) <= named
