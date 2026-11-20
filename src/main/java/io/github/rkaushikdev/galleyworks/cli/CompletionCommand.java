package io.github.rkaushikdev.galleyworks.cli;

import io.github.rkaushikdev.galleyworks.ConfigError;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * {@code lexdrift completion}: print a shell completion script, with a line saying what to do
 * with it.
 */
@Command(
        name = "completion",
        header = "print a shell completion script",
        customSynopsis = "lexdrift completion [OPTIONS] {bash|zsh|fish}",
        description = "Print the completion script for SHELL.")
public final class CompletionCommand extends Common.Base {

    @Parameters(
            paramLabel = "{bash|zsh|fish}",
            converter = Choices.Shells.class,
            completionCandidates = Choices.Shells.class)
    String shell;

    @Option(names = "--quiet", description = "print the script without the usage hint")
    boolean quiet;

    @Override
    public Integer call() {
        String script;
        try {
            script = Completion.scriptFor(shell);
        } catch (ConfigError exc) {
            line("no completion support for " + shell);
            return Main.EXIT_ERROR;
        }
        if (!quiet) {
            line("# " + Completion.installHint(shell));
        }
        line(script);
        return Main.EXIT_OK;
    }
}
