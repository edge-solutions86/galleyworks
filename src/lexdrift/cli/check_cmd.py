"""``lexdrift check`` - verify and repair an index.

Two things are checked. The derived band and posting tables must agree with the
stored records, and the engine configuration must match the one the index was
built with. The second is checked by opening the index at all, so this command
mostly exists for the first.

``--repair`` rebuilds the derived tables and vacuums. It never touches the
stored records, so it cannot lose anything that was indexed.
"""

from __future__ import annotations

import click

from lexdrift.cli.common import build_config, engine_options
from lexdrift.cli.main import EXIT_FINDINGS, EXIT_OK, Runtime, cli
from lexdrift.errors import ConfigError
from lexdrift.index.maintenance import check_integrity, compact
from lexdrift.index.store import SqliteIndex

__all__ = ["check"]


@cli.command(name="check", short_help="verify and optionally repair an index")
@engine_options(index_required=True)
@click.option("--repair", is_flag=True, help="rebuild the derived tables and vacuum")
@click.pass_obj
def check(
    runtime: Runtime,
    index_path: str | None,
    config_path: str | None,
    profile: str | None,
    repair: bool,
) -> int:
    """Check the index named by ``--index``."""

    if index_path is None:  # pragma: no cover - click enforces this
        raise ConfigError("an index path is required")
    config = build_config(config_path, profile)
    with SqliteIndex(index_path, config) as store:
        report = check_integrity(store)
        runtime.line(report.summary())
        if report.is_clean and not repair:
            return EXIT_OK
        if not repair:
            runtime.line("run again with --repair to rebuild the derived tables")
            return EXIT_FINDINGS
        outcome = compact(store)
        runtime.line(
            f"rebuilt {outcome.rebuilt_segments} segments, "
            f"reclaimed {outcome.bytes_reclaimed} bytes"
        )
        after = check_integrity(store)
        runtime.line(after.summary())
        return EXIT_OK if after.is_clean else EXIT_FINDINGS
