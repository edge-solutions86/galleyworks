"""``lexdrift config`` - show and check the settings a run would use.

Configuration reaches the engine from three places and the precedence is not
obvious from any one of them. This prints what the engine would actually run
with, which is the only reliable way to answer "why is it using a shingle width
of nine".

It also reports the derived numbers an operator cannot compute in their head:
the shortest passage the winnowing scheme guarantees to find, the similarity the
banding is tuned for, and the digest an index built with these settings will
carry.
"""

from __future__ import annotations

import json

import click

from lexdrift.canon.profiles import PROFILES
from lexdrift.cli.common import build_config, engine_options
from lexdrift.cli.main import EXIT_FINDINGS, EXIT_OK, Runtime, cli
from lexdrift.config import EngineConfig
from lexdrift.fingerprint.winnow import expected_density, guarantee_length
from lexdrift.report.writers import format_names

__all__ = ["config", "derived"]


def derived(settings: EngineConfig) -> dict[str, float | int | str]:
    """The numbers that follow from the settings rather than being set."""

    shingle = settings.shingle
    return {
        "guarantee_length_tokens": guarantee_length(shingle.width, shingle.winnow_window),
        "expected_density": round(expected_density(shingle.winnow_window), 4),
        "lsh_rows": settings.lsh.rows,
        "lsh_threshold": round(settings.lsh.approximate_threshold, 4),
        "engine_digest": settings.digest(),
    }


@cli.command(name="config", short_help="show and check the settings a run would use")
@engine_options()
@click.option("--json", "as_json", is_flag=True, help="emit the settings as json")
@click.option("--list-profiles", is_flag=True, help="list the canonicalization profiles")
@click.pass_obj
def config(
    runtime: Runtime,
    index_path: str | None,
    config_path: str | None,
    profile: str | None,
    as_json: bool,
    list_profiles: bool,
) -> int:
    """Resolve the configuration and print it."""

    if list_profiles:
        for name in sorted(PROFILES):
            marker = "*" if name == (profile or "default") else " "
            runtime.line(f"{marker} {name}")
        return EXIT_OK
    settings = build_config(config_path, profile)
    numbers = derived(settings)
    if as_json:
        runtime.line(json.dumps({"settings": settings.to_mapping(), "derived": numbers}, indent=2))
        return EXIT_OK
    runtime.line(f"profile: {settings.canon.profile}")
    runtime.line(
        f"shingle: {settings.shingle.mode.value} width {settings.shingle.width}, "
        f"winnow window {settings.shingle.winnow_window}"
    )
    runtime.line(
        f"lsh: {settings.lsh.permutations} permutations in {settings.lsh.bands} bands "
        f"of {numbers['lsh_rows']} rows"
    )
    runtime.line(
        f"match: containment >= {settings.match.min_containment}, "
        f"passages >= {settings.match.min_passage_chars} characters, "
        f"content >= {settings.match.min_content_ratio}"
    )
    runtime.line(
        f"guarantees a shared run of {numbers['guarantee_length_tokens']} tokens, "
        f"keeping about {numbers['expected_density']:.0%} of shingles"
    )
    runtime.line(f"banding is tuned for similarity near {numbers['lsh_threshold']}")
    runtime.line(f"engine digest {numbers['engine_digest']}")
    runtime.line(f"report formats: {', '.join(format_names())}")
    if settings.match.min_containment <= 0.1:
        runtime.line("warning: a containment floor this low will report common phrasing")
        return EXIT_FINDINGS
    return EXIT_OK
