from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from conftest_corpus import MODEL_CLAUSE, make_bill
from golden_corpus import golden_instruments
from lexdrift.pipeline import Engine
from lexdrift.service.app import create_app
from lexdrift.service.routes import MAX_PAGE
from lexdrift.service.state import ServiceState


@pytest.fixture
def client() -> TestClient:
    engine = Engine()
    for instrument in golden_instruments():
        engine.add(instrument)
    return TestClient(create_app(engine))


def test_the_state_is_on_the_application(client: TestClient) -> None:
    state = client.app.state.service  # type: ignore[attr-defined]
    assert isinstance(state, ServiceState)
    assert len(state.digest) == 16


def test_health_still_answers(client: TestClient) -> None:
    payload = client.get("/health").json()
    assert payload["status"] == "ok"
    assert payload["instruments"] == 6


def test_the_listing_is_paginated(client: TestClient) -> None:
    payload = client.get("/v1/instruments", params={"limit": 2}).json()
    assert payload["total"] == 6
    assert payload["limit"] == 2
    assert payload["offset"] == 0
    assert len(payload["instruments"]) == 2


def test_the_second_page_continues(client: TestClient) -> None:
    first = client.get("/v1/instruments", params={"limit": 2}).json()
    second = client.get("/v1/instruments", params={"limit": 2, "offset": 2}).json()
    keys = [item["key"] for item in first["instruments"]] + [
        item["key"] for item in second["instruments"]
    ]
    assert len(set(keys)) == 4


def test_an_offset_past_the_end_is_empty(client: TestClient) -> None:
    payload = client.get("/v1/instruments", params={"offset": 99}).json()
    assert payload["instruments"] == []
    assert payload["total"] == 6


def test_the_listing_can_be_filtered(client: TestClient) -> None:
    payload = client.get("/v1/instruments", params={"jurisdiction": "wy"}).json()
    assert payload["total"] == 1
    assert payload["instruments"][0]["jurisdiction"] == "wy"


def test_instrument_rows_are_fully_named(client: TestClient) -> None:
    row = client.get("/v1/instruments", params={"jurisdiction": "wy"}).json()["instruments"][0]
    assert row["key"] == "wy/2023/lower/HB0142"
    assert row["chamber"] == "lower"
    assert row["number"] == "HB0142"


def test_a_page_larger_than_the_cap_is_rejected(client: TestClient) -> None:
    response = client.get("/v1/instruments", params={"limit": MAX_PAGE + 1})
    assert response.status_code == 422


def test_a_negative_offset_is_rejected(client: TestClient) -> None:
    assert client.get("/v1/instruments", params={"offset": -1}).status_code == 422


def test_a_query_carries_its_explanation(client: TestClient) -> None:
    copy = make_bill(jurisdiction="mt", number="HB0999", body=MODEL_CLAUSE)
    response = client.post("/v1/query", json={"text": copy.raw_text, "jurisdiction": "mt"})
    assert response.status_code == 200
    payload = response.json()
    if payload["matches"]:
        match = payload["matches"][0]
        assert match["confidence"] in {"weak", "moderate", "strong"}
        assert match["headline"]
        assert match["reasons"]


def test_a_real_copy_is_reported(client: TestClient) -> None:
    montana = golden_instruments()[1]
    response = client.post("/v1/query", json={"text": montana.raw_text, "jurisdiction": "mt"})
    assert response.status_code == 200
    assert response.json()["matches"]


def test_the_openapi_document_lists_the_routes(client: TestClient) -> None:
    paths = client.get("/openapi.json").json()["paths"]
    assert set(paths) == {"/health", "/v1/instruments", "/v1/query"}


def test_error_mapping_still_applies(client: TestClient) -> None:
    response = client.post("/v1/query", json={"text": "AN ACT\n", "jurisdiction": "mt"})
    assert response.status_code == 422
    assert response.json()["code"].startswith("lexdrift.ingest")
