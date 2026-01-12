"""The HTTP service.

One index, loaded once, served read-only. Indexing is a batch job and putting it
behind an endpoint would let a request take an hour and hold a write lock while
it did. The service answers questions; ``lexdrift index`` changes the corpus.

The factory does three things and nothing else: build the state, map errors, and
mount the routers. Everything an endpoint does lives in
:mod:`lexdrift.service.routes`.
"""

from __future__ import annotations

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from lexdrift import __version__
from lexdrift.errors import LexdriftError
from lexdrift.pipeline import Engine
from lexdrift.service.routes import corpus_router, meta_router, query_router
from lexdrift.service.state import ServiceState
from lexdrift.util.logging import get_logger

__all__ = ["ServiceState", "create_app", "error_status"]

_log = get_logger("service")

_STATUS_BY_CODE: dict[str, int] = {
    "lexdrift.config": 400,
    "lexdrift.ingest": 422,
    "lexdrift.ingest.parse": 422,
    "lexdrift.ingest.format": 415,
    "lexdrift.store": 503,
    "lexdrift.match": 500,
    "lexdrift.canon": 500,
}


def error_status(exc: Exception) -> int:
    """The status code the service uses for ``exc``."""

    if isinstance(exc, LexdriftError):
        return _STATUS_BY_CODE.get(exc.code, 500)
    return 500


def create_app(engine: Engine | None = None) -> FastAPI:
    """Build the ASGI application around ``engine``."""

    state = ServiceState(engine=engine or Engine())

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        _log.info(
            "service starting",
            extra={"digest": state.digest, "segments": len(state.engine.index)},
        )
        yield
        _log.info("service stopping")

    app = FastAPI(
        title="lexdrift",
        version=__version__,
        summary="Passage-reuse detection for legislative instruments.",
        lifespan=lifespan,
    )
    app.state.service = state

    @app.exception_handler(LexdriftError)
    async def handle_lexdrift_error(request: Request, exc: LexdriftError) -> JSONResponse:
        _log.warning("request failed", extra={"code": exc.code, "path": request.url.path})
        return JSONResponse(status_code=error_status(exc), content=exc.to_dict())

    app.include_router(meta_router)
    app.include_router(corpus_router)
    app.include_router(query_router)
    return app
