package io.github.rkaushikdev.galleyworks.index;

import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_ERROR;
import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_OK;
import static io.github.rkaushikdev.galleyworks.support.Cli.run;
import static io.github.rkaushikdev.galleyworks.support.GoldenCorpus.goldenInstruments;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rkaushikdev.galleyworks.Engine;
import io.github.rkaushikdev.galleyworks.support.Cli;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StatsTest {

    @TempDir
    Path tmp;

    private static MemoryIndex loaded() {
        Engine engine = new Engine();
        goldenInstruments().forEach(engine::add);
        assertThat(engine.index()).isInstanceOf(MemoryIndex.class);
        return (MemoryIndex) engine.index();
    }

    private String indexPath() {
        Path root = Cli.goldenCorpus(tmp);
        String path = tmp.resolve("index.sqlite").toString();
        run("index", "--index", path, root.toString());
        return path;
    }

    @Test
    void anEmptyIndexMeasuresZero() {
        Statistics.CorpusStats measured = Statistics.describe(new MemoryIndex());
        assertThat(measured.instruments()).isZero();
        assertThat(measured.meanTokens()).isZero();
        assertThat(measured.selectivity()).isZero();
        assertThat(measured.widestPostingShare()).isZero();
    }

    @Test
    void theCorpusIsCounted() {
        Statistics.CorpusStats measured = Statistics.describe(loaded());
        assertThat(measured.instruments()).isEqualTo(6);
        assertThat(measured.segments()).isGreaterThan(4);
        assertThat(measured.jurisdictions().keySet())
                .containsExactlyInAnyOrder("wy", "mt", "nd", "co", "laramie", "cheyenne");
        assertThat(measured.sessions().keySet()).containsExactlyInAnyOrder("2023", "2024", "2025");
    }

    @Test
    void averagesArePositive() {
        Statistics.CorpusStats measured = Statistics.describe(loaded());
        assertThat(measured.meanTokens()).isGreaterThan(5);
        assertThat(measured.meanFingerprints()).isPositive();
    }

    @Test
    void selectivityIsAFraction() {
        Statistics.CorpusStats measured = Statistics.describe(loaded());
        assertThat(measured.selectivity()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
        assertThat((long) measured.distinctFingerprints()).isLessThanOrEqualTo(measured.totalFingerprints());
    }

    @Test
    void theWidestPostingIsReported() {
        Statistics.CorpusStats measured = Statistics.describe(loaded());
        assertThat(measured.widestPosting()).isGreaterThanOrEqualTo(1);
        assertThat(measured.widestPostingShare()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
    }

    @Test
    void theSummaryLinesMentionEverything() {
        String lines = String.join("\n", Statistics.describe(loaded()).lines());
        assertThat(lines)
                .contains("instruments")
                .contains("jurisdictions:")
                .contains("selectivity")
                .contains("engine digest");
    }

    @Test
    void anEmptySummarySaysNone() {
        assertThat(String.join("\n", new Statistics.CorpusStats(0, 0).lines())).contains("jurisdictions: none");
    }

    @Test
    void theCommandPrintsTheSummary() {
        Cli.Result result = run("stats", "--index", indexPath());
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(result.output()).contains("6 instruments");
    }

    @Test
    void theCommandCanEmitJson() throws Exception {
        JsonNode payload = new ObjectMapper()
                .readTree(run("stats", "--json", "--index", indexPath()).output());
        assertThat(payload.get("instruments").asInt()).isEqualTo(6);
        assertThat(payload.at("/jurisdictions/wy").asInt()).isEqualTo(1);
        assertThat(payload.get("engine_digest").asText()).hasSize(16);
    }

    @Test
    void aMissingIndexIsAnError() {
        assertThat(run(
                                "stats",
                                "--index",
                                tmp.resolve("missing").resolve("i.sqlite").toString())
                        .code())
                .isEqualTo(EXIT_ERROR);
    }
}
