from __future__ import annotations

import json
import logging
from collections.abc import Iterator
from typing import Any

import pytest

from lexdrift.util.logging import JsonFormatter, bound, configure_logging, get_logger


@pytest.fixture(autouse=True)
def reset_root() -> Iterator[None]:
    root = logging.getLogger("lexdrift")
    saved = list(root.handlers), root.level, root.propagate
    yield
    root.handlers = saved[0]
    root.setLevel(saved[1])
    root.propagate = saved[2]


def emitted(capsys: pytest.CaptureFixture[str]) -> list[dict[str, Any]]:
    err = capsys.readouterr().err.strip()
    return [json.loads(line) for line in err.splitlines() if line]


def test_get_logger_namespaces_under_the_package() -> None:
    assert get_logger("ingest").name == "lexdrift.ingest"
    assert get_logger("lexdrift.match").name == "lexdrift.match"


def test_json_records_carry_extra_fields(capsys: pytest.CaptureFixture[str]) -> None:
    configure_logging(level="info")
    get_logger("ingest").info("parsed", extra={"segments": 12, "key": "wy/2025/lower/HB1"})
    records = emitted(capsys)
    assert len(records) == 1
    assert records[0]["message"] == "parsed"
    assert records[0]["segments"] == 12
    assert records[0]["level"] == "info"
    assert records[0]["logger"] == "lexdrift.ingest"


def test_bound_context_is_attached_and_popped(capsys: pytest.CaptureFixture[str]) -> None:
    configure_logging(level="info")
    log = get_logger("match")
    with bound(run="r-1"):
        log.info("inside")
    log.info("outside")
    records = emitted(capsys)
    assert records[0]["run"] == "r-1"
    assert "run" not in records[1]


def test_bound_contexts_nest(capsys: pytest.CaptureFixture[str]) -> None:
    configure_logging(level="info")
    log = get_logger("match")
    with bound(run="r-1"), bound(stage="align"):
        log.info("deep")
    record = emitted(capsys)[0]
    assert record["run"] == "r-1"
    assert record["stage"] == "align"


def test_explicit_extra_beats_bound_context(capsys: pytest.CaptureFixture[str]) -> None:
    configure_logging(level="info")
    with bound(stage="align"):
        get_logger("match").info("shadow", extra={"stage": "score"})
    assert emitted(capsys)[0]["stage"] == "score"


def test_level_filters_records(capsys: pytest.CaptureFixture[str]) -> None:
    configure_logging(level="warning")
    log = get_logger("index")
    log.info("quiet")
    log.warning("loud")
    records = emitted(capsys)
    assert [r["message"] for r in records] == ["loud"]


def test_configure_replaces_handlers_rather_than_stacking() -> None:
    configure_logging(level="info")
    configure_logging(level="info")
    assert len(logging.getLogger("lexdrift").handlers) == 1


def test_unknown_level_is_rejected() -> None:
    with pytest.raises(ValueError, match="unknown log level"):
        configure_logging(level="chatty")


def test_plain_output_is_not_json(capsys: pytest.CaptureFixture[str]) -> None:
    configure_logging(level="info", json_output=False)
    get_logger("cli").info("hello")
    assert capsys.readouterr().err.strip().endswith("lexdrift.cli hello")


def test_formatter_records_exceptions() -> None:
    formatter = JsonFormatter()
    try:
        raise ValueError("boom")
    except ValueError:
        record = logging.LogRecord(
            "lexdrift.t", logging.ERROR, __file__, 1, "failed", None, __import__("sys").exc_info()
        )
    payload = json.loads(formatter.format(record))
    assert "ValueError: boom" in payload["exception"]
