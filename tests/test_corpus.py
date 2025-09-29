from __future__ import annotations

from pathlib import Path

import pytest

from conftest_corpus import MODEL_CLAUSE, make_bill
from lexdrift.corpus import discover, load_manifest, read_all
from lexdrift.errors import ConfigError, IngestError


def write_bill(root: Path, name: str, body: str = MODEL_CLAUSE) -> Path:
    instrument = make_bill(jurisdiction="wy", number="HB0142", body=body)
    path = root / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(instrument.raw_text, encoding="utf-8")
    return path


def test_discovery_reads_hints_from_the_file_name(tmp_path: Path) -> None:
    write_bill(tmp_path, "wy_2025_HB0142.txt")
    corpus = discover(tmp_path)
    assert len(corpus) == 1
    assert corpus.entries[0].hints == {
        "jurisdiction": "wy",
        "session": "2025",
        "number": "HB0142",
    }


def test_discovery_walks_subdirectories(tmp_path: Path) -> None:
    write_bill(tmp_path / "wy", "wy_2025_HB0142.txt")
    write_bill(tmp_path / "mt", "mt_2025_HB0311.txt")
    assert len(discover(tmp_path)) == 2


def test_discovery_reports_its_jurisdictions(tmp_path: Path) -> None:
    write_bill(tmp_path, "wy_2025_HB0142.txt")
    write_bill(tmp_path, "mt_2025_HB0311.txt")
    assert discover(tmp_path).jurisdictions == ("mt", "wy")


def test_a_badly_named_file_is_skipped_not_guessed(tmp_path: Path) -> None:
    write_bill(tmp_path, "wy_2025_HB0142.txt")
    write_bill(tmp_path, "notes.txt")
    corpus = discover(tmp_path)
    assert len(corpus) == 1
    assert corpus.skipped[0].name == "notes.txt"


def test_unrelated_suffixes_are_ignored(tmp_path: Path) -> None:
    write_bill(tmp_path, "wy_2025_HB0142.txt")
    (tmp_path / "wy_2025_HB0143.pdf").write_bytes(b"%PDF-1.4")
    corpus = discover(tmp_path)
    assert len(corpus) == 1
    assert corpus.skipped == ()


def test_a_number_containing_underscores_survives(tmp_path: Path) -> None:
    write_bill(tmp_path, "co_2025_SB_007.txt")
    assert discover(tmp_path).entries[0].hints["number"] == "SB_007"


def test_discovery_needs_a_directory(tmp_path: Path) -> None:
    with pytest.raises(IngestError, match="not a directory"):
        discover(tmp_path / "absent")


def test_entries_parse_into_instruments(tmp_path: Path) -> None:
    write_bill(tmp_path, "wy_2025_HB0142.txt")
    instruments = list(read_all(discover(tmp_path)))
    assert len(instruments) == 1
    assert instruments[0].key.slug == "wy/2025/lower/HB0142"


def test_a_manifest_names_its_documents(tmp_path: Path) -> None:
    write_bill(tmp_path / "bills", "one.txt")
    manifest = tmp_path / "corpus.yaml"
    manifest.write_text(
        "root: bills\n"
        "documents:\n"
        "  - path: one.txt\n"
        "    jurisdiction: wy\n"
        "    session: '2025'\n"
        "    number: HB0142\n",
        encoding="utf-8",
    )
    corpus = load_manifest(manifest)
    assert len(corpus) == 1
    assert corpus.entries[0].hints["number"] == "HB0142"
    assert list(read_all(corpus))[0].key.jurisdiction == "wy"


def test_a_manifest_can_pin_a_reader(tmp_path: Path) -> None:
    write_bill(tmp_path, "one.txt")
    manifest = tmp_path / "corpus.yaml"
    manifest.write_text(
        "documents:\n"
        "  - path: one.txt\n"
        "    format: plaintext\n"
        "    jurisdiction: wy\n"
        "    session: '2025'\n"
        "    number: HB0142\n",
        encoding="utf-8",
    )
    corpus = load_manifest(manifest)
    assert corpus.entries[0].format_name == "plaintext"
    assert list(read_all(corpus))[0].source_format == "plaintext"


def test_a_missing_manifest_is_rejected(tmp_path: Path) -> None:
    with pytest.raises(ConfigError, match="cannot be read"):
        load_manifest(tmp_path / "absent.yaml")


def test_a_manifest_without_documents_is_rejected(tmp_path: Path) -> None:
    manifest = tmp_path / "corpus.yaml"
    manifest.write_text("root: bills\n", encoding="utf-8")
    with pytest.raises(ConfigError, match="needs a documents list"):
        load_manifest(manifest)


def test_a_manifest_entry_without_a_path_is_rejected(tmp_path: Path) -> None:
    manifest = tmp_path / "corpus.yaml"
    manifest.write_text("documents:\n  - jurisdiction: wy\n", encoding="utf-8")
    with pytest.raises(ConfigError, match="needs a path") as caught:
        load_manifest(manifest)
    assert caught.value.context["entry"] == 0


def test_a_manifest_with_a_scalar_documents_key_is_rejected(tmp_path: Path) -> None:
    manifest = tmp_path / "corpus.yaml"
    manifest.write_text("documents: one.txt\n", encoding="utf-8")
    with pytest.raises(ConfigError, match="must be a list"):
        load_manifest(manifest)


def test_broken_manifest_yaml_is_rejected(tmp_path: Path) -> None:
    manifest = tmp_path / "corpus.yaml"
    manifest.write_text("documents: [\n", encoding="utf-8")
    with pytest.raises(ConfigError, match="not valid YAML"):
        load_manifest(manifest)
