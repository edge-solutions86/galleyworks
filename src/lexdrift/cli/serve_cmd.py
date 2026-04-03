"""``lexdrift serve`` - run the HTTP service over an index.

The index is opened and loaded before the socket is bound, so a bad path fails
at startup rather than on the first request. ``--check`` does the loading and
stops, which is what a deployment smoke test wants.

uvicorn is imported inside the command. It pulls in a fair amount and the other
subcommands have no use for it.
"""

from __future__ import annotations

from typing import TYPE_CHECKING

import click

from lexdrift.cli.common import build_engine, engine_options
from lexdrift.cli.main import EXIT_OK, Runtime, cli
from lexdrift.errors import ConfigError
from lexdrift.pipeline import Engine

if TYPE_CHECKING:
    from fastapi import FastAPI

__all__ = ["build_service", "serve"]


def build_service(
    index_path: str | None,
    config_path: str | None,
    profile: str | None,
    reading: str | None = None,
) -> tuple[FastAPI, Engine]:
    """Build the ASGI app and the engine behind it."""

    try:
        from lexdrift.service.app import create_app
    except ImportError as exc:  # pragma: no cover - depends on the install extra
        raise ConfigError(
            "the service extra is not installed", remedy="pip install 'lexdrift[service]'"
        ) from exc
    engine = build_engine(index_path, config_path, profile, reading)
    return create_app(engine), engine


@cli.command(name="serve", short_help="run the http service over an index")
@engine_options(index_required=True)
@click.option("--host", default="127.0.0.1", show_default=True, help="interface to bind")
@click.option("--port", type=int, default=8000, show_default=True, help="port to bind")
@click.option("--check", is_flag=True, help="load the index and exit without binding")
@click.pass_obj
def serve(
    runtime: Runtime,
    index_path: str | None,
    config_path: str | None,
    profile: str | None,
    reading: str | None,
    host: str,
    port: int,
    check: bool,
) -> int:
    """Serve the index over HTTP."""

    app, engine = build_service(index_path, config_path, profile, reading)
    stats = engine.index.stats()
    runtime.line(
        f"index loaded: {stats.instruments} instruments, {stats.segments} segments, "
        f"digest {stats.digest}"
    )
    if check:
        runtime.line("check only, not binding")
        return EXIT_OK
    import uvicorn  # noqa: PLC0415

    runtime.line(f"listening on http://{host}:{port}")
    uvicorn.run(app, host=host, port=port, log_config=None)
    return EXIT_OK
