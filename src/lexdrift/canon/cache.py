"""A bounded cache in front of the canonicalization pipeline.

Statutes repeat themselves. A corpus of amendments to one chapter re-prints the
same section in every bill that touches it, and canonicalizing it again for each
one is the largest avoidable cost in a corpus run.

The cache is keyed on the text and the settings digest together. Keying on text
alone would return a result canonicalized under a different profile, which is
the one wrong answer this module could plausibly give.

``functools.lru_cache`` is not used: the keys are whole documents and the values
hold an offset map each, so eviction has to be driven by bytes rather than by
entry count.
"""

from __future__ import annotations

from collections import OrderedDict
from dataclasses import dataclass

from lexdrift.canon.pipeline import CanonPipeline, CanonResult
from lexdrift.config import CanonConfig
from lexdrift.errors import ConfigError
from lexdrift.fingerprint.hashing import hash_bytes

__all__ = ["CacheStats", "CachingPipeline"]

DEFAULT_LIMIT = 8 * 1024 * 1024


@dataclass(frozen=True, slots=True)
class CacheStats:
    """How the cache is behaving."""

    hits: int
    misses: int
    entries: int
    bytes_held: int

    @property
    def lookups(self) -> int:
        return self.hits + self.misses

    @property
    def hit_rate(self) -> float:
        return 0.0 if self.lookups == 0 else self.hits / self.lookups


class CachingPipeline:
    """Wraps a pipeline with a byte-bounded least recently used cache."""

    def __init__(self, pipeline: CanonPipeline, *, limit_bytes: int = DEFAULT_LIMIT) -> None:
        if limit_bytes < 0:
            raise ConfigError("cache limit cannot be negative", limit_bytes=limit_bytes)
        self._pipeline = pipeline
        self._limit = limit_bytes
        self._entries: OrderedDict[str, CanonResult] = OrderedDict()
        self._bytes = 0
        self._hits = 0
        self._misses = 0

    @property
    def rule_names(self) -> tuple[str, ...]:
        return self._pipeline.rule_names

    def _key(self, text: str, config: CanonConfig) -> str:
        settings = config.model_dump_json().encode("utf-8")
        blob = text.encode("utf-8")
        return f"{hash_bytes(settings):016x}:{len(blob)}:{hash_bytes(blob):016x}"

    def run(self, text: str, config: CanonConfig | None = None) -> CanonResult:
        """Canonicalize ``text``, reusing an earlier result when there is one."""

        settings = config or CanonConfig()
        key = self._key(text, settings)
        found = self._entries.get(key)
        if found is not None:
            self._hits += 1
            self._entries.move_to_end(key)
            return found
        self._misses += 1
        result = self._pipeline.run(text, settings)
        self._store(key, result)
        return result

    def _store(self, key: str, result: CanonResult) -> None:
        size = len(result.source) + len(result.text)
        if size > self._limit:
            return
        self._entries[key] = result
        self._bytes += size
        while self._bytes > self._limit and self._entries:
            _, evicted = self._entries.popitem(last=False)
            self._bytes -= len(evicted.source) + len(evicted.text)

    def stats(self) -> CacheStats:
        return CacheStats(
            hits=self._hits,
            misses=self._misses,
            entries=len(self._entries),
            bytes_held=self._bytes,
        )

    def clear(self) -> None:
        self._entries.clear()
        self._bytes = 0
