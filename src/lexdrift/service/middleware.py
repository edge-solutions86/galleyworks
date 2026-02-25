"""Request timing and the access log.

A query walks the whole engine, so response times vary by two orders of
magnitude depending on the document. Serving that behind a proxy with a fixed
timeout and no per-request timing means the first sign of trouble is a 504 from
something that is not this process.

Every response carries a request id and a duration header, and every request
produces one structured log line. The id is generated here when the caller did
not supply one, so a slow request can be found in the log from the response
alone.
"""

from __future__ import annotations

import time
import uuid
from collections.abc import Awaitable, Callable
from typing import TYPE_CHECKING

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response

from lexdrift.util.logging import bound, get_logger

if TYPE_CHECKING:
    from starlette.types import ASGIApp

__all__ = ["DURATION_HEADER", "REQUEST_ID_HEADER", "TimingMiddleware", "new_request_id"]

REQUEST_ID_HEADER = "x-request-id"
DURATION_HEADER = "x-response-time-ms"
_SLOW_MS = 1000.0

_log = get_logger("service.access")

Handler = Callable[[Request], Awaitable[Response]]


def new_request_id() -> str:
    """A short opaque id for one request."""

    return uuid.uuid4().hex[:16]


class TimingMiddleware(BaseHTTPMiddleware):
    """Times every request and writes one log line per response."""

    def __init__(self, app: ASGIApp, *, slow_ms: float = _SLOW_MS) -> None:
        super().__init__(app)
        self.slow_ms = slow_ms

    async def dispatch(self, request: Request, call_next: Handler) -> Response:
        request_id = request.headers.get(REQUEST_ID_HEADER) or new_request_id()
        started = time.perf_counter()
        with bound(request_id=request_id):
            response = await call_next(request)
        elapsed_ms = (time.perf_counter() - started) * 1000.0
        response.headers[REQUEST_ID_HEADER] = request_id
        response.headers[DURATION_HEADER] = f"{elapsed_ms:.1f}"
        fields = {
            "request_id": request_id,
            "method": request.method,
            "path": request.url.path,
            "status": response.status_code,
            "duration_ms": round(elapsed_ms, 1),
        }
        if elapsed_ms >= self.slow_ms:
            _log.warning("slow request", extra=fields)
        else:
            _log.info("request", extra=fields)
        return response
