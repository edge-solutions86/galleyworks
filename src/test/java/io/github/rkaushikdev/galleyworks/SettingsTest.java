package io.github.rkaushikdev.galleyworks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsTest {

    @TempDir
    Path tmp;

    private Path write(String text) throws IOException {
        Path path = tmp.resolve("engine.yaml");
        Files.writeString(path, text, StandardCharsets.UTF_8);
        return path;
    }

    @Test
    void parseScalarReadsYamlTypes() {
        assertThat(Settings.parseScalar("12")).isEqualTo(12);
        assertThat((Double) Settings.parseScalar("0.5")).isCloseTo(0.5, within(1e-9));
        assertThat(Settings.parseScalar("true")).isEqualTo(Boolean.TRUE);
        assertThat(Settings.parseScalar("character")).isEqualTo("character");
    }

    @Test
    void parseScalarFallsBackToTheRawString() {
        assertThat(Settings.parseScalar("[unclosed")).isEqualTo("[unclosed");
    }

    @Test
    void deepMergeDoesNotMutateItsInputs() {
        Map<String, Object> base = Map.of("a", Map.of("x", 1, "y", 2));
        Map<String, Object> overlay = Map.of("a", Map.of("y", 3), "b", 4);
        Map<String, Object> merged = Settings.deepMerge(base, overlay);
        assertThat(merged).isEqualTo(Map.of("a", Map.of("x", 1, "y", 3), "b", 4));
        assertThat(base).isEqualTo(Map.of("a", Map.of("x", 1, "y", 2)));
    }

    @Test
    void deepMergeReplacesScalarsWithMappings() {
        assertThat(Settings.deepMerge(Map.of("a", 1), Map.of("a", Map.of("b", 2))))
                .isEqualTo(Map.of("a", Map.of("b", 2)));
    }

    @Test
    void envOverridesAreNestedOnTheDoubleUnderscore() {
        Map<String, String> env = Map.of("LEXDRIFT__MATCH__MIN_CONTAINMENT", "0.75", "PATH", "/bin");
        assertThat(Settings.applyEnvOverrides(Map.of(), env))
                .isEqualTo(Map.of("match", Map.of("min_containment", 0.75)));
    }

    @Test
    void envOverridesIgnoreUnrelatedVariables() {
        assertThat(Settings.applyEnvOverrides(Map.of("a", 1), Map.of("HOME", "/root")))
                .isEqualTo(Map.of("a", 1));
    }

    @Test
    void envOverrideWithNoFieldPathIsRejected() {
        assertThatThrownBy(() -> Settings.applyEnvOverrides(Map.of(), Map.of("LEXDRIFT__", "1")))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("no field path");
    }

    @Test
    void envOverrideCollidingWithAScalarIsRejected() {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("LEXDRIFT__MATCH", "5");
        env.put("LEXDRIFT__MATCH__MIN_CONTAINMENT", "0.5");
        assertThatThrownBy(() -> Settings.applyEnvOverrides(Map.of(), env))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("collides with a scalar");
    }

    @Test
    void loadConfigWithoutAFileGivesDefaults() {
        EngineConfig cfg = Settings.loadConfig(Optional.empty(), Map.of());
        assertThat(cfg.shingle().width()).isEqualTo(5);
    }

    @Test
    void loadConfigReadsYaml() throws IOException {
        Path path = write("shingle:\n  mode: character\n  width: 9\n");
        EngineConfig cfg = Settings.loadConfig(Optional.of(path), Map.of());
        assertThat(cfg.shingle().mode()).isSameAs(ShingleMode.CHARACTER);
        assertThat(cfg.shingle().width()).isEqualTo(9);
    }

    @Test
    void environmentBeatsTheFile() throws IOException {
        Path path = write("shingle:\n  width: 9\n");
        EngineConfig cfg = Settings.loadConfig(Optional.of(path), Map.of("LEXDRIFT__SHINGLE__WIDTH", "3"));
        assertThat(cfg.shingle().width()).isEqualTo(3);
    }

    @Test
    void emptyFileIsTreatedAsDefaults() throws IOException {
        Path path = write("");
        assertThat(Settings.loadConfig(Optional.of(path), Map.of()).lsh().permutations())
                .isEqualTo(128);
    }

    @Test
    void nonMappingFileIsRejected() throws IOException {
        Path path = write("- a\n- b\n");
        assertThatThrownBy(() -> Settings.loadConfig(Optional.of(path), Map.of()))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("must hold a mapping");
    }

    @Test
    void brokenYamlIsRejected() throws IOException {
        Path path = write("shingle: [1, 2\n");
        assertThatThrownBy(() -> Settings.loadConfig(Optional.of(path), Map.of()))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("not valid YAML");
    }

    @Test
    void missingFileIsRejected() {
        assertThatThrownBy(() -> Settings.loadConfig(Optional.of(tmp.resolve("absent.yaml")), Map.of()))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("cannot be read");
    }
}
