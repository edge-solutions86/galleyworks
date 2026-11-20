package io.github.rkaushikdev.galleyworks.cli;

import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_ERROR;
import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_OK;
import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_USAGE;
import static io.github.rkaushikdev.galleyworks.support.Cli.run;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.support.Cli;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * What every command promises about its arguments, checked once for every command that makes the
 * promise. The dependency pins are checked here too, because a promise like this breaks when a
 * dependency range is wide enough to admit a behaviour change.
 */
class CliContractTest {

    /** Commands that cannot do anything useful without being told where the index is. */
    static final List<String> INDEX_REQUIRED = List.of("check", "query", "serve", "stats");

    /** Commands that read a corpus and therefore need a source argument. */
    static final List<String> SOURCE_REQUIRED = List.of("ingest", "index", "report", "diffusion", "manifest");

    /** The libraries the command line runs on. */
    static final List<String> RUNTIME = List.of("picocli", "snakeyaml", "sqlite-jdbc", "logback-classic");

    private static final Pattern COORDINATE = Pattern.compile(
            "^\\s*(implementation|runtimeOnly|api)\\s+'([\\w.-]+):([\\w.-]+)(?::([^']*))?'", Pattern.MULTILINE);
    private static final Pattern RELEASE = Pattern.compile("\\d+(\\.\\d+)+");

    @TempDir
    Path tmp;

    /** Artifact name to version for every runtime dependency declared in {@code script}. */
    static Map<String, String> runtimePins(String script) {
        Map<String, String> pins = new LinkedHashMap<>();
        Matcher match = COORDINATE.matcher(script);
        while (match.find()) {
            pins.put(match.group(3), match.group(4) == null ? "" : match.group(4));
        }
        return pins;
    }

    private static Map<String, String> pins() {
        Map<String, String> pins = runtimePins(Cli.read(Path.of("build.gradle")));
        assertThat(pins).as("runtime dependencies parsed from build.gradle").hasSizeGreaterThanOrEqualTo(4);
        return pins;
    }

    @ParameterizedTest
    @FieldSource("INDEX_REQUIRED")
    void theIndexOptionIsRequired(String name) {
        assertThat(run(name).code()).as("%s ran without --index", name).isEqualTo(EXIT_USAGE);
    }

    @ParameterizedTest
    @FieldSource("SOURCE_REQUIRED")
    void theSourceArgumentIsRequired(String name) {
        assertThat(run(name).code()).as("%s ran without a source", name).isEqualTo(EXIT_USAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"diff", "explain", "query"})
    void theJurisdictionOptionIsRequired(String name) {
        assertThat(run(name, "a.txt", "b.txt").code()).isEqualTo(EXIT_USAGE);
    }

    @Test
    void anUnknownOptionIsAUsageError() {
        assertThat(run("config", "--nonesuch").code()).isEqualTo(EXIT_USAGE);
    }

    @Test
    void anUnknownCommandIsAUsageError() {
        assertThat(run("nonesuch").code()).isEqualTo(EXIT_USAGE);
    }

    @Test
    void noArgumentsPrintsHelp() {
        Cli.Result result = run();
        assertThat(result.code()).isEqualTo(EXIT_USAGE);
        assertThat(result.output()).contains("Usage: lexdrift");
    }

    @Test
    void helpIsNotAnError() {
        assertThat(run("--help").code()).isEqualTo(EXIT_OK);
        assertThat(run("config", "--help").code()).isEqualTo(EXIT_OK);
    }

    @Test
    void aMissingFileIsAnEngineErrorNotAUsageOne() {
        String indexPath = tmp.resolve("index.sqlite").toString();
        run("index", "--index", indexPath, tmp.toString());
        assertThat(run("query", "--index", indexPath, "--jurisdiction", "wy", "absent.txt")
                        .code())
                .isEqualTo(EXIT_ERROR);
    }

    @ParameterizedTest
    @FieldSource("RUNTIME")
    void everyRuntimeDependencyIsPinned(String name) {
        Map<String, String> pins = pins();
        assertThat(pins).as("%s floats", name).containsKey(name);
        assertThat(pins.get(name)).as("%s has no version", name).isNotEmpty();
    }

    @Test
    void theRuntimeVersionsAreExact() {
        assertThat(pins())
                .allSatisfy(
                        (name, version) -> assertThat(version).as(name).doesNotContain("+", "[", "(", ",", "latest."));
    }

    @Test
    void thePinnedVersionsAreReleases() {
        assertThat(pins())
                .allSatisfy((name, version) -> assertThat(version).as(name).matches(RELEASE));
    }

    @Test
    void thePinCheckerSeesAFloatingVersion() {
        Map<String, String> planted =
                runtimePins("dependencies {\n\timplementation 'a.b:fixed:1.2.3'\n\truntimeOnly 'a.b:floating:1.+'\n"
                        + "\timplementation 'a.b:bare'\n\ttestImplementation 'a.b:ignored:9'\n}\n");
        assertThat(planted)
                .containsExactly(Map.entry("fixed", "1.2.3"), Map.entry("floating", "1.+"), Map.entry("bare", ""));
        assertThat(planted.get("floating")).doesNotMatch(RELEASE);
    }

    @Test
    void everyCommandIsCoveredByOneOfTheseLists() {
        Set<String> named = new TreeSet<>(INDEX_REQUIRED);
        named.addAll(SOURCE_REQUIRED);
        named.addAll(List.of("config", "completion", "diff", "explain"));
        assertThat(named).containsAll(Main.commandNames());
    }
}
