"""The HTTP service.

One index, loaded once, served read-only. Indexing is a batch job and putting it
behind an endpoint would let a request take an hour and hold a write lock while
it did. The service answers questions; ``lexdrift index`` changes the corpus.

Errors are mapped from the stable ``LexdriftError`` codes to status codes in one
handler, so a new error type gets sensible HTTP behaviour without touching any
endpoint.
"""

from __future__ import annotations

from contextlib import asynccontextmanager
from dataclasses import dataclass
from typing import Annotated, Any

from fastapi import Depends, FastAPI, Request
from fastapi.responses import JSONResponse

from lexdrift import __version__
from lexdrift.errors import ConfigError, IngestError, LexdriftError, StoreError
from lexdrift.ingest.base import SourceDocument
from lexdrift.pipeline import Engine
from lexdrift.service.schemas import HealthResponse, QueryRequest, ReportOut
from lexdrift.util.logging import get_logger

__all__ = ["ServiceState", "create_app"]

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


@dataclass(slots=True)
class ServiceState:
    """What the endpoints share."""

    engine: Engine

    @property
    def digest(self) -> str:
        return self.engine.config.digest()


def _status_for(exc: LexdriftError) -> int:
    return _STATUS_BY_CODE.get(exc.code, 500)


def get_state(request: Request) -> ServiceState:
    """The shared state, read off the application rather than a closure.

    It has to be module level: postponed annotations make every parameter type a
    string, and FastAPI resolves those against module globals, so a dependency
    alias defined inside the factory is invisible by the time a route is built.
    """

    state: ServiceState = request.app.state.service
    return state


Service = Annotated[ServiceState, Depends(get_state)]


def create_app(engine: Engine | None = None) -> FastAPI:
    """Build the ASGI application around ``engine``."""

    state = ServiceState(engine=engine or Engine())

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> Any:
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
        return JSONResponse(status_code=_status_for(exc), content=exc.to_dict())

    @app.get("/health", response_model=HealthResponse, tags=["meta"])
    def health(service: Service) -> HealthResponse:
        stats = service.engine.index.stats()
        return HealthResponse(
            status="ok",
            version=__version__,
            engine_digest=service.digest,
            instruments=stats.instruments,
            segments=stats.segments,
        )

    @app.get("/v1/instruments", tags=["corpus"])
    def instruments(service: Service) -> dict[str, Any]:
        keys = service.engine.index.instruments()
        return {
            "count": len(keys),
            "instruments": [
                {"key": key.slug, "jurisdiction": key.jurisdiction, "number": key.number}
                for key in keys
            ],
        }

    @app.post("/v1/query", response_model=ReportOut, tags=["query"])
    def query(request: QueryRequest, service: Service) -> ReportOut:
        hints: dict[str, str] = {"jurisdiction": request.jurisdiction}
        if request.session is not None:
            hints["session"] = request.session
        if request.number is not None:
            hints["number"] = request.number
        document = SourceDocument(text=request.text, origin="<request>", hints=hints)
        instrument = service.engine.registry.read(document, format_name=request.format_name)
        service.engine.texts.add(instrument)
        report, _ = service.engine.analyse(instrument)
        return ReportOut.of(report, digest=service.digest, include_text=request.include_text)

    return app


def error_status(exc: Exception) -> int:
    """The status code the service would use for ``exc``; exposed for tests."""

    if isinstance(exc, ConfigError | IngestError | StoreError | LexdriftError):
        return _status_for(exc)
    return 500
