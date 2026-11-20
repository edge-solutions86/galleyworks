package io.github.rkaushikdev.galleyworks.cli;

import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_FINDINGS;
import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_OK;
import static io.github.rkaushikdev.galleyworks.support.Cli.run;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.LOCAL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rkaushikdev.galleyworks.Chamber;
import io.github.rkaushikdev.galleyworks.ConfigError;
import io.github.rkaushikdev.galleyworks.InstrumentKey;
import io.github.rkaushikdev.galleyworks.support.Cli;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliReportTest {

    private static final InstrumentKey KEY = new InstrumentKey("wy", "2025", Chamber.LOWER, "HB0142");

    @TempDir
    Path tmp;

    private String corpus;

    @BeforeEach
    void corpus() {
        Path root = Cli.mkdir(tmp.resolve("bills"));
        Cli.writeBill2025(root, makeBill("wy", "HB0142", MODEL_CLAUSE));
        Cli.writeBill2025(root, makeBill("mt", "HB0311", MODEL_CLAUSE));
        Cli.writeBill2025(root, makeBill("co", "SB0007", LOCAL_CLAUSE));
        corpus = root.toString();
    }

    private static List<String> names(Path directory) throws IOException {
        try (Stream<Path> listing = Files.list(directory)) {
            return listing.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        }
    }

    @Test
    void fileNamesAreFilesystemSafe() {
        assertThat(ReportCommand.fileNameFor(KEY, "text")).isEqualTo("wy-2025-lower-HB0142.txt");
        assertThat(ReportCommand.fileNameFor(KEY, "json")).endsWith(".json");
    }

    @Test
    void anUnknownFormatIsRejected() {
        assertThatThrownBy(() -> ReportCommand.fileNameFor(KEY, "yaml"))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("unknown output format");
    }

    @Test
    void aCorpusWithReuseIsAFinding() {
        Cli.Result result = run("report", corpus);
        assertThat(result.code()).isEqualTo(EXIT_FINDINGS);
        assertThat(result.output()).contains("3 instruments");
    }

    @Test
    void reportsArePrintedToTheStream() {
        assertThat(run("report", corpus).output())
                .contains("lexdrift report for")
                .contains("no reuse found");
    }

    @Test
    void onlyFindingsHidesTheCleanOnes() {
        assertThat(run("report", "--only-findings", corpus).output()).doesNotContain("no reuse found");
    }

    @Test
    void reportsCanBeWrittenToADirectory() throws IOException {
        Path outDir = tmp.resolve("reports");
        Cli.Result result = run("report", "--out", outDir.toString(), corpus);
        assertThat(result.code()).isEqualTo(EXIT_FINDINGS);
        assertThat(names(outDir))
                .containsExactly("co-2025-lower-SB0007.txt", "mt-2025-lower-HB0311.txt", "wy-2025-lower-HB0142.txt");
        assertThat(result.output()).contains("3 reports written");
    }

    @Test
    void jsonReportsAreWritten() throws IOException {
        Path outDir = tmp.resolve("reports");
        run("report", "--out", outDir.toString(), "--output", "json", corpus);
        JsonNode payload = new ObjectMapper().readTree(Cli.read(outDir.resolve("wy-2025-lower-HB0142.json")));
        assertThat(payload.at("/instrument/jurisdiction").asText()).isEqualTo("wy");
    }

    @Test
    void onlyFindingsLimitsWhatIsWritten() throws IOException {
        Path outDir = tmp.resolve("reports");
        run("report", "--only-findings", "--out", outDir.toString(), corpus);
        assertThat(names(outDir)).doesNotContain("co-2025-lower-SB0007.txt");
    }

    @Test
    void aCleanCorpusExitsZero() {
        Path root = Cli.mkdir(tmp.resolve("one"));
        Cli.writeBill2025(root, makeBill("co", "SB0007", LOCAL_CLAUSE));
        Cli.Result result = run("report", root.toString());
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(result.output()).contains("1 instruments, 0 with reuse");
    }

    @Test
    void theEdgeCountIsReported() {
        assertThat(run("report", corpus).output()).contains("edges");
    }

    @Test
    void theOutputDirectoryIsCreated() {
        Path outDir = tmp.resolve("deep").resolve("reports");
        run("report", "--out", outDir.toString(), corpus);
        assertThat(outDir).isDirectory();
    }
}
