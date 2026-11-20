package io.github.rkaushikdev.galleyworks;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.cli.Main;
import io.github.rkaushikdev.galleyworks.support.Cli;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import picocli.CommandLine;

/** What the build promises about the artifacts it makes. */
class PackageTest {

    static final List<String> EXPECTED_COMMANDS = List.of(
            "check",
            "completion",
            "config",
            "diff",
            "diffusion",
            "explain",
            "index",
            "ingest",
            "manifest",
            "query",
            "report",
            "serve",
            "stats");

    private static final String BUILD = Cli.read(Path.of("build.gradle"));
    private static final String SERVICE_BUILD = Cli.read(Path.of("service", "build.gradle"));

    private static String declared(String script, String pattern) {
        Matcher match = Pattern.compile(pattern, Pattern.MULTILINE).matcher(script);
        assertThat(match.find()).as(pattern).isTrue();
        return match.group(1);
    }

    @Test
    void theVersionIsDeclaredOnce() {
        assertThat(declared(BUILD, "^version = '([^']+)'$")).isEqualTo(Lexdrift.VERSION);
        assertThat(SERVICE_BUILD).contains("version = rootProject.version").doesNotContain(Lexdrift.VERSION);
    }

    @Test
    void theBuildTreatsWarningsAsErrors() {
        for (String script : List.of(BUILD, SERVICE_BUILD)) {
            assertThat(script).contains("'-Xlint:all,-serial,-processing'").contains("'-Werror'");
        }
    }

    @Test
    void theConsoleScriptPointsAtACallable() throws NoSuchMethodException {
        assertThat(declared(BUILD, "^\\s*applicationName = '([^']+)'$")).isEqualTo("lexdrift");
        String mainClass = declared(BUILD, "^\\s*mainClass = '([^']+)'$");
        assertThat(mainClass).isEqualTo(Main.class.getName());
        Method main = Main.class.getMethod("main", String[].class);
        assertThat(Modifier.isStatic(main.getModifiers())).isTrue();
        assertThat(declared(SERVICE_BUILD, "^\\s*mainClass = '([^']+)'$")).isEqualTo(mainClass);
    }

    @Test
    void everyCommandIsRegistered() {
        assertThat(Main.commandNames()).isEqualTo(EXPECTED_COMMANDS);
    }

    @Test
    void everyCommandHasHelp() {
        Main.commandLine().getSubcommands().forEach((name, command) -> assertThat(
                        String.join(" ", command.getCommandSpec().usageMessage().header()))
                .as(name)
                .isNotBlank());
    }

    @Test
    void everyClassLoads() throws IOException, ClassNotFoundException {
        Path root = Path.of("src", "main", "java");
        List<String> names;
        try (Stream<Path> walk = Files.walk(root)) {
            names = walk.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(path ->
                            root.relativize(path).toString().replace('/', '.').replace(".java", ""))
                    .toList();
        }
        assertThat(names).hasSizeGreaterThan(40);
        for (String name : names) {
            Class.forName(name, true, getClass().getClassLoader());
        }
    }

    @Test
    void theRuntimeDependenciesAreDeclared() {
        assertThat(BUILD).containsPattern("api 'info\\.picocli:picocli:[0-9.]+'");
        assertThat(BUILD).containsPattern("api 'org\\.yaml:snakeyaml:[0-9.]+'");
        assertThat(BUILD).containsPattern("implementation 'org\\.xerial:sqlite-jdbc:[0-9.]+'");
    }

    @Test
    void theServiceProjectExists() {
        assertThat(Cli.read(Path.of("settings.gradle"))).contains("include 'service'");
        assertThat(SERVICE_BUILD)
                .contains("implementation project(':')")
                .contains("org.springframework.boot:spring-boot-starter-web");
    }

    @Test
    void theJavaFloorMatchesTheSyntaxUsed() {
        for (String script : List.of(BUILD, SERVICE_BUILD)) {
            assertThat(script).contains("languageVersion = JavaLanguageVersion.of(21)");
        }
        assertThat(Runtime.version().feature()).isGreaterThanOrEqualTo(21);
    }

    @ParameterizedTest
    @FieldSource("EXPECTED_COMMANDS")
    void eachCommandClassIsLoadable(String name) throws ClassNotFoundException {
        String simple = Character.toUpperCase(name.charAt(0)) + name.substring(1) + "Command";
        Class<?> command = Class.forName(Main.class.getPackageName() + "." + simple);
        assertThat(command.getAnnotation(CommandLine.Command.class).name()).isEqualTo(name);
    }
}
