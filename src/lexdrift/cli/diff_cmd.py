"""``lexdrift diff`` - what changed between two prints of one instrument.

No index is involved. The two documents are given directly, which is what a
drafting office has: the version that went in and the version that came out.

The default profile is ``verbatim``, because a comparison of two prints of the
same act should notice a renumbering and a capitalisation change. Pass
``--profile default`` to ignore typography and see only substantive edits.
"""

from __future__ import annotations

import click

from lexdrift.canon.profiles import profile as named_profile
from lexdrift.cli.main import EXIT_FINDINGS, EXIT_OK, Runtime, cli
from lexdrift.diff import ChangeKind, InstrumentDiff, compare
from lexdrift.ingest.base import SourceDocument
from lexdrift.ingest.registry import default_registry

__all__ = ["diff", "render_diff"]

_MARKS = {
    ChangeKind.UNCHANGED: " ",
    ChangeKind.CHANGED: "~",
    ChangeKind.ADDED: "+",
    ChangeKind.REMOVED: "-",
}


def render_diff(result: InstrumentDiff, *, show_unchanged: bool) -> list[str]:
    """One line per segment, plus a summary."""

    lines = [f"{result.left} -> {result.right}"]
    for change in result.changes:
        if not change.is_material and not show_unchanged:
            continue
        mark = _MARKS[change.kind]
        detail = (
            f"similarity {change.similarity:.2f}, "
            f"+{change.words_added} -{change.words_removed} words"
        )
        lines.append(f"{mark} {change.path:<16} {change.kind.value:<9} {detail}")
    counts = result.counts()
    lines.append(
        f"{counts['changed']} changed, {counts['added']} added, "
        f"{counts['removed']} removed, {counts['unchanged']} unchanged, "
        f"{result.total_churn} words of churn"
    )
    return lines


@cli.command(name="diff", short_help="compare two prints of one instrument")
@click.argument("left")
@click.argument("right")
@click.option("--jurisdiction", required=True, help="jurisdiction slug for both documents")
@click.option("--session", default=None, help="session, when the text does not say")
@click.option("--number", default=None, help="instrument number, when the text omits it")
@click.option(
    "--profile",
    default="verbatim",
    show_default=True,
    help="canonicalization profile to compare under",
)
@click.option("--show-unchanged", is_flag=True, help="list segments that did not change")
@click.pass_obj
def diff(
    runtime: Runtime,
    left: str,
    right: str,
    jurisdiction: str,
    session: str | None,
    number: str | None,
    profile: str,
    show_unchanged: bool,
) -> int:
    """Compare LEFT with RIGHT, segment by segment."""

    registry = default_registry()
    hints: dict[str, str] = {"jurisdiction": jurisdiction}
    if session is not None:
        hints["session"] = session
    if number is not None:
        hints["number"] = number
    before = registry.read(SourceDocument.from_path(left, **hints))
    after = registry.read(SourceDocument.from_path(right, **hints))
    result = compare(before, after, named_profile(profile))
    for line in render_diff(result, show_unchanged=show_unchanged):
        runtime.line(line)
    return EXIT_OK if result.is_identical else EXIT_FINDINGS
