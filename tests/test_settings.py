from __future__ import annotations

from pathlib import Path

import pytest

from lexdrift.config import ShingleMode
from lexdrift.errors import ConfigError
from lexdrift.settings import apply_env_overrides, deep_merge, load_config, parse_scalar


def write(tmp_path: Path, text: str) -> Path:
    path = tmp_path / "engine.yaml"
    path.write_text(text, encoding="utf-8")
    return path


def test_parse_scalar_reads_yaml_types() -> None:
    assert parse_scalar("12") == 12
    assert parse_scalar("0.5") == pytest.approx(0.5)
    assert parse_scalar("true") is True
    assert parse_scalar("character") == "character"


def test_parse_scalar_falls_back_to_the_raw_string() -> None:
    assert parse_scalar("[unclosed") == "[unclosed"


def test_deep_merge_does_not_mutate_its_inputs() -> None:
    base = {"a": {"x": 1, "y": 2}}
    overlay = {"a": {"y": 3}, "b": 4}
    merged = deep_merge(base, overlay)
    assert merged == {"a": {"x": 1, "y": 3}, "b": 4}
    assert base == {"a": {"x": 1, "y": 2}}


def test_deep_merge_replaces_scalars_with_mappings() -> None:
    assert deep_merge({"a": 1}, {"a": {"b": 2}}) == {"a": {"b": 2}}


def test_env_overrides_are_nested_on_the_double_underscore() -> None:
    env = {"LEXDRIFT__MATCH__MIN_CONTAINMENT": "0.75", "PATH": "/bin"}
    assert apply_env_overrides({}, env) == {"match": {"min_containment": 0.75}}


def test_env_overrides_ignore_unrelated_variables() -> None:
    assert apply_env_overrides({"a": 1}, {"HOME": "/root"}) == {"a": 1}


def test_env_override_with_no_field_path_is_rejected() -> None:
    with pytest.raises(ConfigError, match="no field path"):
        apply_env_overrides({}, {"LEXDRIFT__": "1"})


def test_env_override_colliding_with_a_scalar_is_rejected() -> None:
    env = {"LEXDRIFT__MATCH": "5", "LEXDRIFT__MATCH__MIN_CONTAINMENT": "0.5"}
    with pytest.raises(ConfigError, match="collides with a scalar"):
        apply_env_overrides({}, env)


def test_load_config_without_a_file_gives_defaults() -> None:
    cfg = load_config(environ={})
    assert cfg.shingle.width == 5


def test_load_config_reads_yaml(tmp_path: Path) -> None:
    path = write(tmp_path, "shingle:\n  mode: character\n  width: 9\n")
    cfg = load_config(path, environ={})
    assert cfg.shingle.mode is ShingleMode.CHARACTER
    assert cfg.shingle.width == 9


def test_environment_beats_the_file(tmp_path: Path) -> None:
    path = write(tmp_path, "shingle:\n  width: 9\n")
    cfg = load_config(path, environ={"LEXDRIFT__SHINGLE__WIDTH": "3"})
    assert cfg.shingle.width == 3


def test_empty_file_is_treated_as_defaults(tmp_path: Path) -> None:
    path = write(tmp_path, "")
    assert load_config(path, environ={}).lsh.permutations == 128


def test_non_mapping_file_is_rejected(tmp_path: Path) -> None:
    path = write(tmp_path, "- a\n- b\n")
    with pytest.raises(ConfigError, match="must hold a mapping"):
        load_config(path, environ={})


def test_broken_yaml_is_rejected(tmp_path: Path) -> None:
    path = write(tmp_path, "shingle: [1, 2\n")
    with pytest.raises(ConfigError, match="not valid YAML"):
        load_config(path, environ={})


def test_missing_file_is_rejected(tmp_path: Path) -> None:
    with pytest.raises(ConfigError, match="cannot be read"):
        load_config(tmp_path / "absent.yaml", environ={})
