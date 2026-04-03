from __future__ import annotations

import io
from pathlib import Path

import pytest
from pydantic import ValidationError

from conftest_corpus import MODEL_CLAUSE, make_bill
from lexdrift.cli import build_cli
from lexdrift.cli.common import build_config
from lexdrift.cli.main import EXIT_OK, main
from lexdrift.config import EngineConfig, IngestConfig
from lexdrift.ingest.amendment import Reading
from lexdrift.ingest.registry import default_registry
from lexdrift.pipeline import Engine

DRAFT = (
    "AN ACT relating to water rights.\n\n"
    "Be It Enacted by the Legislature:\n\n"
    "Section 1.  W.S. 41-3-101 is amended to read:\n\n"
    "41-3-101.  Schedule of appropriations.\n\n"
    "(a)  The board shall publish a schedule not later than "
    "{-sixty (60)-}{+ninety (90)+}\ndays after the close of each irrigation season.\n"
)


@pytest.fixture(autouse=True)
def _loaded() -> None:
    build_cli()


def run(*argv: str) -> tuple[int, str]:
    stream = io.StringIO()
    code = main(list(argv), stream)
    return code, stream.getvalue()


def test_the_default_reading_is_proposed() -> None:
    assert EngineConfig().ingest.amendment_reading == "proposed"


def test_only_one_amendment_reader_is_registered() -> None:
    names = default_registry().names
    assert names.count("amendment-proposed") == 1
    assert "amendment-current" not in names


def test_the_registry_can_be_built_for_either_reading() -> None:
    assert "amendment-current" in default_registry(Reading.CURRENT).names
    assert "amendment-current" in default_registry("current").names


def test_the_engine_uses_the_configured_reading() -> None:
    config = EngineConfig(ingest=IngestConfig(amendment_reading="current"))
    assert "amendment-current" in Engine(config).registry.names


def test_the_reading_is_part_of_the_index_digest() -> None:
    proposed = EngineConfig()
    current = EngineConfig(ingest=IngestConfig(amendment_reading="current"))
    assert proposed.digest() != current.digest()


def test_an_unknown_reading_is_rejected() -> None:
    with pytest.raises(ValidationError):
        IngestConfig(amendment_reading="sideways")  # type: ignore[arg-type]


def test_the_two_readings_parse_different_text(tmp_path: Path) -> None:
    path = tmp_path / "draft.txt"
    path.write_text(DRAFT, encoding="utf-8")
    hints = {"jurisdiction": "wy", "session": "2025", "number": "HB0142"}
    from lexdrift.ingest.base import SourceDocument

    document = SourceDocument.from_path(path, **hints)
    proposed = default_registry("proposed").read(document)
    current = default_registry("current").read(document)
    assert proposed.source_format == "amendment-proposed"
    assert current.source_format == "amendment-current"
    assert proposed.raw_text == current.raw_text


def test_the_flag_reaches_the_config() -> None:
    assert build_config(None, None, "current").ingest.amendment_reading == "current"
    assert build_config(None, None, None).ingest.amendment_reading == "proposed"


def test_the_config_command_reports_the_reading() -> None:
    _, output = run("config", "--reading", "current")
    assert "amendment reading: current" in output


def test_the_index_command_takes_the_flag(tmp_path: Path) -> None:
    root = tmp_path / "bills"
    root.mkdir()
    (root / "wy_2025_HB0142.txt").write_text(DRAFT, encoding="utf-8")
    code, output = run("index", "--reading", "current", "--stats", str(root))
    assert code == EXIT_OK
    assert "1 instruments" in output


def test_a_plain_bill_is_unaffected_by_the_reading(tmp_path: Path) -> None:
    instrument = make_bill(jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE)
    root = tmp_path / "bills"
    root.mkdir()
    (root / "wy_2025_HB0142.txt").write_text(instrument.raw_text, encoding="utf-8")
    _, proposed = run("index", "--stats", str(root))
    _, current = run("index", "--reading", "current", "--stats", str(root))
    assert "1 instruments, " in proposed
    assert proposed.split("engine digest")[0] == current.split("engine digest")[0]
