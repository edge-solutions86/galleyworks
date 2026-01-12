"""What the routes share, and how they get at it.

The dependency has to be module level. Postponed annotations turn every
parameter type into a string and FastAPI resolves those against module globals,
so an alias defined inside the application factory is invisible by the time a
route is built. That failure shows up as a startup error about an undefined
class rather than as anything about scope, which is worth the paragraph.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Annotated

from fastapi import Depends, Request

from lexdrift.pipeline import Engine

__all__ = ["Service", "ServiceState", "get_state"]


@dataclass(slots=True)
class ServiceState:
    """One engine, shared by every request."""

    engine: Engine

    @property
    def digest(self) -> str:
        return self.engine.config.digest()


def get_state(request: Request) -> ServiceState:
    """The shared state, read off the application rather than a closure."""

    state: ServiceState = request.app.state.service
    return state


Service = Annotated[ServiceState, Depends(get_state)]
