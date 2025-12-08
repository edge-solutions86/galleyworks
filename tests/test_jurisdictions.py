from __future__ import annotations

from pathlib import Path

import pytest

from lexdrift.errors import ConfigError
from lexdrift.identity import GovernmentLevel, Jurisdiction
from lexdrift.jurisdictions import JurisdictionRegistry, load_jurisdictions

TABLE = """
jurisdictions:
  - slug: wy
    name: Wyoming
    level: state
  - slug: albany-county
    name: Albany County
    level: county
    parent: wy
  - slug: laramie
    name: Laramie
    level: municipal
    parent: albany-county
    profile: municipal
"""


def write(tmp_path: Path, text: str = TABLE) -> Path:
    path = tmp_path / "jurisdictions.yaml"
    path.write_text(text, encoding="utf-8")
    return path


def test_a_table_loads(tmp_path: Path) -> None:
    registry = load_jurisdictions(write(tmp_path))
    assert len(registry) == 3
    assert "wy" in registry
    assert registry.get("laramie").level is GovernmentLevel.MUNICIPAL


def test_iteration_is_ordered(tmp_path: Path) -> None:
    slugs = [jurisdiction.slug for jurisdiction in load_jurisdictions(write(tmp_path))]
    assert slugs == sorted(slugs)


def test_ancestry_walks_upward(tmp_path: Path) -> None:
    registry = load_jurisdictions(write(tmp_path))
    assert registry.ancestry("laramie") == ["laramie", "albany-county", "wy"]


def test_children_are_found(tmp_path: Path) -> None:
    registry = load_jurisdictions(write(tmp_path))
    assert [child.slug for child in registry.children_of("wy")] == ["albany-county"]


def test_a_profile_is_used_where_named(tmp_path: Path) -> None:
    registry = load_jurisdictions(write(tmp_path))
    assert registry.profile_for("laramie") == "municipal"
    assert registry.profile_for("wy") == "default"


def test_a_profile_is_inherited_from_an_ancestor(tmp_path: Path) -> None:
    table = TABLE.replace(
        "    parent: albany-county\n    profile: municipal", "    parent: albany-county"
    ).replace("    level: state", "    level: state\n    profile: verbatim")
    registry = load_jurisdictions(write(tmp_path, table))
    assert registry.profile_for("laramie") == "verbatim"


def test_an_unknown_jurisdiction_is_an_error(tmp_path: Path) -> None:
    registry = load_jurisdictions(write(tmp_path))
    with pytest.raises(ConfigError, match="no such jurisdiction"):
        registry.get("nowhere")


def test_a_duplicate_registration_is_rejected() -> None:
    registry = JurisdictionRegistry()
    entry = Jurisdiction(slug="wy", name="Wyoming", level=GovernmentLevel.STATE)
    registry.add(entry)
    with pytest.raises(ConfigError, match="already registered"):
        registry.add(entry)


def test_an_unknown_profile_is_rejected() -> None:
    registry = JurisdictionRegistry()
    entry = Jurisdiction(slug="wy", name="Wyoming", level=GovernmentLevel.STATE)
    with pytest.raises(ConfigError, match="unknown canonicalization profile"):
        registry.add(entry, profile="nowhere")


def test_a_dangling_parent_is_rejected(tmp_path: Path) -> None:
    table = "jurisdictions:\n  - slug: laramie\n    name: L\n    level: municipal\n    parent: wy\n"
    with pytest.raises(ConfigError, match="unknown parent"):
        load_jurisdictions(write(tmp_path, table))


def test_a_cycle_is_rejected(tmp_path: Path) -> None:
    table = (
        "jurisdictions:\n"
        "  - slug: a\n    name: A\n    level: state\n    parent: b\n"
        "  - slug: b\n    name: B\n    level: state\n    parent: a\n"
    )
    with pytest.raises(ConfigError, match="cycle"):
        load_jurisdictions(write(tmp_path, table))


def test_an_incomplete_entry_names_what_is_missing(tmp_path: Path) -> None:
    table = "jurisdictions:\n  - slug: wy\n"
    with pytest.raises(ConfigError, match="is incomplete") as caught:
        load_jurisdictions(write(tmp_path, table))
    assert caught.value.context["missing"] == "name,level"


def test_an_unknown_level_is_rejected(tmp_path: Path) -> None:
    table = "jurisdictions:\n  - slug: wy\n    name: W\n    level: galactic\n"
    with pytest.raises(ConfigError, match="unknown government level"):
        load_jurisdictions(write(tmp_path, table))


def test_a_scalar_entry_is_rejected(tmp_path: Path) -> None:
    with pytest.raises(ConfigError, match="must be a mapping"):
        load_jurisdictions(write(tmp_path, "jurisdictions:\n  - wy\n"))


def test_a_table_without_the_key_is_rejected(tmp_path: Path) -> None:
    with pytest.raises(ConfigError, match="needs a jurisdictions list"):
        load_jurisdictions(write(tmp_path, "other: 1\n"))


def test_a_missing_file_is_rejected(tmp_path: Path) -> None:
    with pytest.raises(ConfigError, match="cannot be read"):
        load_jurisdictions(tmp_path / "absent.yaml")


def test_broken_yaml_is_rejected(tmp_path: Path) -> None:
    with pytest.raises(ConfigError, match="not valid YAML"):
        load_jurisdictions(write(tmp_path, "jurisdictions: [\n"))
