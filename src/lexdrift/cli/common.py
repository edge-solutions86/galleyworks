"""Options every command that touches an index needs.

Kept in one place so ``--index`` and ``--config`` mean the same thing in every
subcommand. A flag that drifts between commands is the kind of thing nobody
notices until an operator points the wrong index at the wrong corpus.
"""

from __future__ import annotations

import argparse
from pathlib import Path

from lexdrift.config import EngineConfig
from lexdrift.index.base import Index, open_index
from lexdrift.pipeline import Engine
from lexdrift.settings import load_config

__all__ = ["add_engine_options", "config_from", "engine_from", "index_from"]


def add_engine_options(parser: argparse.ArgumentParser, *, index_required: bool = False) -> None:
    """Add ``--index`` and ``--config`` to ``parser``."""

    parser.add_argument(
        "--index",
        dest="index_path",
        required=index_required,
        default=None,
        help="path to the sqlite index; omit for an in-memory one",
    )
    parser.add_argument(
        "--config",
        dest="config_path",
        default=None,
        help="path to an engine configuration file",
    )
    parser.add_argument(
        "--profile",
        dest="profile",
        default=None,
        help="canonicalization profile to apply",
    )


def config_from(args: argparse.Namespace) -> EngineConfig:
    """Build the engine configuration these arguments ask for."""

    config = load_config(getattr(args, "config_path", None))
    profile = getattr(args, "profile", None)
    if profile is None:
        return config
    from lexdrift.canon.profiles import profile as named_profile

    return EngineConfig.from_mapping(
        {**config.to_mapping(), "canon": named_profile(profile).model_dump(mode="json")}
    )


def index_from(args: argparse.Namespace, config: EngineConfig | None = None) -> Index:
    """Open the index these arguments name."""

    settings = config or config_from(args)
    path = getattr(args, "index_path", None)
    return open_index(None if path is None else Path(path), settings)


def engine_from(args: argparse.Namespace) -> Engine:
    """Build a fully wired engine from the common options."""

    config = config_from(args)
    return Engine(config, index_from(args, config))
