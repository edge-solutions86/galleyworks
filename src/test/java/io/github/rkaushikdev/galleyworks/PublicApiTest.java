package io.github.rkaushikdev.galleyworks;

import static io.github.rkaushikdev.galleyworks.support.GoldenCorpus.goldenInstruments;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.report.InstrumentReport;
import io.github.rkaushikdev.galleyworks.support.Cli;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The public surface: the types a caller imports from the root package, the report they get back,
 * and the helpers on {@link Lexdrift}.
 */
class PublicApiTest {

    private static final List<String> TYPES = List.of(
            "CanonConfig",
            "CanonError",
            "Chamber",
            "ConfigError",
            "Corpus",
            "Engine",
            "EngineConfig",
            "GovernmentLevel",
            "IngestConfig",
            "IngestError",
            "Instrument",
            "InstrumentKey",
            "InstrumentReport",
            "Jurisdiction",
            "LexdriftError",
            "LshConfig",
            "MatchConfig",
            "MatchError",
            "ParseError",
            "ReportConfig",
            "RunResult",
            "Segment",
            "SegmentKind",
            "ShingleConfig",
            "StoreError",
            "UnknownFormatError");

    private static final List<String> HELPERS =
            List.of("VERSION", "discover", "errorCodeOf", "httpStatusOf", "loadConfig", "loadManifest");

    /** Where each exported type lives: the root package, except the report the engine returns. */
    private static final Map<String, String> PACKAGES = Map.of("InstrumentReport", "report");

    @TempDir
    Path tmp;

    private static Class<?> exported(String name) throws ClassNotFoundException {
        String root = Lexdrift.class.getPackageName();
        return Class.forName(
                PACKAGES.containsKey(name) ? root + "." + PACKAGES.get(name) + "." + name : root + "." + name);
    }

    /** The names of the facade's public static members. */
    private static TreeSet<String> facade() {
        TreeSet<String> names = new TreeSet<>();
        for (Field field : Lexdrift.class.getDeclaredFields()) {
            if (isPublicStatic(field.getModifiers())) {
                names.add(field.getName());
            }
        }
        for (Method method : Lexdrift.class.getDeclaredMethods()) {
            if (isPublicStatic(method.getModifiers())) {
                names.add(method.getName());
            }
        }
        return names;
    }

    private static boolean isPublicStatic(int modifiers) {
        return Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers);
    }

    @Test
    void thePublicSurfaceIsPinned() {
        assertThat(facade()).containsExactlyElementsOf(HELPERS);
    }

    @Test
    void everyExportedNameExists() throws ClassNotFoundException {
        for (String name : TYPES) {
            assertThat(Modifier.isPublic(exported(name).getModifiers()))
                    .as(name)
                    .isTrue();
        }
    }

    @Test
    void theExportListIsSorted() {
        String source = Cli.read(Path.of("src/main/java/io/github/rkaushikdev/galleyworks/Lexdrift.java"));
        List<String> declared = new ArrayList<>();
        for (String line : source.lines().toList()) {
            String trimmed = line.strip();
            if (trimmed.startsWith("public static ")) {
                String name =
                        trimmed.substring(0, trimmed.contains("(") ? trimmed.indexOf('(') : trimmed.indexOf(" ="));
                name = name.substring(name.lastIndexOf(' ') + 1);
                if (!declared.contains(name)) {
                    declared.add(name);
                }
            }
        }
        assertThat(declared)
                .as("helpers declared in %s", "Lexdrift.java")
                .hasSize(HELPERS.size())
                .isSorted();
    }

    @Test
    void theVersionIsAReleaseNumber() {
        String[] parts = Lexdrift.VERSION.split("\\.", -1);
        assertThat(parts).hasSize(3).allSatisfy(part -> assertThat(part).matches("[0-9]+"));
    }

    @Test
    void theReadmeExampleRuns() {
        Path root = Cli.goldenCorpus(tmp);
        Engine engine = new Engine(new EngineConfig());
        RunResult result = engine.run(Lexdrift.discover(root));
        List<Double> reported = result.reports().stream()
                .filter(report -> !report.isEmpty())
                .map(report -> report.summary().reuseRatio())
                .toList();
        assertThat(reported)
                .isNotEmpty()
                .allSatisfy(ratio -> assertThat(ratio).isGreaterThan(0.0).isLessThanOrEqualTo(1.0));
        assertThat(goldenInstruments()).hasSize(result.instrumentCount());
    }

    @Test
    void theEngineIsThePipelineEngine() throws ClassNotFoundException {
        assertThat(exported("Engine")).isSameAs(Engine.class);
        assertThat(exported("InstrumentReport")).isSameAs(InstrumentReport.class);
    }

    @Test
    void theConfigTypesAreTheConfigurationTypes() throws ClassNotFoundException {
        assertThat(exported("EngineConfig")).isSameAs(EngineConfig.class);
        assertThat(exported("ReportConfig")).isSameAs(ReportConfig.class);
        assertThat(EngineConfig.class.isRecord()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"discover", "loadManifest", "loadConfig"})
    void theExportedHelpersAreCallable(String name) {
        List<Method> methods = Arrays.stream(Lexdrift.class.getMethods())
                .filter(method -> method.getName().equals(name) && Modifier.isStatic(method.getModifiers()))
                .toList();
        assertThat(methods).as(name).isNotEmpty();
    }
}
