package io.github.rkaushikdev.galleyworks.cli;

import io.github.rkaushikdev.galleyworks.Lexdrift;
import io.github.rkaushikdev.galleyworks.LexdriftError;
import io.github.rkaushikdev.galleyworks.util.Log;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ParseResult;

/**
 * The command line entry point.
 *
 * <p>Exit codes are part of the contract:
 *
 * <ul>
 *   <li>{@code 0} the command ran and found nothing worth flagging
 *   <li>{@code 1} the command ran and the result is a finding, for use in a CI gate
 *   <li>{@code 2} the arguments were wrong
 *   <li>{@code 3} a lexdrift error, with its stable code on stderr
 * </ul>
 *
 * <p>Output goes to a writer handed to {@link #run}, not to {@code System.out} directly, so a
 * caller can run the CLI in process and read what it produced.
 */
@Command(
        name = "lexdrift",
        customSynopsis = "lexdrift [OPTIONS] COMMAND [ARGS]...",
        description = "Passage-reuse detection for legislative instruments.",
        footer = Main.EPILOG,
        version = "lexdrift, version " + Lexdrift.VERSION,
        usageHelpWidth = 100,
        sortOptions = false,
        descriptionHeading = "%n",
        optionListHeading = "%nOptions:%n",
        commandListHeading = "%nCommands:%n",
        footerHeading = "%n",
        subcommands = {
            CheckCommand.class,
            CompletionCommand.class,
            ConfigCommand.class,
            DiffCommand.class,
            DiffusionCommand.class,
            ExplainCommand.class,
            IndexCommand.class,
            IngestCommand.class,
            ManifestCommand.class,
            QueryCommand.class,
            ReportCommand.class,
            ServeCommand.class,
            StatsCommand.class
        })
public final class Main implements Subcommand {

    /** The command ran and found nothing worth flagging. */
    public static final int EXIT_OK = 0;

    /** The command ran and the result is a finding. */
    public static final int EXIT_FINDINGS = 1;

    /** The arguments were wrong. */
    public static final int EXIT_USAGE = 2;

    /** A lexdrift error, reported with its stable code. */
    public static final int EXIT_ERROR = 3;

    /** The closing paragraph of the top-level help. */
    public static final String EPILOG =
            """
            A corpus is a directory of documents named <jurisdiction>_<session>_<number>.txt,
            or a manifest.yaml naming them explicitly.

            Exit codes: 0 nothing to flag, 1 a finding, 2 bad arguments, 3 a lexdrift error.
            """;

    private static final String HELP = "Show this message and exit.";

    @Option(names = "--version", versionHelp = true, description = "Show the version and exit.")
    boolean version;

    @Option(
            names = "--log-level",
            paramLabel = "[debug|info|warning|error]",
            defaultValue = "warning",
            converter = Choices.LogLevels.class,
            completionCandidates = Choices.LogLevels.class,
            showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
            description = "how much to write to stderr")
    String logLevel;

    @Option(
            names = "--log-format",
            paramLabel = "[plain|json]",
            defaultValue = "plain",
            converter = Choices.LogFormats.class,
            completionCandidates = Choices.LogFormats.class,
            showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
            description = "stderr log layout")
    String logFormat;

    /** Configure logging for the run. Subcommands do the work. */
    @Override
    public Integer call() {
        Log.configure(logLevel, logFormat.equals("json"), System.err);
        return EXIT_OK;
    }

    /**
     * The command tree, with every subcommand registered. Options may repeat with the last one
     * winning, and an argument starting with {@code @} is an ordinary argument.
     */
    public static CommandLine commandLine() {
        CommandLine root = new CommandLine(new Main());
        addHelp(root);
        root.setExpandAtFiles(false);
        root.setOverwrittenOptionsAllowed(true);
        return root;
    }

    /** The names of the subcommands, sorted. */
    public static List<String> commandNames() {
        return commandLine().getSubcommands().keySet().stream().sorted().toList();
    }

