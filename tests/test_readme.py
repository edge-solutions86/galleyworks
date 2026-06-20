"""The README is documentation and documentation drifts.

Every command it lists has to exist, and every command that exists has to be
listed. Nothing here checks prose; it checks the two lists that go stale.
"""

from __future__ import annotations

import io
import re
from pathlib import Path

import pytest

from lexdrift import __version__
from lexdrift.cli import build_cli
from lexdrift.cli.main import EPILOG, cli, main
from lexdrift.config import EngineConfig
from lexdrift.report.writers import format_names

README = (Path(__file__).resolve().parent.parent / "README.md").read_text(encoding="utf-8")


@pytest.fixture(autouse=True)
def _loaded() -> None:
    build_cli()


def documented_commands() -> set[str]:
    return set(re.findall(r"^lexdrift (\w[\w-]*)", README, flags=re.MULTILINE))


def test_every_documented_command_exists() -> None:
    assert documented_commands() <= set(cli.commands)


def test_every_command_is_documented() -> None:
    assert set(cli.commands) <= documented_commands()


def test_the_exit_codes_are_documented() -> None:
    for code in ("0", "1", "2", "3"):
        assert f"`{code}`" in README or f"{code} " in README


def test_the_exit_codes_are_in_the_help_epilog() -> None:
    assert "Exit codes" in EPILOG
    stream = io.StringIO()
    main([], stream)
    assert "Exit codes" in stream.getvalue()


def test_the_configuration_keys_in_the_readme_are_real() -> None:
    defaults = EngineConfig().to_mapping()
    for section in ("shingle", "lsh", "canon", "match", "report"):
        assert f"{section}:" in README
        assert section in defaults


def test_the_documented_settings_exist() -> None:
    mapping = EngineConfig().to_mapping()
    assert "winnow_window" in mapping["shingle"]
    assert "permutations" in mapping["lsh"]
    assert "same_jurisdiction_containment" in mapping["match"]
    assert "min_confidence" in mapping["report"]


def test_the_python_floor_matches() -> None:
    assert "Python 3.12" in README


def test_the_service_extra_is_documented() -> None:
    assert "[service]" in README


def test_the_library_example_uses_exported_names() -> None:
    import lexdrift

    for name in ("Engine", "EngineConfig", "discover"):
        assert f"{name}" in README
        assert name in lexdrift.__all__


def test_the_version_is_not_mentioned_in_prose() -> None:
    # A version in the README is a version that goes stale on every release.
    assert __version__ not in README


def test_the_report_formats_are_documented() -> None:
    lowered = README.lower()
    assert all(name in lowered for name in format_names())
