"""What the packaging metadata promises.

These are cheap and they catch the class of mistake nobody notices locally: a
module added but not shipped, a console script pointing at a function that was
renamed, or a typed package without its marker so every downstream user gets
``Any`` from it.
"""

from __future__ import annotations

import importlib
import pkgutil
import tomllib
from pathlib import Path

import pytest

import lexdrift
from lexdrift.cli import build_cli
from lexdrift.cli.main import cli

ROOT = Path(__file__).resolve().parent.parent
PACKAGE = Path(lexdrift.__file__).resolve().parent

EXPECTED_COMMANDS = {
    "check",
    "config",
    "diff",
    "diffusion",
    "explain",
    "index",
    "ingest",
    "manifest",
    "query",
    "report",
    "serve",
    "stats",
}


def project() -> dict:
    return tomllib.loads((ROOT / "pyproject.toml").read_text(encoding="utf-8"))["project"]


def test_the_version_is_declared_once() -> None:
    assert project()["version"] == lexdrift.__version__


def test_the_package_is_marked_as_typed() -> None:
    assert (PACKAGE / "py.typed").is_file()
    assert "py.typed" in (ROOT / "pyproject.toml").read_text(encoding="utf-8")


def test_the_console_script_points_at_a_callable() -> None:
    target = project()["scripts"]["lexdrift"]
    module_name, _, attribute = target.partition(":")
    module = importlib.import_module(module_name)
    assert callable(getattr(module, attribute))


def test_every_command_is_registered() -> None:
    build_cli()
    assert set(cli.commands) == EXPECTED_COMMANDS


def test_every_command_has_help() -> None:
    build_cli()
    for name, command in cli.commands.items():
        assert command.get_short_help_str(), name


def test_every_module_imports() -> None:
    failures: list[str] = []
    for info in pkgutil.walk_packages([str(PACKAGE)], prefix="lexdrift."):
        try:
            importlib.import_module(info.name)
        except ImportError as exc:  # pragma: no cover - a real failure fails the test
            failures.append(f"{info.name}: {exc}")
    assert failures == []


def test_the_runtime_dependencies_are_pinned_loosely() -> None:
    dependencies = project()["dependencies"]
    assert any(name.startswith("click") for name in dependencies)
    assert any(name.startswith("pydantic") for name in dependencies)
    assert all("<" in name for name in dependencies)


def test_the_service_extra_exists() -> None:
    extras = project()["optional-dependencies"]
    assert any(name.startswith("fastapi") for name in extras["service"])


def test_the_python_floor_matches_the_syntax_used() -> None:
    assert project()["requires-python"] == ">=3.12"


@pytest.mark.parametrize("name", sorted(EXPECTED_COMMANDS))
def test_each_command_module_is_importable(name: str) -> None:
    importlib.import_module(f"lexdrift.cli.{name}_cmd")
