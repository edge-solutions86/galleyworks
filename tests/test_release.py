"""Release metadata, and the promises a 1.0 makes.

Version one means the stable codes, the wire formats and the export list are a
contract. These tests are what makes that claim checkable rather than a sentence
in a changelog.
"""

from __future__ import annotations

import tomllib
from pathlib import Path

import pytest

import lexdrift
from lexdrift import errors
from lexdrift.fingerprint.codec import CODEC_VERSION
from lexdrift.index.schema import SCHEMA_VERSION
from lexdrift.report.json_writer import FORMAT_VERSION

ROOT = Path(__file__).resolve().parent.parent
PROJECT = tomllib.loads((ROOT / "pyproject.toml").read_text(encoding="utf-8"))["project"]

STABLE_CODES = {
    "lexdrift.error",
    "lexdrift.config",
    "lexdrift.ingest",
    "lexdrift.ingest.parse",
    "lexdrift.ingest.format",
    "lexdrift.canon",
    "lexdrift.store",
    "lexdrift.match",
}


def test_the_version_is_one_zero() -> None:
    assert lexdrift.__version__ == "1.0.0"
    assert PROJECT["version"] == lexdrift.__version__


def test_the_error_codes_are_the_published_set() -> None:
    published = {
        cls.code
        for cls in vars(errors).values()
        if isinstance(cls, type) and issubclass(cls, errors.LexdriftError)
    }
    assert published == STABLE_CODES


def test_every_code_is_namespaced() -> None:
    assert all(code.startswith("lexdrift.") for code in STABLE_CODES)


def test_the_on_disk_versions_are_pinned() -> None:
    assert CODEC_VERSION == 1
    assert SCHEMA_VERSION == 4
    assert FORMAT_VERSION == 1


def test_the_project_is_classified() -> None:
    classifiers = PROJECT["classifiers"]
    assert "Typing :: Typed" in classifiers
    assert any(entry.startswith("Development Status ::") for entry in classifiers)
    assert any("3.12" in entry for entry in classifiers)


def test_the_project_has_keywords() -> None:
    assert "legislation" in PROJECT["keywords"]
    assert len(PROJECT["keywords"]) >= 3


def test_the_project_names_its_source() -> None:
    urls = PROJECT["urls"]
    assert urls["Source"].startswith("https://")
    assert "Issues" in urls


def test_the_licence_and_readme_are_declared() -> None:
    assert PROJECT["readme"] == "README.md"
    assert PROJECT["license"]["text"]


@pytest.mark.parametrize("name", sorted(STABLE_CODES))
def test_each_code_has_a_status(name: str) -> None:
    match = next(
        cls
        for cls in vars(errors).values()
        if isinstance(cls, type) and issubclass(cls, errors.LexdriftError) and cls.code == name
    )
    assert 400 <= match.http_status <= 599
