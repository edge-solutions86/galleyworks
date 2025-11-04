"""Options every command that touches an index needs.

Kept in one place so ``--index`` and ``--config`` mean the same thing in every
subcommand. A flag that drifts between commands is the kind of thing nobody
notices until an operator points the wrong index at the wrong corpus.

:func:`engine_options` is a decorator stack rather than a function that mutates
a parser, which is how click composes shared options.
"""

from __future__ import annotations

from collections.abc import Callable
from pathlib import Path
from typing import Any, TypeVar

import click

from lexdrift.config import EngineConfig
from lexdrift.index.base import Index, open_index
from lexdrift.pipeline import Engine
from lexdrift.settings import load_config

__all__ = ["build_config", "build_engine", "build_index", "engine_options"]

F = TypeVar("F", bound=Callable[..., Any])


def engine_options(*, index_required: bool = False) -> Callable[[F], F]:
    """Add ``--index``, ``--config`` and ``--profile`` to a command."""

    def decorate(command: F) -> F:
        command = click.option(
            "--profile",
            default=None,
            help="canonicalization profile to apply",
        )(command)
        command = click.option(
            "--config",
            "config_path",
            type=click.Path(dir_okay=False),
            default=None,
            help="path to an engine configuration file",
        )(command)
        command = click.option(
            "--index",
            "index_path",
            type=click.Path(dir_okay=False),
            required=index_required,
            default=None,
            help="path to the sqlite index; omit for an in-memory one",
        )(command)
        return command

    return decorate


def build_config(config_path: str | None, profile: str | None) -> EngineConfig:
    """Build the engine configuration these options ask for."""

    config = load_config(config_path)
    if profile is None:
        return config
    from lexdrift.canon.profiles import profile as named_profile

    return EngineConfig.from_mapping(
        {**config.to_mapping(), "canon": named_profile(profile).model_dump(mode="json")}
    )


def build_index(index_path: str | None, config: EngineConfig) -> Index:
    """Open the index these options name."""

    return open_index(None if index_path is None else Path(index_path), config)


def build_engine(index_path: str | None, config_path: str | None, profile: str | None) -> Engine:
    """Build a fully wired engine from the common options."""

    config = build_config(config_path, profile)
    return Engine(config, build_index(index_path, config))
