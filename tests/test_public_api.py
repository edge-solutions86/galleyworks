"""The names a caller is allowed to import from the package root.

Adding to this list is a feature; removing from it is a breaking change. The
test exists so that the second one cannot happen by accident during a refactor
of the module layout.
"""

from __future__ import annotations

import importlib
from pathlib import Path

import pytest

import lexdrift

EXPECTED = {
    "CanonConfig",
    "CanonError",
    "Chamber",
    "ConfigError",
    "Corpus",
    "Engine",
    "EngineConfig",
    "GovernmentLevel",
    "IngestConfig",
    "IngestError",
    "Instrument",
    "InstrumentKey",
    "InstrumentReport",
    "Jurisdiction",
    "LexdriftError",
    "LshConfig",
    "MatchConfig",
    "MatchError",
    "ParseError",
    "ReportConfig",
    "RunResult",
    "Segment",
    "SegmentKind",
    "ShingleConfig",
    "StoreError",
    "UnknownFormatError",
    "__version__",
    "discover",
    "error_code_of",
    "http_status_of",
    "load_config",
    "load_manifest",
}


def test_the_public_surface_is_pinned() -> None:
    assert set(lexdrift.__all__) == EXPECTED


def test_every_exported_name_exists() -> None:
    missing = [name for name in lexdrift.__all__ if not hasattr(lexdrift, name)]
    assert missing == []


def test_the_export_list_is_sorted() -> None:
    assert list(lexdrift.__all__) == sorted(lexdrift.__all__)


def test_the_version_is_a_release_number() -> None:
    parts = lexdrift.__version__.split(".")
    assert len(parts) == 3
    assert all(part.isdigit() for part in parts)


def test_the_readme_example_runs(tmp_path: Path) -> None:
    from golden_corpus import golden_instruments

    root = tmp_path / "bills"
    root.mkdir()
    for instrument in golden_instruments():
        key = instrument.key
        (root / f"{key.jurisdiction}_{key.session}_{key.number}.txt").write_text(
            instrument.raw_text, encoding="utf-8"
        )
    engine = lexdrift.Engine(lexdrift.EngineConfig())
    result = engine.run(lexdrift.discover(root))
    reported = [
        (report.key.slug, report.summary.reuse_ratio)
        for report in result.reports
        if not report.is_empty
    ]
    assert reported
    assert all(0.0 < ratio <= 1.0 for _, ratio in reported)


def test_the_engine_is_the_pipeline_engine() -> None:
    assert lexdrift.Engine is importlib.import_module("lexdrift.pipeline").Engine


def test_the_config_types_are_the_config_module_types() -> None:
    config = importlib.import_module("lexdrift.config")
    assert lexdrift.EngineConfig is config.EngineConfig
    assert lexdrift.ReportConfig is config.ReportConfig


@pytest.mark.parametrize("name", ["discover", "load_manifest", "load_config"])
def test_the_exported_helpers_are_callable(name: str) -> None:
    assert callable(getattr(lexdrift, name))
