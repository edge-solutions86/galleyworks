"""House rules, checked rather than remembered.

None of these is clever. They are the conventions that a reviewer would
otherwise have to hold in their head across ninety source files, and each one
has been broken at least once during this codebase's life.
"""

from __future__ import annotations

import ast
import importlib
import pkgutil
from pathlib import Path

import pytest

import lexdrift

PACKAGE = Path(lexdrift.__file__).resolve().parent
ROOT = PACKAGE.parent.parent
TESTS = ROOT / "tests"

SOURCES = sorted(PACKAGE.rglob("*.py"))
TEST_FILES = sorted(TESTS.rglob("*.py"))
ALL_FILES = SOURCES + TEST_FILES

# The markers are assembled rather than written out, because this file is
# itself scanned and a literal here would be a false positive about itself.
MARKERS = tuple(
    "".join(parts) for parts in (("TO", "DO"), ("FIX", "ME"), ("X", "XX"), ("HA", "CK"))
)
DEBUGGERS = ("break" + "point()", "import " + "pdb")

# __main__ is a shim and the package inits are one line each.
NO_ALL_NEEDED = {"__main__.py", "__init__.py"}


def parsed(path: Path) -> ast.Module:
    return ast.parse(path.read_text(encoding="utf-8"), filename=str(path))


def test_there_are_sources_to_check() -> None:
    assert len(SOURCES) > 40
    assert len(TEST_FILES) > 40


@pytest.mark.parametrize("path", ALL_FILES, ids=lambda p: p.name)
def test_every_file_uses_unix_line_endings(path: Path) -> None:
    assert b"\r\n" not in path.read_bytes()


@pytest.mark.parametrize("path", ALL_FILES, ids=lambda p: p.name)
def test_every_file_ends_with_one_newline(path: Path) -> None:
    data = path.read_bytes()
    assert data.endswith(b"\n")
    assert not data.endswith(b"\n\n")


@pytest.mark.parametrize("path", SOURCES, ids=lambda p: p.name)
def test_every_module_has_a_docstring(path: Path) -> None:
    assert ast.get_docstring(parsed(path)), path.name


@pytest.mark.parametrize(
    "path", [p for p in SOURCES if p.name not in NO_ALL_NEEDED], ids=lambda p: p.name
)
def test_every_module_declares_its_exports(path: Path) -> None:
    names = {
        target.id
        for node in parsed(path).body
        if isinstance(node, ast.Assign)
        for target in node.targets
        if isinstance(target, ast.Name)
    }
    assert "__all__" in names, path.name


@pytest.mark.parametrize(
    "path", [p for p in SOURCES if p.name not in NO_ALL_NEEDED], ids=lambda p: p.name
)
def test_every_export_list_is_sorted(path: Path) -> None:
    for node in parsed(path).body:
        if not isinstance(node, ast.Assign):
            continue
        if not any(isinstance(t, ast.Name) and t.id == "__all__" for t in node.targets):
            continue
        assert isinstance(node.value, ast.List)
        names = [item.value for item in node.value.elts if isinstance(item, ast.Constant)]
        assert names == sorted(names), path.name


@pytest.mark.parametrize(
    "path", [p for p in SOURCES if p.name not in NO_ALL_NEEDED], ids=lambda p: p.name
)
def test_every_exported_name_is_defined(path: Path) -> None:
    module = importlib.import_module(
        "lexdrift."
        + str(path.relative_to(PACKAGE).with_suffix("")).replace("\\", ".").replace("/", ".")
    )
    missing = [name for name in getattr(module, "__all__", ()) if not hasattr(module, name)]
    assert missing == [], path.name


@pytest.mark.parametrize("path", ALL_FILES, ids=lambda p: p.name)
def test_no_unfinished_work_markers(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    for marker in MARKERS:
        assert marker not in text, f"{path.name} still carries {marker}"


@pytest.mark.parametrize("path", ALL_FILES, ids=lambda p: p.name)
def test_no_stray_debugging(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    assert DEBUGGERS[0] not in text
    assert DEBUGGERS[1] not in text


@pytest.mark.parametrize("path", ALL_FILES, ids=lambda p: p.name)
def test_every_file_uses_postponed_annotations(path: Path) -> None:
    tree = parsed(path)
    futures = {
        alias.name
        for node in tree.body
        if isinstance(node, ast.ImportFrom) and node.module == "__future__"
        for alias in node.names
    }
    if not tree.body or (len(tree.body) == 1 and isinstance(tree.body[0], ast.Expr)):
        return
    assert "annotations" in futures, path.name


def test_every_module_is_importable() -> None:
    for info in pkgutil.walk_packages([str(PACKAGE)], prefix="lexdrift."):
        importlib.import_module(info.name)


def test_the_test_suite_covers_every_source_package() -> None:
    packages = {path.parent.name for path in SOURCES if path.name != "__init__.py"}
    covered = " ".join(path.read_text(encoding="utf-8") for path in TEST_FILES)
    for name in packages - {"lexdrift"}:
        assert f"lexdrift.{name}" in covered, name
