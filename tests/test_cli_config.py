from __future__ import annotations

import io
import json
from pathlib import Path

import pytest

from lexdrift.cli import build_cli
from lexdrift.cli.config_cmd import derived
from lexdrift.cli.main import EXIT_ERROR, EXIT_FINDINGS, EXIT_OK, main
from lexdrift.config import EngineConfig, LshConfig, ShingleConfig


@pytest.fixture(autouse=True)
def _loaded() -> None:
    build_cli()


def run(*argv: str) -> tuple[int, str]:
    stream = io.StringIO()
    code = main(list(argv), stream)
    return code, stream.getvalue()


def test_the_derived_numbers_follow_the_settings() -> None:
    numbers = derived(EngineConfig(shingle=ShingleConfig(width=5, winnow_window=4)))
    assert numbers["guarantee_length_tokens"] == 8
    assert 0.0 < float(numbers["expected_density"]) <= 1.0
    assert len(str(numbers["engine_digest"])) == 16


def test_the_banding_numbers_are_derived() -> None:
    numbers = derived(EngineConfig(lsh=LshConfig(permutations=128, bands=16)))
    assert numbers["lsh_rows"] == 8
    assert 0.0 < float(numbers["lsh_threshold"]) < 1.0


def test_the_default_settings_print() -> None:
    code, output = run("config")
    assert code == EXIT_OK
    assert "profile: default" in output
    assert "engine digest" in output
    assert "report formats:" in output


def test_a_config_file_is_reflected(tmp_path: Path) -> None:
    path = tmp_path / "engine.yaml"
    path.write_text("shingle:\n  width: 7\n", encoding="utf-8")
    _, output = run("config", "--config", str(path))
    assert "width 7" in output


def test_a_profile_is_reflected() -> None:
    _, output = run("config", "--profile", "verbatim")
    assert "profile: verbatim" in output


def test_the_json_form_parses() -> None:
    _, output = run("config", "--json")
    payload = json.loads(output)
    assert payload["settings"]["shingle"]["width"] == 5
    assert payload["derived"]["lsh_rows"] == 4


def test_the_profiles_can_be_listed() -> None:
    code, output = run("config", "--list-profiles")
    assert code == EXIT_OK
    assert "* default" in output
    assert "verbatim" in output


def test_the_selected_profile_is_marked() -> None:
    _, output = run("config", "--list-profiles", "--profile", "municipal")
    assert "* municipal" in output


def test_a_dangerous_threshold_is_flagged(tmp_path: Path) -> None:
    path = tmp_path / "engine.yaml"
    path.write_text("match:\n  min_containment: 0.05\n", encoding="utf-8")
    code, output = run("config", "--config", str(path))
    assert code == EXIT_FINDINGS
    assert "warning" in output


def test_an_unknown_profile_is_an_error() -> None:
    assert run("config", "--profile", "nowhere")[0] == EXIT_ERROR


def test_a_broken_config_file_is_an_error(tmp_path: Path) -> None:
    path = tmp_path / "engine.yaml"
    path.write_text("shingle: [\n", encoding="utf-8")
    assert run("config", "--config", str(path))[0] == EXIT_ERROR
