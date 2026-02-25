from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from golden_corpus import golden_instruments
from lexdrift.pipeline import Engine
from lexdrift.service.app import create_app
from lexdrift.service.middleware import (
    DURATION_HEADER,
    REQUEST_ID_HEADER,
    TimingMiddleware,
    new_request_id,
)


@pytest.fixture
def client() -> TestClient:
    engine = Engine()
    for instrument in golden_instruments():
        engine.add(instrument)
    return TestClient(create_app(engine))


def test_request_ids_are_unique_and_short() -> None:
    ids = {new_request_id() for _ in range(50)}
    assert len(ids) == 50
    assert all(len(value) == 16 for value in ids)


def test_the_middleware_is_installed(client: TestClient) -> None:
    installed = [entry.cls for entry in client.app.user_middleware]  # type: ignore[attr-defined]
    assert TimingMiddleware in installed


def test_a_response_carries_a_request_id(client: TestClient) -> None:
    response = client.get("/health")
    assert response.headers[REQUEST_ID_HEADER]
    assert len(response.headers[REQUEST_ID_HEADER]) == 16


def test_a_supplied_request_id_is_echoed(client: TestClient) -> None:
    response = client.get("/health", headers={REQUEST_ID_HEADER: "abc123"})
    assert response.headers[REQUEST_ID_HEADER] == "abc123"


def test_a_response_carries_its_duration(client: TestClient) -> None:
    response = client.get("/health")
    assert float(response.headers[DURATION_HEADER]) >= 0.0


def test_every_endpoint_is_timed(client: TestClient) -> None:
    for path in ("/health", "/v1/instruments"):
        assert DURATION_HEADER in client.get(path).headers


def test_a_query_is_timed(client: TestClient) -> None:
    montana = golden_instruments()[1]
    response = client.post("/v1/query", json={"text": montana.raw_text, "jurisdiction": "mt"})
    assert response.status_code == 200
    assert float(response.headers[DURATION_HEADER]) > 0.0


def test_an_error_response_is_still_timed(client: TestClient) -> None:
    response = client.post("/v1/query", json={"text": "AN ACT\n", "jurisdiction": "mt"})
    assert response.status_code == 422
    assert DURATION_HEADER in response.headers


def test_a_missing_route_is_still_timed(client: TestClient) -> None:
    response = client.get("/nowhere")
    assert response.status_code == 404
    assert REQUEST_ID_HEADER in response.headers


def test_the_slow_threshold_is_configurable() -> None:
    engine = Engine()
    app = create_app(engine)
    middleware = next(entry for entry in app.user_middleware if entry.cls is TimingMiddleware)
    assert middleware.kwargs["slow_ms"] > 0
