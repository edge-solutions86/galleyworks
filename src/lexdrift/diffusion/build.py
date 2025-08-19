"""Turning matches into a dated, directed reuse graph.

Direction comes from the calendar and nothing else. A match says two instruments
share text; it cannot say which one wrote it, and inferring direction from
length or from score would be inventing evidence. When both instruments carry an
effective date the earlier one is the source. When they do not, the edge is
recorded undirected with a stable source chosen by slug, so a run over the same
corpus produces the same graph.

Weight is the reused character count divided by the length of the later
instrument's matched segment, which reads as "how much of this clause is not
original".
"""

from __future__ import annotations

from collections.abc import Iterable, Mapping
from datetime import date

from lexdrift.diffusion.graph import ReuseEdge, ReuseGraph
from lexdrift.identity import InstrumentKey
from lexdrift.match.engine import SegmentMatch
from lexdrift.models import Instrument

__all__ = ["build_graph", "edge_for", "orient"]


def orient(
    left: InstrumentKey,
    right: InstrumentKey,
    dates: Mapping[str, date],
) -> tuple[InstrumentKey, InstrumentKey, bool]:
    """Return ``(source, target, directed)`` for one pair."""

    left_date = dates.get(left.slug)
    right_date = dates.get(right.slug)
    if left_date is not None and right_date is not None and left_date != right_date:
        if left_date < right_date:
            return left, right, True
        return right, left, True
    if left.slug <= right.slug:
        return left, right, False
    return right, left, False


def edge_for(match: SegmentMatch, dates: Mapping[str, date]) -> ReuseEdge:
    """Build one edge from one match."""

    source, target, directed = orient(match.query.key, match.other.key, dates)
    characters = sum(passage.length for passage in match.passages)
    return ReuseEdge(
        source=source,
        target=target,
        passages=len(match.passages),
        characters=characters,
        weight=match.score.best_containment,
        directed=directed,
    )


def build_graph(
    matches: Iterable[SegmentMatch],
    instruments: Iterable[Instrument] = (),
) -> ReuseGraph:
    """Fold every match into one graph, dated from the instruments given."""

    graph = ReuseGraph()
    dates: dict[str, date] = {}
    for instrument in instruments:
        graph.note(instrument.key, instrument.effective_date)
        if instrument.effective_date is not None:
            dates[instrument.key.slug] = instrument.effective_date
    for match in matches:
        graph.add(edge_for(match, dates))
    return graph
