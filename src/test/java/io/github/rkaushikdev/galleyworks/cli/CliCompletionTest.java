package io.github.rkaushikdev.galleyworks.cli;

import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_OK;
import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_USAGE;
import static io.github.rkaushikdev.galleyworks.support.Cli.run;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.galleyworks.ConfigError;
import io.github.rkaushikdev.galleyworks.support.Cli;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.FieldSource;

class CliCompletionTest {

    static final List<String> SHELLS = Completion.SHELLS;

    @ParameterizedTest
    @FieldSource("SHELLS")
    void everyShellHasAScript(String shell) {
        String script = Completion.scriptFor(shell);
        assertThat(script.strip()).isNotEmpty();
        assertThat(script).contains("lexdrift");
    }

    @ParameterizedTest
    @FieldSource("SHELLS")
    void everyShellHasAHint(String shell) {
        assertThat(Completion.installHint(shell)).containsAnyOf("lexdrift completion", "completions");
    }

    @Test
    void anUnknownShellIsRejected() {
        assertThatThrownBy(() -> Completion.scriptFor("csh"))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("unsupported shell");
        assertThatThrownBy(() -> Completion.installHint("csh"))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("unsupported shell");
    }

    @Test
    void theCommandPrintsAScript() {
        Cli.Result result = run("completion", "bash");
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(result.output()).startsWith("# add to ~/.bashrc").contains("_LEXDRIFT_COMPLETE");
    }

    @Test
    void quietOmitsTheHint() {
        String quiet = run("completion", "--quiet", "zsh").output();
        String loud = run("completion", "zsh").output();
        assertThat(quiet).doesNotContain("add to ~/.zshrc");
        assertThat(loud).contains("add to ~/.zshrc");
        assertThat(quiet.strip()).isNotEmpty();
    }

    @Test
    void theVariableNameMatchesTheProgram() {
        assertThat(Completion.scriptFor("fish")).contains("_LEXDRIFT_COMPLETE");
    }

    @Test
    void anUnsupportedShellIsAUsageError() {
        assertThat(run("completion", "csh").code()).isEqualTo(EXIT_USAGE);
    }

    @Test
    void theShellArgumentIsRequired() {
        assertThat(run("completion").code()).isEqualTo(EXIT_USAGE);
    }

    // The script calls the program back with the words typed so far. These pin the answers it
    // gives.

    private static String complete(String shell, String words, String cword) {
        Cli.Result result =
                run(Map.of(Completion.COMPLETE_VAR, shell + "_complete", "COMP_WORDS", words, "COMP_CWORD", cword));
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(result.output()).endsWith("\n");
        return result.output().substring(0, result.output().length() - 1);
    }

    @Test
    void theSubcommandsAreOffered() {
        assertThat(complete("bash", "lexdrift ", "1").split("\n"))
                .containsExactly(
                        "plain,check",
                        "plain,completion",
                        "plain,config",
                        "plain,diff",
                        "plain,diffusion",
                        "plain,explain",
                        "plain,index",
                        "plain,ingest",
                        "plain,manifest",
                        "plain,query",
                        "plain,report",
                        "plain,serve",
                        "plain,stats");
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            textBlock =
                    """
            lexdrift ch                              | 1 | plain,check
            lexdrift -                               | 1 | plain,--version\\nplain,--log-level\\nplain,--log-format\\nplain,-h\\nplain,--help
            lexdrift --log-level w                   | 2 | plain,warning
            lexdrift query --index x --c             | 4 | plain,--config\\nplain,--crossing-only
            lexdrift query --output=j                | 2 | plain,json
            lexdrift report --out                    | 3 | dir,
            lexdrift index --index                   | 3 | file,
            lexdrift completion b                    | 2 | plain,bash
            lexdrift diff a                          | 3 | ''
            lexdrift query -- --                     | 3 | ''
            lexdrift nonesuch q                      | 2 | plain,query
            'lexdrift "qu'                           | 1 | plain,query
            'lexdrift ''q''u'                        | 1 | plain,query
            lexdrift qu\\                             | 1 | plain,query
            'lexdrift "q\\u"'                         | 1 | ''
            'lexdrift "c\\"x"'                        | 1 | ''
            """)
    void bashIsAnsweredWithTypeAndValue(String words, String cword, String expected) {
        assertThat(complete("bash", padded(words, cword), cword)).isEqualTo(expected.replace("\\n", "\n"));
    }

    @Test
    void zshCarriesTheHelpText() {
        assertThat(complete("zsh", "lexdrift st", "1")).isEqualTo("plain\nstats\ndescribe what an index holds");
        assertThat(complete("zsh", "lexdrift --log-level d", "2")).isEqualTo("plain\ndebug\n_");
    }

    @Test
    void fishReadsThePartialWordFromItsOwnVariable() {
        assertThat(complete("fish", "lexdrift query --ou", "--ou"))
                .isEqualTo("plain,--output\thow to print the report");
        assertThat(complete("fish", "lexdrift report --out r", "r")).isEqualTo("dir,r");
    }

    @Test
    void theScriptCanBePrintedThroughTheVariable() {
        Cli.Result result = run(Map.of(Completion.COMPLETE_VAR, "bash_source"));
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(result.output()).isEqualTo(Completion.scriptFor("bash") + "\n");
    }

    @Test
    void anUnknownInstructionIsRefused() {
        assertThat(run(Map.of(Completion.COMPLETE_VAR, "csh_complete")).code()).isEqualTo(1);
        assertThat(run(Map.of(Completion.COMPLETE_VAR, "bash_explode")).code()).isEqualTo(1);
    }

    /** A trailing space in the table is trimmed away; put it back where the cursor is past the last word. */
    private static String padded(String words, String cword) {
        int typed = words.isEmpty() ? 0 : words.split(" ", -1).length;
        return Integer.parseInt(cword) >= typed ? words + " " : words;
    }
}
