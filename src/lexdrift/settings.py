"""Loading :class:`~lexdrift.config.EngineConfig` from files and the environment.

Precedence, lowest first: packaged defaults, the YAML file, then environment
variables. An operator can therefore keep a checked-in profile and still retune
one threshold on a single box without editing it.

Environment keys look like ``LEXDRIFT__MATCH__MIN_CONTAINMENT=0.5``. The double
underscore is the nesting separator, because single underscores already appear
inside field names.
"""

from __future__ import annotations

import os
from collections.abc import Mapping
from pathlib import Path
from typing import Any

import yaml

from lexdrift.config import EngineConfig
from lexdrift.errors import ConfigError

__all__ = [
    "ENV_PREFIX",
    "ENV_SEPARATOR",
    "apply_env_overrides",
    "deep_merge",
    "load_config",
    "parse_scalar",
]

ENV_PREFIX = "LEXDRIFT__"
ENV_SEPARATOR = "__"


def parse_scalar(raw: str) -> Any:
    """Turn an environment string into the closest YAML scalar."""

    try:
        return yaml.safe_load(raw)
    except yaml.YAMLError:
        return raw


def deep_merge(base: Mapping[str, Any], overlay: Mapping[str, Any]) -> dict[str, Any]:
    """Recursively merge ``overlay`` onto ``base`` without mutating either."""

    merged: dict[str, Any] = dict(base)
    for key, value in overlay.items():
        existing = merged.get(key)
        if isinstance(existing, Mapping) and isinstance(value, Mapping):
            merged[key] = deep_merge(existing, value)
        else:
            merged[key] = value
    return merged


def apply_env_overrides(
    data: Mapping[str, Any], environ: Mapping[str, str] | None = None
) -> dict[str, Any]:
    """Fold ``LEXDRIFT__`` variables into ``data``."""

    source = os.environ if environ is None else environ
    overlay: dict[str, Any] = {}
    for name, raw in sorted(source.items()):
        if not name.startswith(ENV_PREFIX):
            continue
        path = [part.lower() for part in name[len(ENV_PREFIX) :].split(ENV_SEPARATOR) if part]
        if not path:
            raise ConfigError("environment override has no field path", variable=name)
        cursor = overlay
        for part in path[:-1]:
            branch = cursor.setdefault(part, {})
            if not isinstance(branch, dict):
                raise ConfigError("environment override collides with a scalar", variable=name)
            cursor = branch
        cursor[path[-1]] = parse_scalar(raw)
    return deep_merge(data, overlay)


def _read_yaml(path: Path) -> dict[str, Any]:
    try:
        text = path.read_text(encoding="utf-8")
    except OSError as exc:
        raise ConfigError("configuration file cannot be read", path=str(path)) from exc
    try:
        loaded = yaml.safe_load(text)
    except yaml.YAMLError as exc:
        raise ConfigError("configuration file is not valid YAML", path=str(path)) from exc
    if loaded is None:
        return {}
    if not isinstance(loaded, dict):
        raise ConfigError("configuration file must hold a mapping", path=str(path))
    return loaded


def load_config(
    path: str | Path | None = None,
    *,
    environ: Mapping[str, str] | None = None,
) -> EngineConfig:
    """Build a config from an optional YAML file plus environment overrides."""

    data: dict[str, Any] = {}
    if path is not None:
        data = _read_yaml(Path(path))
    merged = apply_env_overrides(data, environ)
    return EngineConfig.from_mapping(merged)
