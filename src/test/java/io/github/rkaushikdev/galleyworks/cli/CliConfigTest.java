package io.github.rkaushikdev.galleyworks.cli;

import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_ERROR;
import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_FINDINGS;
import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_OK;
import static io.github.rkaushikdev.galleyworks.support.Cli.run;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rkaushikdev.galleyworks.EngineConfig;
import io.github.rkaushikdev.galleyworks.support.Cli;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliConfigTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tmp;

    @Test
    void theDerivedNumbersFollowTheSettings() {
        EngineConfig config = new EngineConfig()
                .withShingle(new EngineConfig().shingle().withWidth(5).withWinnowWindow(4));
        Map<String, Object> numbers = ConfigCommand.derived(config);
        assertThat(numbers.get("guarantee_length_tokens")).isEqualTo(8);
        assertThat((Double) numbers.get("expected_density")).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
        assertThat(String.valueOf(numbers.get("engine_digest"))).hasSize(16);
    }

    @Test
    void theBandingNumbersAreDerived() {
        EngineConfig config = new EngineConfig()
                .withLsh(new EngineConfig().lsh().withPermutations(128).withBands(16));
        Map<String, Object> numbers = ConfigCommand.derived(config);
        assertThat(numbers.get("lsh_rows")).isEqualTo(8);
        assertThat((Double) numbers.get("lsh_threshold")).isGreaterThan(0.0).isLessThan(1.0);
    }

    @Test
    void theDefaultSettingsPrint() {
        Cli.Result result = run("config");
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(result.output())
                .contains("profile: default")
                .contains("engine digest")
                .contains("report formats:");
    }

    @Test
    void aConfigFileIsReflected() {
        Path path = Cli.write(tmp.resolve("engine.yaml"), "shingle:\n  width: 7\n");
        assertThat(run("config", "--config", path.toString()).output()).contains("width 7");
    }

    @Test
    void aProfileIsReflected() {
        assertThat(run("config", "--profile", "verbatim").output()).contains("profile: verbatim");
    }

    @Test
    void theJsonFormParses() throws Exception {
        JsonNode payload = JSON.readTree(run("config", "--json").output());
        assertThat(payload.at("/settings/shingle/width").asInt()).isEqualTo(5);
        assertThat(payload.at("/derived/lsh_rows").asInt()).isEqualTo(4);
    }

    @Test
    void theProfilesCanBeListed() {
        Cli.Result result = run("config", "--list-profiles");
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(result.output()).contains("* default").contains("verbatim");
    }

    @Test
    void theSelectedProfileIsMarked() {
        assertThat(run("config", "--list-profiles", "--profile", "municipal").output())
                .contains("* municipal");
    }

    @Test
    void aDangerousThresholdIsFlagged() {
        Path path = Cli.write(tmp.resolve("engine.yaml"), "match:\n  min_containment: 0.05\n");
        Cli.Result result = run("config", "--config", path.toString());
        assertThat(result.code()).isEqualTo(EXIT_FINDINGS);
        assertThat(result.output()).contains("warning");
    }

    @Test
    void anUnknownProfileIsAnError() {
        assertThat(run("config", "--profile", "nowhere").code()).isEqualTo(EXIT_ERROR);
    }

    @Test
    void aBrokenConfigFileIsAnError() {
        Path path = Cli.write(tmp.resolve("engine.yaml"), "shingle: [\n");
        assertThat(run("config", "--config", path.toString()).code()).isEqualTo(EXIT_ERROR);
    }
}
