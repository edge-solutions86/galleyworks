from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from conftest_corpus import LOCAL_CLAUSE, MODEL_CLAUSE, make_bill
from lexdrift import __version__
from lexdrift.errors import IngestError, StoreError
from lexdrift.pipeline import Engine
from lexdrift.service.app import create_app, error_status


@pytest.fixture
def client() -> TestClient:
    engine = Engine()
    engine.add(make_bill(jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE))
    return TestClient(create_app(engine))


def query_body(**overrides: object) -> dict[str, object]:
    copy = make_bill(jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE)
    body: dict[str, object] = {"text": copy.raw_text, "jurisdiction": "mt"}
    body.update(overrides)
    return body


def test_health_reports_the_index(client: TestClient) -> None:
    payload = client.get("/health").json()
    assert payload["status"] == "ok"
    assert payload["version"] == __version__
    assert payload["instruments"] == 1
    assert payload["segments"] > 0
    assert len(payload["engine_digest"]) == 16


def test_instruments_are_listed(client: TestClient) -> None:
    payload = client.get("/v1/instruments").json()
    assert payload["count"] == 1
    assert payload["instruments"][0]["key"] == "wy/2025/lower/HB0142"


def test_a_copied_bill_is_reported(client: TestClient) -> None:
    response = client.post("/v1/query", json=query_body())
    assert response.status_code == 200
    payload = response.json()
    assert payload["instrument"] == "mt/2025/lower/HB0311"
    assert payload["matches"]
    assert payload["matches"][0]["other_instrument"] == "wy/2025/lower/HB0142"


def test_an_original_bill_reports_nothing(client: TestClient) -> None:
    original = make_bill(jurisdiction="co", number="SB0007", body=LOCAL_CLAUSE)
    response = client.post("/v1/query", json={"text": original.raw_text, "jurisdiction": "co"})
    assert response.status_code == 200
    assert response.json()["matches"] == []


def test_text_can_be_left_out(client: TestClient) -> None:
    payload = client.post("/v1/query", json=query_body(include_text=False)).json()
    assert payload["matches"][0]["passages"][0]["query_text"] is None


def test_the_query_document_is_not_indexed(client: TestClient) -> None:
    client.post("/v1/query", json=query_body())
    assert client.get("/health").json()["instruments"] == 1


def test_a_document_with_no_identity_is_a_422(client: TestClient) -> None:
    response = client.post("/v1/query", json={"text": "AN ACT\n", "jurisdiction": "mt"})
    assert response.status_code == 422
    assert response.json()["code"].startswith("lexdrift.ingest")


def test_an_unknown_reader_is_a_415(client: TestClient) -> None:
    response = client.post("/v1/query", json=query_body(format_name="nonesuch"))
    assert response.status_code == 415
    assert response.json()["code"] == "lexdrift.ingest.format"


def test_a_malformed_request_is_rejected(client: TestClient) -> None:
    assert client.post("/v1/query", json={"jurisdiction": "mt"}).status_code == 422
    assert client.post("/v1/query", json={"text": "", "jurisdiction": "mt"}).status_code == 422


def test_unknown_fields_are_rejected(client: TestClient) -> None:
    response = client.post("/v1/query", json=query_body(nonsense=1))
    assert response.status_code == 422


def test_the_error_map_is_used() -> None:
    assert error_status(IngestError("x")) == 422
    assert error_status(StoreError("x")) == 503
    assert error_status(ValueError("x")) == 500


def test_the_openapi_document_is_served(client: TestClient) -> None:
    payload = client.get("/openapi.json").json()
    assert payload["info"]["title"] == "lexdrift"
    assert "/v1/query" in payload["paths"]
