from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from golden_corpus import golden_instruments
from lexdrift.pipeline import Engine
from lexdrift.service.app import create_app
from lexdrift.service.auth import TOKEN_ENV, TokenPolicy, bearer_token, policy_from_env

TOKEN = "s3cret-token-value"


def engine() -> Engine:
    built = Engine()
    built.add(golden_instruments()[0])
    return built


def guarded() -> TestClient:
    return TestClient(create_app(engine(), TokenPolicy(token=TOKEN)))


def open_client() -> TestClient:
    return TestClient(create_app(engine(), TokenPolicy()))


def test_no_token_means_no_enforcement() -> None:
    policy = TokenPolicy()
    assert not policy.is_enforced
    assert policy.accepts(None)
    assert policy.accepts("anything")


def test_a_configured_token_is_enforced() -> None:
    policy = TokenPolicy(token=TOKEN)
    assert policy.is_enforced
    assert policy.accepts(TOKEN)
    assert not policy.accepts("wrong")
    assert not policy.accepts(None)


def test_the_policy_reads_the_environment() -> None:
    assert policy_from_env({TOKEN_ENV: TOKEN}).token == TOKEN
    assert policy_from_env({TOKEN_ENV: "   "}).token is None
    assert policy_from_env({}).token is None


@pytest.mark.parametrize(
    ("header", "expected"),
    [
        (f"Bearer {TOKEN}", TOKEN),
        (f"bearer {TOKEN}", TOKEN),
        ("Basic abc", None),
        ("Bearer", None),
        ("Bearer   ", None),
        (None, None),
        ("", None),
    ],
)
def test_the_header_is_parsed(header: str | None, expected: str | None) -> None:
    assert bearer_token(header) == expected


def test_an_open_service_answers_without_a_token() -> None:
    assert open_client().get("/v1/instruments").status_code == 200


def test_a_guarded_service_refuses_without_a_token() -> None:
    response = guarded().get("/v1/instruments")
    assert response.status_code == 401
    assert response.headers["WWW-Authenticate"] == "Bearer"
    assert response.json()["code"] == "lexdrift.unauthorized"


def test_a_guarded_service_accepts_the_right_token() -> None:
    response = guarded().get("/v1/instruments", headers={"Authorization": f"Bearer {TOKEN}"})
    assert response.status_code == 200


def test_a_guarded_service_refuses_the_wrong_token() -> None:
    response = guarded().get("/v1/instruments", headers={"Authorization": "Bearer nope"})
    assert response.status_code == 401


def test_health_is_exempt() -> None:
    assert guarded().get("/health").status_code == 200


def test_the_openapi_document_is_exempt() -> None:
    assert guarded().get("/openapi.json").status_code == 200


def test_the_query_endpoint_is_guarded() -> None:
    body = {"text": golden_instruments()[1].raw_text, "jurisdiction": "mt"}
    assert guarded().post("/v1/query", json=body).status_code == 401
    ok = guarded().post("/v1/query", json=body, headers={"Authorization": f"Bearer {TOKEN}"})
    assert ok.status_code == 200


def test_the_policy_is_recorded_on_the_app() -> None:
    client = guarded()
    assert client.app.state.token_policy.is_enforced  # type: ignore[attr-defined]
