package io.github.rkaushikdev.galleyworks.cli;

import io.github.rkaushikdev.galleyworks.ConfigError;
import io.github.rkaushikdev.galleyworks.Context;
import io.github.rkaushikdev.galleyworks.util.ShellWords;
import io.github.rkaushikdev.galleyworks.util.Text;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import picocli.CommandLine;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;

/**
 * Shell completion for bash, zsh and fish.
 *
 * <p>A script is printed once and sourced by the shell. On every tab press it runs {@code lexdrift}
 * again with {@code _LEXDRIFT_COMPLETE} set to {@code <shell>_complete} and the words typed so far
 * in {@code COMP_WORDS} and {@code COMP_CWORD}; the program answers with one candidate per line and
 * exits. {@code <shell>_source} prints the script itself.
 *
 * <p>Nothing is written to the user's shell configuration. A tool that edits a dotfile on your
 * behalf is a tool you have to audit before running.
 */
public final class Completion {

    /** The shells a script exists for. */
    public static final List<String> SHELLS = List.of("bash", "zsh", "fish");

    /** The environment variable the scripts set when they ask for candidates. */
    public static final String COMPLETE_VAR = "_LEXDRIFT_COMPLETE";

    private static final String PROG_NAME = "lexdrift";
    private static final String COMPLETE_FUNC = "_lexdrift_completion";

    private static final Map<String, String> HINTS = Map.of(
            "bash", "add to ~/.bashrc:  eval \"$(lexdrift completion bash)\"",
            "zsh", "add to ~/.zshrc:  eval \"$(lexdrift completion zsh)\"",
            "fish", "write to ~/.config/fish/completions/lexdrift.fish");

    private static final String BASH =
            """
            %(complete_func)s() {
                local IFS=$'\\n'
                local response

                response=$(env COMP_WORDS="${COMP_WORDS[*]}" COMP_CWORD=$COMP_CWORD \
            %(complete_var)s=bash_complete $1)

                for completion in $response; do
                    IFS=',' read type value <<< "$completion"

                    if [[ $type == 'dir' ]]; then
                        COMPREPLY=()
                        compopt -o dirnames
                    elif [[ $type == 'file' ]]; then
                        COMPREPLY=()
                        compopt -o default
                    elif [[ $type == 'plain' ]]; then
                        COMPREPLY+=($value)
                    fi
                done

                return 0
            }

            %(complete_func)s_setup() {
                complete -o nosort -F %(complete_func)s %(prog_name)s
            }

            %(complete_func)s_setup;
            """;

    private static final String ZSH =
            """
            #compdef %(prog_name)s

            %(complete_func)s() {
                local -a completions
                local -a completions_with_descriptions
                local -a response
                (( ! $+commands[%(prog_name)s] )) && return 1

                response=("${(@f)$(env COMP_WORDS="${words[*]}" COMP_CWORD=$((CURRENT-1)) \
            %(complete_var)s=zsh_complete %(prog_name)s)}")

                for type key descr in ${response}; do
                    if [[ "$type" == "plain" ]]; then
                        if [[ "$descr" == "_" ]]; then
                            completions+=("$key")
                        else
                            completions_with_descriptions+=("$key":"$descr")
                        fi
                    elif [[ "$type" == "dir" ]]; then
                        _path_files -/
                    elif [[ "$type" == "file" ]]; then
                        _path_files -f
                    fi
                done

                if [ -n "$completions_with_descriptions" ]; then
                    _describe -V unsorted completions_with_descriptions -U
                fi

                if [ -n "$completions" ]; then
                    compadd -U -V unsorted -a completions
                fi
            }

            if [[ $zsh_eval_context[-1] == loadautofunc ]]; then
                # autoload from fpath, call function directly
                %(complete_func)s "$@"
            else
                # eval/source/. command, register function for later
                compdef %(complete_func)s %(prog_name)s
            fi
            """;

    private static final String FISH =
            """
            function %(complete_func)s;
                set -l response (env %(complete_var)s=fish_complete COMP_WORDS=(commandline -cp) \
            COMP_CWORD=(commandline -t) %(prog_name)s);

                for completion in $response;
                    set -l metadata (string split "," $completion);

                    if test $metadata[1] = "dir";
                        __fish_complete_directories $metadata[2];
                    else if test $metadata[1] = "file";
                        __fish_complete_path $metadata[2];
                    else if test $metadata[1] = "plain";
                        echo $metadata[2];
                    end;
                end;
            end;

            complete --no-files --command %(prog_name)s --arguments \
            "(%(complete_func)s)";
            """;

