"""The service routes.

They used to be closures inside ``create_app``, which meant the application
factory grew every time an endpoint did and nothing could be imported without
building an app first. Routers are module level, so a route can be read, typed
and tested on its own.

The corpus listing is paginated. A production index holds tens of thousands of
instruments and the first version of this endpoint returned all of them in one
response, which is fine until the day it is not.
"""

from __future__ import annotations

from fastapi import APIRouter, HTTPException, Query, status

from lexdrift import __version__
from lexdrift.errors import ConfigError
from lexdrift.identity import InstrumentKey
from lexdrift.ingest.base import SourceDocument
from lexdrift.service.schemas import (
    HealthResponse,
    InstrumentDetail,
    InstrumentOut,
    InstrumentsResponse,
    QueryRequest,
    ReportOut,
    SegmentOut,
)
from lexdrift.service.state import Service

__all__ = ["corpus_router", "meta_router", "query_router"]

MAX_PAGE = 500

meta_router = APIRouter(tags=["meta"])
corpus_router = APIRouter(prefix="/v1", tags=["corpus"])
query_router = APIRouter(prefix="/v1", tags=["query"])


@meta_router.get("/health", response_model=HealthResponse)
def health(service: Service) -> HealthResponse:
    """Liveness plus enough detail to tell two deployments apart."""

    stats = service.engine.index.stats()
    return HealthResponse(
        status="ok",
        version=__version__,
        engine_digest=service.digest,
        instruments=stats.instruments,
        segments=stats.segments,
    )


@corpus_router.get("/instruments", response_model=InstrumentsResponse)
def instruments(
    service: Service,
    limit: int = Query(default=100, ge=1, le=MAX_PAGE),
    offset: int = Query(default=0, ge=0),
    jurisdiction: str | None = Query(default=None, max_length=32),
) -> InstrumentsResponse:
    """One page of the indexed corpus."""

    keys = service.engine.index.instruments()
    if jurisdiction is not None:
        keys = [key for key in keys if key.jurisdiction == jurisdiction]
    page = keys[offset : offset + limit]
    return InstrumentsResponse(
        total=len(keys),
        offset=offset,
        limit=limit,
        instruments=tuple(InstrumentOut.of(key) for key in page),
    )


@corpus_router.get("/instruments/{jurisdiction}/{session}/{chamber}/{number}")
def instrument_detail(
    service: Service, jurisdiction: str, session: str, chamber: str, number: str
) -> InstrumentDetail:
    """Every indexed segment of one instrument."""

    slug = f"{jurisdiction}/{session}/{chamber}/{number}"
    try:
        key = InstrumentKey.parse(slug)
    except ConfigError as exc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND, detail="no such instrument"
        ) from exc
    segments = [record for record in service.engine.index if record.key == key]
    if not segments:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="no such instrument")
    segments.sort(key=lambda record: record.segment_index)
    return InstrumentDetail(
        instrument=InstrumentOut.of(key),
        segments=tuple(
            SegmentOut(
                index=record.segment_index,
                path=record.path,
                tokens=record.token_count,
                fingerprints=len(record.selected),
                content_ratio=round(record.content_ratio, 4),
            )
            for record in segments
        ),
    )


@query_router.post("/query", response_model=ReportOut)
def query(request: QueryRequest, service: Service) -> ReportOut:
    """Report what one document reuses, without indexing it."""

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