    /** Run the CLI in process with {@code System.err} for errors, returning the exit code. */
    public static int run(List<String> argv, Writer out) {
        return run(argv, out, new PrintWriter(new OutputStreamWriter(System.err, StandardCharsets.UTF_8)));
    }

    /** Run the CLI in process, returning the exit code. */
    public static int run(List<String> argv, Writer out, Writer err) {
        return run(argv, out, err, System.getenv());
    }

    /**
     * Run the CLI in process against {@code environ}, returning the exit code.
     *
     * <p>When {@code _LEXDRIFT_COMPLETE} is set, the arguments are ignored and the request is
     * answered for the shell completion script instead.
     */
    public static int run(List<String> argv, Writer out, Writer err, Map<String, String> environ) {
        PrintWriter output = out instanceof PrintWriter printer ? printer : new PrintWriter(out);
        PrintWriter errors = err instanceof PrintWriter printer ? printer : new PrintWriter(err);
        try {
            String instruction = environ.get(Completion.COMPLETE_VAR);
            if (instruction != null && !instruction.isEmpty()) {
                return Completion.respond(commandLine(), instruction, environ, output);
            }
            return dispatch(argv, output, errors);
        } finally {
            output.flush();
            errors.flush();
        }
    }

    /** The console entry point. */
    public static void main(String[] args) {
        System.exit(run(List.of(args), new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8))));
    }

    private static int dispatch(List<String> argv, PrintWriter out, PrintWriter err) {
        CommandLine root = commandLine();
        root.setOut(out);
        root.setErr(err);
        ParseResult parsed;
        try {
            parsed = root.parseArgs(argv.toArray(String[]::new));
        } catch (ParameterException exc) {
            err.println(exc.getMessage());
            return EXIT_USAGE;
        }
        for (ParseResult level = parsed; level != null; level = level.subcommand()) {
            if (level.isUsageHelpRequested()) {
                printHelp(level.commandSpec().commandLine(), out);
                return EXIT_OK;
            }
            if (level.isVersionHelpRequested()) {
                level.commandSpec().commandLine().printVersionHelp(out);
                return EXIT_OK;
            }
        }
        try {
            ((Main) root.getCommand()).call();
            if (!parsed.hasSubcommand()) {
                printHelp(root, out);
                return EXIT_USAGE;
            }
            ParseResult leaf = parsed.subcommand();
            while (leaf.hasSubcommand()) {
                leaf = leaf.subcommand();
            }
            return ((Subcommand) leaf.commandSpec().userObject()).call();
        } catch (LexdriftError exc) {
            err.println(exc.code() + ": " + exc.getMessage());
            return EXIT_ERROR;
        }
    }

    /** The help page of {@code command}, ending in exactly one newline. */
    private static void printHelp(CommandLine command, PrintWriter out) {
        String page = command.getUsageMessage();
        int end = page.length();
        while (end > 0 && page.charAt(end - 1) == '\n') {
            end--;
        }
        out.print(page.substring(0, end));
        out.print('\n');
    }

    /**
     * Give {@code command} and every subcommand a {@code -h, --help} option after their own, and
     * leave the one-line summary and the argument list out of a subcommand's own page: the summary
     * belongs in the parent's command list, and the synopsis already names the arguments.
     */
    private static void addHelp(CommandLine command) {
        CommandSpec spec = command.getCommandSpec();
        spec.addOption(OptionSpec.builder("-h", "--help")
                .usageHelp(true)
                .description(HELP)
                .build());
        command.getSubcommands().values().forEach(sub -> {
            List<String> sections = new ArrayList<>(sub.getHelpSectionKeys());
            sections.removeAll(List.of(
                    CommandLine.Model.UsageMessageSpec.SECTION_KEY_HEADER_HEADING,
                    CommandLine.Model.UsageMessageSpec.SECTION_KEY_HEADER,
                    CommandLine.Model.UsageMessageSpec.SECTION_KEY_PARAMETER_LIST_HEADING,
                    CommandLine.Model.UsageMessageSpec.SECTION_KEY_PARAMETER_LIST));
            sub.setHelpSectionKeys(sections);
            addHelp(sub);
        });
    }
}