    private Completion() {}

    /**
     * One thing the shell may offer.
     *
     * @param type {@code plain} for a literal value, {@code file} or {@code dir} to let the shell
     *     complete a path
     * @param value the value, or the partial path for a path completion
     * @param help a description, or empty
     */
    public record Item(String type, String value, String help) {

        /** A literal value with a description. */
        static Item plain(String value, String help) {
            return new Item("plain", value, help);
        }
    }

    /** One line telling the user what to do with the script for {@code shell}. */
    public static String installHint(String shell) {
        String hint = HINTS.get(shell);
        if (hint == null) {
            throw unsupported(shell);
        }
        return hint;
    }

    /** The completion script for {@code shell}. */
    public static String scriptFor(String shell) {
        String template =
                switch (shell) {
                    case "bash" -> BASH;
                    case "zsh" -> ZSH;
                    case "fish" -> FISH;
                    default -> throw unsupported(shell);
                };
        return template.replace("%(complete_func)s", COMPLETE_FUNC)
                .replace("%(complete_var)s", COMPLETE_VAR)
                .replace("%(prog_name)s", PROG_NAME);
    }

    /**
     * Answer a request from a completion script.
     *
     * @param root the command tree
     * @param instruction the value of {@code _LEXDRIFT_COMPLETE}, such as {@code bash_complete}
     * @param environ where {@code COMP_WORDS} and {@code COMP_CWORD} are read from
     * @param out where the answer is written
     * @return {@code 0} when the instruction was understood, {@code 1} otherwise
     */
    public static int respond(CommandLine root, String instruction, Map<String, String> environ, PrintWriter out) {
        int split = instruction.indexOf('_');
        String shell = split < 0 ? instruction : instruction.substring(0, split);
        String action = split < 0 ? "" : instruction.substring(split + 1);
        if (!SHELLS.contains(shell)) {
            return 1;
        }
        if (action.equals("source")) {
            out.print(scriptFor(shell));
            out.print('\n');
            return 0;
        }
        if (!action.equals("complete")) {
            return 1;
        }
        List<String> words = ShellWords.split(environ.getOrDefault("COMP_WORDS", ""));
        List<String> args;
        String incomplete;
        if (shell.equals("fish")) {
            incomplete = environ.getOrDefault("COMP_CWORD", "");
            args = new ArrayList<>(words.subList(Math.min(1, words.size()), words.size()));
            if (!incomplete.isEmpty() && !args.isEmpty() && args.getLast().equals(incomplete)) {
                args.removeLast();
            }
        } else {
            int cword = Integer.parseInt(Text.strip(environ.getOrDefault("COMP_CWORD", "0")));
            args = slice(words, 1, cword);
            int at = cword < 0 ? words.size() + cword : cword;
            incomplete = at >= 0 && at < words.size() ? words.get(at) : "";
        }
        Function<Item, String> format =
                switch (shell) {
                    case "zsh" ->
                        item -> item.type() + "\n" + item.value() + "\n"
                                + (item.help().isEmpty() ? "_" : item.help());
                    case "fish" ->
                        item -> item.type() + "," + item.value() + (item.help().isEmpty() ? "" : "\t" + item.help());
                    default -> item -> item.type() + "," + item.value();
                };
        List<String> lines =
                complete(root, args, incomplete).stream().map(format).toList();
        out.print(String.join("\n", lines));
        out.print('\n');
        return 0;
    }

    /** The candidates for {@code incomplete}, after the complete words {@code args}. */
    public static List<Item> complete(CommandLine root, List<String> args, String incomplete) {
        List<String> complete = new ArrayList<>(args);
        Resolved context = resolve(root, complete);
        String partial = incomplete;
        if (partial.equals("=")) {
            partial = "";
        } else if (partial.contains("=") && startsOption(partial)) {
            int equals = partial.indexOf('=');
            complete.add(partial.substring(0, equals));
            partial = partial.substring(equals + 1);
        }
        if (!complete.contains("--") && startsOption(partial)) {
            return commandItems(context, partial);
        }
        CommandSpec spec = context.command().getCommandSpec();
        for (OptionSpec option : spec.options()) {
            if (awaitsValue(complete, option)) {
                return valueItems(option, partial);
            }
        }
        List<PositionalParamSpec> positionals = spec.positionalParameters();
        if (context.positionals() < positionals.size()) {
            return valueItems(positionals.get(context.positionals()), partial);
        }
        return commandItems(context, partial);
    }

