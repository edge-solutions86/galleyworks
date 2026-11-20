package io.github.rkaushikdev.galleyworks;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.cli.Main;
import io.github.rkaushikdev.galleyworks.report.ReportWriters;
import io.github.rkaushikdev.galleyworks.support.Cli;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The README is documentation and documentation drifts. Every command it lists has to exist, and
 * every command that exists has to be listed. Nothing here checks prose; it checks the lists that
 * go stale.
 */
class ReadmeTest {

    private static final String README = Cli.read(Path.of("README.md"));
    private static final Pattern COMMAND = Pattern.compile("^lexdrift (\\w[\\w-]*)", Pattern.MULTILINE);

    /** The commands {@code text} shows at the start of a line. */
    static Set<String> documentedCommands(String text) {
        Set<String> found = new TreeSet<>();
        Matcher match = COMMAND.matcher(text);
        while (match.find()) {
            found.add(match.group(1));
        }
        return found;
    }

    @Test
    void everyDocumentedCommandExists() {
        assertThat(Main.commandNames()).containsAll(documentedCommands(README));
    }

    @Test
    void everyCommandIsDocumented() {
        assertThat(documentedCommands(README)).containsAll(Main.commandNames());
    }

    @Test
    void theCommandReaderSeesAPlantedList() {
        assertThat(documentedCommands("lexdrift alpha x\n  lexdrift indented\nsee lexdrift beta\nlexdrift gamma-ray\n"))
                .containsExactly("alpha", "gamma-ray");
        assertThat(documentedCommands("")).isEmpty();
    }

    @Test
    void theExitCodesAreDocumented() {
        for (String code : new String[] {"0", "1", "2", "3"}) {
            assertThat(README.contains("`" + code + "`") || README.contains(code + " "))
                    .as(code)
                    .isTrue();
        }
    }

    @Test
    void theExitCodesAreInTheHelpEpilog() {
        assertThat(Main.EPILOG).contains("Exit codes");
        assertThat(Cli.run().output()).contains("Exit codes");
    }

    @Test
    void theConfigurationKeysInTheReadmeAreReal() {
        Map<String, Object> defaults = new EngineConfig().toMapping();
        for (String section : new String[] {"shingle", "lsh", "canon", "match", "report"}) {
            assertThat(README).contains(section + ":");
            assertThat(defaults).containsKey(section);
        }
    }

    @Test
    void theDocumentedSettingsExist() {
        Map<String, Object> mapping = new EngineConfig().toMapping();
        assertThat(((Map<?, ?>) mapping.get("shingle")).containsKey("winnow_window"))
                .isTrue();
        assertThat(((Map<?, ?>) mapping.get("lsh")).containsKey("permutations")).isTrue();
        assertThat(((Map<?, ?>) mapping.get("match")).containsKey("same_jurisdiction_containment"))
                .isTrue();
        assertThat(((Map<?, ?>) mapping.get("report")).containsKey("min_confidence"))
                .isTrue();
    }

    @Test
    void theJavaFloorMatches() {
        assertThat(README).contains("Java 21");
        assertThat(Cli.read(Path.of("build.gradle"))).contains("JavaLanguageVersion.of(21)");
    }

    @Test
    void theServiceIsDocumented() {
        assertThat(README)
                .contains(":service:bootJar")
                .contains("lexdrift-service-")
                .contains("LEXDRIFT_API_TOKEN");
    }

    @Test
    void theLibraryExampleUsesExportedNames() {
        assertThat(README).contains("new Engine(new EngineConfig())").contains("Lexdrift.discover(");
        assertThat(Engine.class.getPackageName()).isEqualTo(Lexdrift.class.getPackageName());
        assertThat(EngineConfig.class.getPackageName()).isEqualTo(Lexdrift.class.getPackageName());
    }

    @Test
    void theVersionIsNotMentionedInProse() {
        // A version in the README is a version that goes stale on every release.
        assertThat(README).doesNotContain(Lexdrift.VERSION);
    }

    @Test
    void theReportFormatsAreDocumented() {
        String lowered = README.toLowerCase(Locale.ROOT);
        assertThat(ReportWriters.formatNames())
                .allSatisfy(name -> assertThat(lowered).contains(name));
    }
}
