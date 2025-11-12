from __future__ import annotations

import io
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from conftest_corpus import MODEL_CLAUSE, make_bill
from lexdrift.cli import build_cli
from lexdrift.cli.main import EXIT_ERROR, EXIT_OK, EXIT_USAGE, main
from lexdrift.cli.serve_cmd import build_service


@pytest.fixture(autouse=True)
def _loaded() -> None:
    build_cli()


def run(*argv: str) -> tuple[int, str]:
    stream = io.StringIO()
    code = main(list(argv), stream)
    return code, stream.getvalue()


@pytest.fixture
def index_path(tmp_path: Path) -> str:
    root = tmp_path / "bills"
    root.mkdir()
    instrument = make_bill(jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE)
    (root / "wy_2025_HB0142.txt").write_text(instrument.raw_text, encoding="utf-8")
    path = tmp_path / "index.sqlite"
    run("index", "--index", str(path), str(root))
    return str(path)


def test_check_loads_the_index_without_binding(index_path: str) -> None:
    code, output = run("serve", "--index", index_path, "--check")
    assert code == EXIT_OK
    assert "index loaded: 1 instruments" in output
    assert "check only, not binding" in output


def test_check_reports_the_digest(index_path: str) -> None:
    _, output = run("serve", "--index", index_path, "--check")
    assert "digest " in output


def test_the_index_option_is_required() -> None:
    assert run("serve", "--check")[0] == EXIT_USAGE


def test_a_missing_index_directory_is_an_error(tmp_path: Path) -> None:
    code, _ = run("serve", "--index", str(tmp_path / "missing" / "i.sqlite"), "--check")
    assert code == EXIT_ERROR


def test_the_built_app_serves_the_index(index_path: str) -> None:
    app, engine = build_service(index_path, None, None)
    assert len(engine.index) > 0
    client = TestClient(app)
    payload = client.get("/health").json()
    assert payload["instruments"] == 1


def test_the_built_app_answers_a_query(index_path: str) -> None:
    app, _ = build_service(index_path, None, None)
    copy = make_bill(jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE)
    response = TestClient(app).post("/v1/query", json={"text": copy.raw_text, "jurisdiction": "mt"})
    assert response.status_code == 200
    assert response.json()["matches"]


def test_a_profile_reaches_the_service(index_path: str) -> None:
    _, engine = build_service(None, None, "verbatim")
    assert engine.config.canon.profile == "verbatim"


def test_an_unknown_profile_is_an_error(index_path: str) -> None:
    code, _ = run("serve", "--index", index_path, "--profile", "nowhere", "--check")
    assert code == EXIT_ERROR
