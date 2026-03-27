"""lexdrift - passage-reuse detection for legislative instruments."""

from __future__ import annotations

from lexdrift.errors import (
    CanonError,
    ConfigError,
    IngestError,
    LexdriftError,
    MatchError,
    ParseError,
    StoreError,
    UnknownFormatError,
    error_code_of,
    http_status_of,
)

__version__ = "0.1.0"

__all__ = [
    "CanonError",
    "ConfigError",
    "IngestError",
    "LexdriftError",
    "MatchError",
    "ParseError",
    "StoreError",
    "UnknownFormatError",
    "__version__",
    "error_code_of",
    "http_status_of",
]
