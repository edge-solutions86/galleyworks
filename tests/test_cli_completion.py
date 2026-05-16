from __future__ import annotations

import io

import pytest

from lexdrift.cli import build_cli
from lexdrift.cli.completion_cmd import SHELLS, install_hint, script_for
from lexdrift.cli.main import EXIT_OK, EXIT_USAGE, main
from lexdrift.errors import ConfigError


@pytest.fixture(autouse=True)
def _loaded() -> None:
    build_cli()


def run(*argv: str) -> tuple[int, str]:
    stream = io.StringIO()
    code = main(list(argv), stream)
    return code, stream.getvalue()


@pytest.mark.parametrize("shell", SHELLS)
def test_every_shell_has_a_script(shell: str) -> None:
    script = script_for(shell)
    assert script.strip()
    assert "lexdrift" in script


@pytest.mark.parametrize("shell", SHELLS)
def test_every_shell_has_a_hint(shell: str) -> None:
    assert "lexdrift completion" in install_hint(shell) or "completions" in install_hint(shell)


def test_an_unknown_shell_is_rejected() -> None:
    with pytest.raises(ConfigError, match="unsupported shell"):
        script_for("csh")
    with pytest.raises(ConfigError, match="unsupported shell"):
        install_hint("csh")


def test_the_command_prints_a_script() -> None:
    code, output = run("completion", "bash")
    assert code == EXIT_OK
    assert output.startswith("# add to ~/.bashrc")
    assert "_LEXDRIFT_COMPLETE" in output


def test_quiet_omits_the_hint() -> None:
    _, quiet = run("completion", "--quiet", "zsh")
    _, loud = run("completion", "zsh")
    assert "add to ~/.zshrc" not in quiet
    assert "add to ~/.zshrc" in loud
    assert quiet.strip()


def test_the_variable_name_matches_the_program() -> None:
    assert "_LEXDRIFT_COMPLETE" in script_for("fish")


def test_an_unsupported_shell_is_a_usage_error() -> None:
    assert run("completion", "csh")[0] == EXIT_USAGE


def test_the_shell_argument_is_required() -> None:
    assert run("completion")[0] == EXIT_USAGE
