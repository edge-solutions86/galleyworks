"""Exception hierarchy for lexdrift.

Every failure that can reach a caller carries a stable ``code``. The codes are
part of the CLI and service contract, so they change only on a major version.
"""

from __future__ import annotations

from typing import Any


class LexdriftError(Exception):
    """Base class for everything this package raises deliberately."""

    code = "lexdrift.error"

    def __init__(self, message: str, **context: Any) -> None:
        super().__init__(message)
        self.message = message
        self.context: dict[str, Any] = dict(context)

    def to_dict(self) -> dict[str, Any]:
        payload: dict[str, Any] = {"code": self.code, "message": self.message}
        if self.context:
            payload["context"] = dict(self.context)
        return payload

    def __str__(self) -> str:
        if not self.context:
            return self.message
        detail = ", ".join(f"{k}={v!r}" for k, v in sorted(self.context.items()))
        return f"{self.message} ({detail})"


class ConfigError(LexdriftError):
    """The engine was handed a configuration it cannot run with."""

    code = "lexdrift.config"


class IngestError(LexdriftError):
    """A source document could not be turned into an instrument."""

    code = "lexdrift.ingest"


class ParseError(IngestError):
    """A document parser hit text it cannot account for."""

    code = "lexdrift.ingest.parse"

    def __init__(self, message: str, *, line: int, column: int = 0, **context: Any) -> None:
        super().__init__(message, line=line, column=column, **context)
        self.line = line
        self.column = column

    def located(self) -> str:
        return f"line {self.line}, column {self.column}: {self.message}"


class UnknownFormatError(IngestError):
    """No registered reader claimed the document."""

    code = "lexdrift.ingest.format"


class CanonError(LexdriftError):
    """A canonicalization rule could not be applied."""

    code = "lexdrift.canon"


class StoreError(LexdriftError):
    """The index store rejected a read or a write."""

    code = "lexdrift.store"


class MatchError(LexdriftError):
    """Candidate generation or alignment failed."""

    code = "lexdrift.match"


def error_code_of(exc: BaseException) -> str:
    """Return the stable code for ``exc``, or the generic code for foreign errors."""

    if isinstance(exc, LexdriftError):
        return exc.code
    return "lexdrift.unexpected"
