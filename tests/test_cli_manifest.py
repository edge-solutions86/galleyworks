from __future__ import annotations

import io
from pathlib import Path

import pytest
import yaml

from golden_corpus import golden_instruments
from lexdrift.cli import build_cli
from lexdrift.cli.main import EXIT_ERROR, EXIT_FINDINGS, EXIT_OK, main
from lexdrift.cli.manifest_cmd import build_manifest
from lexdrift.corpus import load_manifest, read_all


@pytest.fixture(autouse=True)
def _loaded() -> None:
    build_cli()


def run(*argv: str) -> tuple[int, str]:
    stream = io.StringIO()
    code = main(list(argv), stream)
    return code, stream.getvalue()


@pytest.fixture
def corpus(tmp_path: Path) -> Path:
    root = tmp_path / "bills"
    root.mkdir()
    for instrument in golden_instruments():
        key = instrument.key
        name = f"{key.jurisdiction}_{key.session}_{key.number}.txt"
        (root / name).write_text(instrument.raw_text, encoding="utf-8")
    return root


def test_a_manifest_names_every_document(corpus: Path) -> None:
    payload, skipped = build_manifest(str(corpus))
    assert len(payload["documents"]) == 6
    assert skipped == []


def test_paths_are_relative_and_posix(corpus: Path) -> None:
    payload, _ = build_manifest(str(corpus))
    paths = [document["path"] for document in payload["documents"]]
    assert all("\\" not in path for path in paths)
    assert "wy_2023_HB0142.txt" in paths


def test_enrichment_fills_in_the_chamber_and_date(corpus: Path) -> None:
    payload, _ = build_manifest(str(corpus))
    row = next(d for d in payload["documents"] if d["number"] == "SB0007")
    assert row["chamber"] == "upper"
    assert row["format"] == "enrolled"
    assert row["enacted_on"] == "2025-09-01"


def test_enrichment_can_be_switched_off(corpus: Path) -> None:
    payload, _ = build_manifest(str(corpus), enrich=False)
    assert all("chamber" not in document for document in payload["documents"])


def test_the_manifest_round_trips_through_the_loader(corpus: Path, tmp_path: Path) -> None:
    target = corpus / "corpus.yaml"
    run("manifest", "--out", str(target), str(corpus))
    loaded = load_manifest(target)
    assert len(loaded) == 6
    assert len(list(read_all(loaded))) == 6


def test_the_command_prints_to_stdout(corpus: Path) -> None:
    code, output = run("manifest", str(corpus))
    assert code == EXIT_OK
    payload = yaml.safe_load(output)
    assert len(payload["documents"]) == 6


def test_the_command_writes_a_file(corpus: Path, tmp_path: Path) -> None:
    target = tmp_path / "corpus.yaml"
    code, output = run("manifest", "--out", str(target), str(corpus))
    assert code == EXIT_OK
    assert target.exists()
    assert "6 documents written" in output


def test_an_existing_manifest_is_not_overwritten(corpus: Path, tmp_path: Path) -> None:
    target = tmp_path / "corpus.yaml"
    target.write_text("documents: []\n", encoding="utf-8")
    assert run("manifest", "--out", str(target), str(corpus))[0] == EXIT_ERROR
    assert target.read_text(encoding="utf-8") == "documents: []\n"


def test_force_overwrites(corpus: Path, tmp_path: Path) -> None:
    target = tmp_path / "corpus.yaml"
    target.write_text("documents: []\n", encoding="utf-8")
    assert run("manifest", "--force", "--out", str(target), str(corpus))[0] == EXIT_OK
    assert "wy_2023_HB0142.txt" in target.read_text(encoding="utf-8")


def test_skipped_files_are_a_finding(corpus: Path) -> None:
    (corpus / "notes.txt").write_text("scratch\n", encoding="utf-8")
    code, output = run("manifest", str(corpus))
    assert code == EXIT_FINDINGS
    assert "skipped" in output


def test_a_missing_directory_is_an_error(tmp_path: Path) -> None:
    assert run("manifest", str(tmp_path / "absent"))[0] == EXIT_ERROR
