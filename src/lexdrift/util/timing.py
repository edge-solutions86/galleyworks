"""Timing a run without reaching for a profiler.

A corpus pass has four stages and one of them is always the problem. Which one
depends on the corpus: a small corpus of long acts is dominated by
canonicalization, a large corpus of short ordinances by candidate generation.
Guessing wrong costs an afternoon, so the pipeline carries a stopwatch.

Wall clock, deliberately. The interesting stalls are sqlite waits and page
faults, and CPU time hides both.
"""

from __future__ import annotations

from collections.abc import Iterator
from contextlib import contextmanager
from dataclasses import dataclass, field
from time import perf_counter

__all__ = ["Stopwatch", "Timing"]


@dataclass(frozen=True, slots=True)
class Timing:
    """How long one stage took, and how often it ran."""

    name: str
    seconds: float
    calls: int

    @property
    def average(self) -> float:
        return 0.0 if self.calls == 0 else self.seconds / self.calls

    def __str__(self) -> str:
        return f"{self.name}: {self.seconds:.3f}s over {self.calls} calls"


@dataclass(slots=True)
class Stopwatch:
    """Accumulates elapsed time per named stage."""

    _seconds: dict[str, float] = field(default_factory=dict)
    _calls: dict[str, int] = field(default_factory=dict)

    @contextmanager
    def stage(self, name: str) -> Iterator[None]:
        """Time the block, adding it to ``name``."""

        started = perf_counter()
        try:
            yield
        finally:
            self.record(name, perf_counter() - started)

    def record(self, name: str, seconds: float) -> None:
        if seconds < 0:
            raise ValueError(f"elapsed time cannot be negative, got {seconds}")
        self._seconds[name] = self._seconds.get(name, 0.0) + seconds
        self._calls[name] = self._calls.get(name, 0) + 1

    def __len__(self) -> int:
        return len(self._seconds)

    def __contains__(self, name: object) -> bool:
        return name in self._seconds

    @property
    def total(self) -> float:
        return sum(self._seconds.values())

    def timings(self) -> list[Timing]:
        """Every stage, slowest first."""

        found = [
            Timing(name=name, seconds=seconds, calls=self._calls[name])
            for name, seconds in self._seconds.items()
        ]
        found.sort(key=lambda timing: (-timing.seconds, timing.name))
        return found

    def report(self) -> list[str]:
        """One line per stage, plus a total."""

        lines = [str(timing) for timing in self.timings()]
        lines.append(f"total: {self.total:.3f}s")
        return lines

    def reset(self) -> None:
        self._seconds.clear()
        self._calls.clear()
