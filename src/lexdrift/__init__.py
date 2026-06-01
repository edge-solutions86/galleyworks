"""lexdrift - passage-reuse detection for legislative instruments.

The package used to export only the exception hierarchy, which meant every
caller reached three modules deep for the four things anyone actually wants: an
engine, a configuration, a corpus and a report. Those are here.

Everything else stays where it is. Re-exporting the whole tree would make the
import surface look like the API surface, and most of this package is
implementation that should be free to move.

    from lexdrift import Engine, EngineConfig, discover

    engine = Engine(EngineConfig())
    result = engine.run(discover("bills/"))
    for report in result.reports:
        if not report.is_empty:
            print(report.key.slug, report.summary.reuse_ratio)
"""

from __future__ import annotations

from lexdrift.config import (
    CanonConfig,
    EngineConfig,
    IngestConfig,
    LshConfig,
    MatchConfig,
    ReportConfig,
    ShingleConfig,
)
from lexdrift.corpus import Corpus, discover, load_manifest
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
from lexdrift.identity import Chamber, GovernmentLevel, InstrumentKey, Jurisdiction
from lexdrift.models import Instrument, Segment, SegmentKind
from lexdrift.pipeline import Engine, RunResult
from lexdrift.report.model import InstrumentReport
from lexdrift.settings import load_config

__version__ = "0.9.0"

__all__ = [
    "CanonConfig",
    "CanonError",
    "Chamber",
    "ConfigError",
    "Corpus",
    "Engine",
    "EngineConfig",
    "GovernmentLevel",
    "IngestConfig",
    "IngestError",
    "Instrument",
    "InstrumentKey",
    "InstrumentReport",
    "Jurisdiction",
    "LexdriftError",
    "LshConfig",
    "MatchConfig",
    "MatchError",
    "ParseError",
    "ReportConfig",
    "RunResult",
    "Segment",
    "SegmentKind",
    "ShingleConfig",
    "StoreError",
    "UnknownFormatError",
    "__version__",
    "discover",
    "error_code_of",
    "http_status_of",
    "load_config",
    "load_manifest",
]
