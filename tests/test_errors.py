from __future__ import annotations

import pytest

from lexdrift import errors


def test_base_error_carries_message_and_code() -> None:
    exc = errors.LexdriftError("something went sideways")
    assert exc.message == "something went sideways"
    assert exc.code == "lexdrift.error"
    assert exc.to_dict() == {"code": "lexdrift.error", "message": "something went sideways"}


def test_context_is_rendered_in_str_sorted() -> None:
    exc = errors.ConfigError("bad profile", profile="wy-2024", stage="load")
    assert str(exc) == "bad profile (profile='wy-2024', stage='load')"


def test_context_is_copied_not_aliased() -> None:
    ctx = {"path": "a.txt"}
    exc = errors.IngestError("nope", **ctx)
    ctx["path"] = "b.txt"
    assert exc.context == {"path": "a.txt"}
    assert exc.to_dict()["context"] == {"path": "a.txt"}


def test_parse_error_is_an_ingest_error_and_locates_itself() -> None:
    exc = errors.ParseError("unbalanced subsection marker", line=42, column=7)
    assert isinstance(exc, errors.IngestError)
    assert exc.code == "lexdrift.ingest.parse"
    assert exc.located() == "line 42, column 7: unbalanced subsection marker"
    assert exc.to_dict()["context"]["line"] == 42


def test_parse_error_defaults_column_to_zero() -> None:
    exc = errors.ParseError("stray enacting clause", line=3)
    assert exc.column == 0
    assert exc.located().startswith("line 3, column 0:")


@pytest.mark.parametrize(
    ("cls", "code"),
    [
        (errors.ConfigError, "lexdrift.config"),
        (errors.IngestError, "lexdrift.ingest"),
        (errors.UnknownFormatError, "lexdrift.ingest.format"),
        (errors.CanonError, "lexdrift.canon"),
        (errors.StoreError, "lexdrift.store"),
        (errors.MatchError, "lexdrift.match"),
    ],
)
def test_codes_are_stable(cls: type[errors.LexdriftError], code: str) -> None:
    assert cls("x").code == code
    assert issubclass(cls, errors.LexdriftError)


def test_error_code_of_handles_foreign_exceptions() -> None:
    assert errors.error_code_of(ValueError("nope")) == "lexdrift.unexpected"
    assert errors.error_code_of(errors.StoreError("nope")) == "lexdrift.store"


@pytest.mark.parametrize(
    ("cls", "status"),
    [
        (errors.LexdriftError, 500),
        (errors.ConfigError, 400),
        (errors.IngestError, 422),
        (errors.ParseError, 422),
        (errors.UnknownFormatError, 415),
        (errors.CanonError, 500),
        (errors.StoreError, 503),
        (errors.MatchError, 500),
    ],
)
def test_each_error_names_its_http_status(cls: type[errors.LexdriftError], status: int) -> None:
    assert cls.http_status == status


def test_http_status_of_reads_the_instance() -> None:
    assert errors.http_status_of(errors.StoreError("x")) == 503
    assert errors.http_status_of(errors.ParseError("x", line=1)) == 422


def test_a_foreign_exception_is_a_server_error() -> None:
    assert errors.http_status_of(ValueError("x")) == 500


def test_a_subclass_inherits_the_status() -> None:
    class Narrower(errors.StoreError):
        code = "lexdrift.store.narrow"

    assert Narrower("x").http_status == 503
    assert errors.http_status_of(Narrower("x")) == 503