    /**
     * Where the complete words leave the command line.
     *
     * @param command the innermost command named so far
     * @param given the options of that command already on the line
     * @param positionals how many of its positional arguments are already on the line
     */
    private record Resolved(CommandLine command, Set<OptionSpec> given, int positionals) {}

    private static Resolved resolve(CommandLine root, List<String> args) {
        CommandLine command = root;
        Set<OptionSpec> given = new HashSet<>();
        int positionals = 0;
        boolean literal = false;
        int i = 0;
        while (i < args.size()) {
            String arg = args.get(i);
            i++;
            if (!literal && arg.equals("--")) {
                literal = true;
                continue;
            }
            if (!literal && arg.length() > 1 && arg.startsWith("-")) {
                int equals = arg.indexOf('=');
                OptionSpec option = command.getCommandSpec().findOption(equals < 0 ? arg : arg.substring(0, equals));
                if (option != null) {
                    given.add(option);
                    if (takesValue(option) && equals < 0) {
                        i++;
                    }
                }
                continue;
            }
            Map<String, CommandLine> subcommands = command.getSubcommands();
            if (!subcommands.isEmpty()) {
                CommandLine next = subcommands.get(arg);
                if (next == null) {
                    return new Resolved(command, given, positionals);
                }
                command = next;
                given = new HashSet<>();
                positionals = 0;
                literal = false;
                continue;
            }
            positionals++;
        }
        return new Resolved(command, given, positionals);
    }

    /** Subcommand names for a group, and option names when the word starts like one. */
    private static List<Item> commandItems(Resolved context, String incomplete) {
        List<Item> items = new ArrayList<>();
        new TreeMap<>(context.command().getSubcommands()).forEach((name, sub) -> {
            if (name.startsWith(incomplete)) {
                items.add(Item.plain(
                        name,
                        String.join(" ", sub.getCommandSpec().usageMessage().header())));
            }
        });
        if (!incomplete.isEmpty() && !Text.isAlnum(incomplete.codePointAt(0))) {
            for (OptionSpec option : context.command().getCommandSpec().options()) {
                if (option.hidden() || context.given().contains(option)) {
                    continue;
                }
                for (String name : option.names()) {
                    if (name.startsWith(incomplete)) {
                        items.add(Item.plain(name, String.join(" ", option.description())));
                    }
                }
            }
        }
        return items;
    }

    /** The values an option or argument accepts that start with {@code incomplete}. */
    private static List<Item> valueItems(ArgSpec param, String incomplete) {
        Iterable<String> candidates = param.completionCandidates();
        if (candidates != null) {
            List<Item> items = new ArrayList<>();
            for (String candidate : candidates) {
                if (candidate.startsWith(incomplete)) {
                    items.add(new Item("plain", candidate, ""));
                }
            }
            return items;
        }
        return switch (param.paramLabel()) {
            case "FILE" -> List.of(new Item("file", incomplete, ""));
            case "DIRECTORY" -> List.of(new Item("dir", incomplete, ""));
            default -> List.of();
        };
    }

    /** True when the last complete word is this option's name and the option takes a value. */
    private static boolean awaitsValue(List<String> args, OptionSpec option) {
        if (!takesValue(option) || args.isEmpty()) {
            return false;
        }
        String last = args.getLast();
        return startsOption(last) && List.of(option.names()).contains(last);
    }

    private static boolean takesValue(OptionSpec option) {
        return option.arity().max() > 0 && !option.usageHelp() && !option.versionHelp();
    }

    /** {@code words[start:stop]}, with negative bounds counting from the end and both clamped. */
    private static List<String> slice(List<String> words, int start, int stop) {
        int size = words.size();
        int from = Math.clamp(start < 0 ? size + start : start, 0, size);
        int to = Math.clamp(stop < 0 ? size + stop : stop, 0, size);
        return new ArrayList<>(to > from ? words.subList(from, to) : List.of());
    }

    private static boolean startsOption(String word) {
        return !word.isEmpty() && word.charAt(0) == '-';
    }

    private static ConfigError unsupported(String shell) {
        return new ConfigError("unsupported shell", Context.of("shell", shell, "known", String.join(",", SHELLS)));
    }
}
