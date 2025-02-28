"""Structured logging on top of the standard library.

Corpus runs are long and mostly unattended, so the log is the only record of
what the engine decided. Records carry a ``fields`` mapping that the JSON
formatter flattens, which keeps ``grep``-ability without inventing a logging
framework.
"""

from __future__ import annotations

import json
import logging
import sys
from collections.abc import Iterator, Mapping
from contextlib import contextmanager
from contextvars import ContextVar
from typing import Any

__all__ = [
    "JsonFormatter",
    "bound",
    "configure_logging",
    "get_logger",
]

_EMPTY: Mapping[str, Any] = {}
_CONTEXT: ContextVar[Mapping[str, Any]] = ContextVar("lexdrift_log_context", default=_EMPTY)

_RESERVED = frozenset(logging.LogRecord("", 0, "", 0, "", None, None).__dict__) | {
    "message",
    "asctime",
    "taskName",
}


class JsonFormatter(logging.Formatter):
    """Render a record as one JSON object per line."""

    def format(self, record: logging.LogRecord) -> str:
        payload: dict[str, Any] = {
            "ts": self.formatTime(record, "%Y-%m-%dT%H:%M:%S"),
            "level": record.levelname.lower(),
            "logger": record.name,
            "message": record.getMessage(),
        }
        for key, value in record.__dict__.items():
            if key not in _RESERVED and not key.startswith("_"):
                payload[key] = value
        if record.exc_info is not None:
            payload["exception"] = self.formatException(record.exc_info)
        return json.dumps(payload, default=str, sort_keys=True)


class _ContextFilter(logging.Filter):
    """Copy the ambient context onto every record that passes through."""

    def filter(self, record: logging.LogRecord) -> bool:
        for key, value in _CONTEXT.get().items():
            if key not in record.__dict__:
                record.__dict__[key] = value
        return True


def configure_logging(*, level: str = "info", json_output: bool = True) -> None:
    """Install a single stderr handler, replacing any earlier one."""

    numeric = logging.getLevelNamesMapping().get(level.upper())
    if numeric is None:
        raise ValueError(f"unknown log level {level!r}")
    root = logging.getLogger("lexdrift")
    for handler in list(root.handlers):
        root.removeHandler(handler)
    handler = logging.StreamHandler(sys.stderr)
    if json_output:
        handler.setFormatter(JsonFormatter())
    else:
        handler.setFormatter(logging.Formatter("%(levelname)-8s %(name)s %(message)s"))
    handler.addFilter(_ContextFilter())
    root.addHandler(handler)
    root.setLevel(numeric)
    root.propagate = False


def get_logger(name: str) -> logging.Logger:
    """Return a child of the package logger."""

    if name.startswith("lexdrift"):
        return logging.getLogger(name)
    return logging.getLogger(f"lexdrift.{name}")


@contextmanager
def bound(**fields: Any) -> Iterator[None]:
    """Attach ``fields`` to every record emitted inside the block."""

    token = _CONTEXT.set({**_CONTEXT.get(), **fields})
    try:
        yield
    finally:
        _CONTEXT.reset(token)
