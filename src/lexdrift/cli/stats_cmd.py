"""``lexdrift stats`` - describe what an index holds.

Read only and cheap enough to run before every query session. It answers "am I
pointed at the corpus I think I am", which is the question behind most reports
that turn out to be wrong.
"""

from __future__ import annotations

import json

import click

from lexdrift.cli.common import build_config, build_index, engine_options
from lexdrift.cli.main import EXIT_FINDINGS, EXIT_OK, Runtime, cli
from lexdrift.index.statistics import describe

__all__ = ["stats"]

WIDE_POSTING_SHARE = 0.5


@cli.command(name="stats", short_help="describe what an index holds")
@engine_options(index_required=True)
@click.option("--json", "as_json", is_flag=True, help="emit the numbers as json")
@click.pass_obj
def stats(
    runtime: Runtime,
    index_path: str | None,
    config_path: str | None,
    profile: str | None,
    reading: str | None,
    as_json: bool,
) -> int:
    """Describe the index named by ``--index``."""

    config = build_config(config_path, profile, reading)
    measured = describe(build_index(index_path, config))
    if as_json:
        runtime.line(
            json.dumps(
                {
                    "instruments": measured.instruments,
                    "segments": measured.segments,
                    "jurisdictions": measured.jurisdictions,
                    "sessions": measured.sessions,
                    "mean_tokens": round(measured.mean_tokens, 3),
                    "mean_fingerprints": round(measured.mean_fingerprints, 3),
                    "distinct_fingerprints": measured.distinct_fingerprints,
                    "total_fingerprints": measured.total_fingerprints,
                    "selectivity": round(measured.selectivity, 6),
                    "widest_posting": measured.widest_posting,
                    "engine_digest": measured.digest,
                },
                indent=2,
            )
        )
    else:
        for line in measured.lines():
            runtime.line(line)
    if measured.segments and measured.widest_posting_share >= WIDE_POSTING_SHARE:
        runtime.line(
            "warning: one fingerprint appears in half the corpus; "
            "consider a wider shingle or a stricter boilerplate filter"
        )
        return EXIT_FINDINGS
    return EXIT_OK
