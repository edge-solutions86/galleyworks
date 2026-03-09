"""The lexdrift command line.

Subcommand modules register themselves on import, so the package has to import
them before a parser is built. :func:`build_cli` does that once and is safe to
call again.
"""

from __future__ import annotations

__all__ = ["build_cli"]

_loaded = False


def build_cli() -> None:
    """Import every subcommand module so the registry is populated."""

    global _loaded
    if _loaded:
        return
    from lexdrift.cli import (  # noqa: F401
        check_cmd,
        config_cmd,
        diff_cmd,
        diffusion_cmd,
        index_cmd,
        ingest_cmd,
        manifest_cmd,
        query_cmd,
        report_cmd,
        serve_cmd,
        stats_cmd,
    )

    _loaded = True
