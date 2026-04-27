from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from golden_corpus import golden_instruments
from lexdrift.pipeline import Engine
from lexdrift.service.app import create_app

WYOMING = "/v1/instruments/wy/2023/lower/HB0142"


@pytest.fixture
def client() -> TestClient:
    engine = Engine()
    for instrument in golden_instruments():
        engine.add(instrument)
    return TestClient(create_app(engine))


def test_the_detail_names_the_instrument(client: TestClient) -> None:
    payload = client.get(WYOMING).json()
    assert payload["instrument"]["key"] == "wy/2023/lower/HB0142"
    assert payload["instrument"]["chamber"] == "lower"


def test_every_segment_is_listed(client: TestClient) -> None:
    payload = client.get(WYOMING).json()
    paths = [segment["path"] for segment in payload["segments"]]
    assert paths == ["1", "1(a)", "1(b)", "1(b)(i)", "1(b)(ii)", "2"]


def test_segments_come_back_in_document_order(client: TestClient) -> None:
    indexes = [segment["index"] for segment in client.get(WYOMING).json()["segments"]]
    assert indexes == sorted(indexes)


def test_each_segment_reports_its_size(client: TestClient) -> None:
    for segment in client.get(WYOMING).json()["segments"]:
        assert segment["tokens"] > 0
        assert segment["fingerprints"] >= 0
        assert 0.0 <= segment["content_ratio"] <= 1.0


def test_an_unknown_instrument_is_a_404(client: TestClient) -> None:
    response = client.get("/v1/instruments/wy/2023/lower/HB9999")
    assert response.status_code == 404


def test_a_malformed_key_is_a_404(client: TestClient) -> None:
    assert client.get("/v1/instruments/WY/2023/lower/HB0142").status_code == 404


def test_an_unknown_chamber_is_a_404(client: TestClient) -> None:
    assert client.get("/v1/instruments/wy/2023/senate/HB0142").status_code == 404


def test_the_route_is_documented(client: TestClient) -> None:
    paths = client.get("/openapi.json").json()["paths"]
    assert "/v1/instruments/{jurisdiction}/{session}/{chamber}/{number}" in paths


def test_the_listing_still_works(client: TestClient) -> None:
    assert client.get("/v1/instruments").json()["total"] == 6
