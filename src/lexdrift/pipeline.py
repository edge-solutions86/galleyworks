"""The engine facade.

Everything below this module is composable on its own; this is the one object
that knows the order. It also holds the two things the stages cannot share
otherwise: the text source used to quote the other side of a match, and the
boilerplate filter, which can only be learned once the whole corpus is indexed.

The ordering constraint worth stating: an instrument must be indexed before any
later instrument is matched, and it must not be matched against an index that
already contains it, or every segment matches itself perfectly. :meth:`run`
indexes and matches in one forward pass over the corpus, which satisfies both.
"""

from __future__ import annotations

from collections.abc import Iterable, Sequence
from dataclasses import dataclass, field
from datetime import datetime

from lexdrift.config import EngineConfig
from lexdrift.corpus import Corpus, read_all
from lexdrift.diffusion.build import build_graph
from lexdrift.diffusion.graph import ReuseGraph
from lexdrift.fingerprint.records import Fingerprinter
from lexdrift.index.base import Index, open_index
from lexdrift.ingest.registry import ReaderRegistry, default_registry
from lexdrift.match.boilerplate import BoilerplateFilter, learn_boilerplate
from lexdrift.match.dedupe import dedupe_matches
from lexdrift.match.engine import Matcher, SegmentMatch
from lexdrift.models import Instrument
from lexdrift.report.model import InstrumentReport, MappingTextSource, build_report
from lexdrift.util.logging import get_logger
from lexdrift.util.timing import Stopwatch

__all__ = ["Engine", "RunResult"]

_log = get_logger("pipeline")


@dataclass(slots=True)
class RunResult:
    """What one pass over a corpus produced."""

    instruments: tuple[Instrument, ...] = ()
    reports: tuple[InstrumentReport, ...] = field(default_factory=tuple)
    matches: tuple[SegmentMatch, ...] = field(default_factory=tuple)
    graph: ReuseGraph = field(default_factory=ReuseGraph)
    indexed_segments: int = 0
    timings: tuple[str, ...] = ()

    @property
    def instrument_count(self) -> int:
        return len(self.instruments)

    @property
    def match_count(self) -> int:
        return len(self.matches)

    def report_for(self, slug: str) -> InstrumentReport | None:
        for report in self.reports:
            if report.key.slug == slug:
                return report
        return None


class Engine:
    """Ingest, index, match and report, in that order."""

    def __init__(
        self,
        config: EngineConfig | None = None,
        index: Index | None = None,
        registry: ReaderRegistry | None = None,
    ) -> None:
        self.config = config or EngineConfig()
        self.index = index if index is not None else open_index(config=self.config)
        self.registry = registry or default_registry()
        self.texts = MappingTextSource()
        self.boilerplate = BoilerplateFilter()
        self.stopwatch = Stopwatch()
        self._fingerprinter = Fingerprinter(self.config)

    def ingest(self, corpus: Corpus) -> list[Instrument]:
        """Parse every document in ``corpus``."""

        with self.stopwatch.stage("ingest"):
            instruments = list(read_all(corpus, self.registry))
        for instrument in instruments:
            self.texts.add(instrument)
        _log.info("corpus ingested", extra={"documents": len(instruments)})
        return instruments

    def add(self, instrument: Instrument) -> int:
        """Index one instrument, returning how many segments went in."""

        self.texts.add(instrument)
        with self.stopwatch.stage("fingerprint"):
            records = self._fingerprinter.fingerprint(instrument)
        with self.stopwatch.stage("index"):
            return self.index.add_all(records)

    def add_all(self, instruments: Iterable[Instrument]) -> int:
        return sum(self.add(instrument) for instrument in instruments)

    def learn_boilerplate(self, *, max_ratio: float = 0.2, min_segments: int = 5) -> int:
        """Learn the corpus boilerplate from whatever is indexed so far."""

        self.boilerplate = learn_boilerplate(
            iter(self.index), max_ratio=max_ratio, min_segments=min_segments
        )
        return len(self.boilerplate)

    def matcher(self) -> Matcher:
        return Matcher(self.index, self.config, self.boilerplate)

    def analyse(
        self, instrument: Instrument, *, now: datetime | None = None
    ) -> tuple[InstrumentReport, list[SegmentMatch]]:
        """Match one instrument and build its report."""

        with self.stopwatch.stage("match"):
            matches = dedupe_matches(self.matcher().match_instrument(instrument))
        with self.stopwatch.stage("report"):
            report = build_report(
                instrument,
                matches,
                self.texts,
                now=now,
                match_config=self.config.match,
            )
        return report, matches

    def run(self, corpus: Corpus, *, now: datetime | None = None) -> RunResult:
        """Ingest a corpus, then index and match it in one forward pass."""

        instruments = self.ingest(corpus)
        return self.run_over(instruments, now=now)

    def run_over(
        self, instruments: Sequence[Instrument], *, now: datetime | None = None
    ) -> RunResult:
        """Index and match an already parsed sequence, in order."""

        reports: list[InstrumentReport] = []
        found: list[SegmentMatch] = []
        indexed = 0
        for instrument in instruments:
            report, matches = self.analyse(instrument, now=now)
            reports.append(report)
            found.extend(matches)
            indexed += self.add(instrument)
        with self.stopwatch.stage("diffusion"):
            graph = build_graph(found, instruments)
        _log.info(
            "run finished",
            extra={"documents": len(instruments), "matches": len(found), "edges": len(graph)},
        )
        return RunResult(
            instruments=tuple(instruments),
            reports=tuple(reports),
            matches=tuple(found),
            graph=graph,
            indexed_segments=indexed,
            timings=tuple(self.stopwatch.report()),
        )
