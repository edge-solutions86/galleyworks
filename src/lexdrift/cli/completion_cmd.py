"""``lexdrift completion`` - print a shell completion script.

click can generate one, but only through an environment variable whose name
encodes the program, which is not something anybody remembers. This wraps it in
a command that says what to do with the output.

Nothing is written to the user's shell configuration. A tool that edits a
dotfile on your behalf is a tool you have to audit before running.
"""

from __future__ import annotations

import click
from click.shell_completion import get_completion_class

from lexdrift.cli.main import EXIT_ERROR, EXIT_OK, Runtime, cli
from lexdrift.errors import ConfigError

__all__ = ["SHELLS", "completion", "install_hint", "script_for"]

SHELLS = ("bash", "zsh", "fish")
_COMPLETE_VAR = "_LEXDRIFT_COMPLETE"

_HINTS = {
    "bash": 'add to ~/.bashrc:  eval "$(lexdrift completion bash)"',
    "zsh": 'add to ~/.zshrc:  eval "$(lexdrift completion zsh)"',
    "fish": "write to ~/.config/fish/completions/lexdrift.fish",
}


def install_hint(shell: str) -> str:
    """One line telling the user what to do with the script."""

    try:
        return _HINTS[shell]
    except KeyError as exc:
        raise ConfigError("unsupported shell", shell=shell, known=",".join(SHELLS)) from exc


def script_for(shell: str) -> str:
    """The completion script for ``shell``."""

    cls = get_completion_class(shell)
    if cls is None:
        raise ConfigError("unsupported shell", shell=shell, known=",".join(SHELLS))
    return cls(cli, {}, "lexdrift", _COMPLETE_VAR).source()


@cli.command(name="completion", short_help="print a shell completion script")
@click.argument("shell", type=click.Choice(list(SHELLS)))
@click.option("--quiet", is_flag=True, help="print the script without the usage hint")
@click.pass_obj
def completion(runtime: Runtime, shell: str, quiet: bool) -> int:
    """Print the completion script for SHELL."""

    try:
        script = script_for(shell)
    except ConfigError:
        runtime.line(f"no completion support for {shell}")
        return EXIT_ERROR
    if not quiet:
        runtime.line(f"# {install_hint(shell)}")
    runtime.line(script)
    return EXIT_OK
