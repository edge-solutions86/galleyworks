"""Bearer token authentication, off unless a token is configured.

A corpus of unenacted drafts is confidential before it is introduced, so the
service needs a lock even though it only reads. It is deliberately the simplest
thing that works: one shared token, compared in constant time, supplied through
the environment.

Two decisions worth stating. Comparison uses :func:`secrets.compare_digest`,
because a naive ``==`` on a token leaks its prefix through timing. And
``/health`` is exempt: a load balancer has to be able to ask whether the process
is alive without holding a credential, and the answer carries no corpus content.
"""

from __future__ import annotations

import os
import secrets
from dataclasses import dataclass

from fastapi import Request, status
from fastapi.responses import JSONResponse

__all__ = ["TOKEN_ENV", "TokenPolicy", "authorize", "bearer_token", "policy_from_env"]

TOKEN_ENV = "LEXDRIFT_API_TOKEN"
_SCHEME = "bearer"
_EXEMPT = frozenset({"/health", "/openapi.json", "/docs", "/redoc"})


@dataclass(frozen=True, slots=True)
class TokenPolicy:
    """The token the service expects, if any."""

    token: str | None = None

    @property
    def is_enforced(self) -> bool:
        return bool(self.token)

    def accepts(self, presented: str | None) -> bool:
        if not self.is_enforced:
            return True
        if presented is None:
            return False
        return secrets.compare_digest(presented, self.token or "")


def policy_from_env(environ: dict[str, str] | None = None) -> TokenPolicy:
    """Read the expected token from the environment."""

    source = os.environ if environ is None else environ
    token = source.get(TOKEN_ENV, "").strip()
    return TokenPolicy(token=token or None)


def bearer_token(header: str | None) -> str | None:
    """Pull the token out of an ``Authorization`` header."""

    if not header:
        return None
    scheme, _, value = header.partition(" ")
    if scheme.lower() != _SCHEME or not value.strip():
        return None
    return value.strip()


def authorize(request: Request, policy: TokenPolicy) -> JSONResponse | None:
    """The response to send instead of handling ``request``, or ``None``.

    A refusal is returned rather than raised. Middleware runs outside the
    exception handlers, so an ``HTTPException`` raised here escapes as a 500
    instead of the 401 it describes.
    """

    if not policy.is_enforced or request.url.path in _EXEMPT:
        return None
    presented = bearer_token(request.headers.get("authorization"))
    if policy.accepts(presented):
        return None
    return JSONResponse(
        status_code=status.HTTP_401_UNAUTHORIZED,
        content={"code": "lexdrift.unauthorized", "message": "a bearer token is required"},
        headers={"WWW-Authenticate": "Bearer"},
    )
