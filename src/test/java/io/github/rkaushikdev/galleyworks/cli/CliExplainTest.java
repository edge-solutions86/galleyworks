package io.github.rkaushikdev.galleyworks.cli;

import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_ERROR;
import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_FINDINGS;
import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_OK;
import static io.github.rkaushikdev.galleyworks.support.Cli.run;
import static io.github.rkaushikdev.galleyworks.support.GoldenCorpus.goldenInstruments;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.Engine;
import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.report.InstrumentReport;
import io.github.rkaushikdev.galleyworks.support.Cli;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliExplainTest {

    @TempDir
    Path tmp;

    private String indexPath;
    private String copy;
    private String original;

    @BeforeEach
    void prepared() {
        Path root = Cli.mkdir(tmp.resolve("bills"));
        List<Instrument> instruments = goldenInstruments();
        Cli.writeBill(root, instruments.getFirst());
        indexPath = tmp.resolve("index.sqlite").toString();
        run("index", "--index", indexPath, root.toString());
        copy = Cli.write(tmp.resolve("montana.txt"), instruments.get(1).rawText())
                .toString();
        original = Cli.write(tmp.resolve("colorado.txt"), instruments.get(3).rawText())
                .toString();
    }

    private static InstrumentReport reportFor(String slug) {
        return new Engine().runOver(goldenInstruments()).reportFor(slug).orElseThrow();
    }

    @Test
    void theCaseStartsWithTheSummary() {
        List<String> lines = ExplainCommand.renderCase(reportFor("mt/2024/lower/HB0311"));
        assertThat(lines.getFirst()).startsWith("mt/2024/lower/HB0311:").contains("of the document reused");
    }

    @Test
    void theCaseNumbersItsFindings() {
        String body = String.join("\n", ExplainCommand.renderCase(reportFor("mt/2024/lower/HB0311")));
        assertThat(body).contains("1. ").contains("evidence that");
    }

    @Test
    void theCaseNeverQuotesTheText() {
        String body = String.join("\n", ExplainCommand.renderCase(reportFor("mt/2024/lower/HB0311")));
        assertThat(body).doesNotContain("schedule of appropriations of record");
    }

    @Test
    void anEmptyReportSaysSo() {
        assertThat(String.join("\n", ExplainCommand.renderCase(reportFor("co/2025/upper/SB0007"))))
                .contains("no reuse found");
    }

    @Test
    void theCommandExplainsACopy() {
        Cli.Result result = run("explain", "--index", indexPath, "--jurisdiction", "mt", copy);
        assertThat(result.code()).isIn(EXIT_OK, EXIT_FINDINGS);
        assertThat(result.output()).contains("wy/2023/lower/HB0142").contains("evidence that");
    }

    @Test
    void theCommandIsQuietOnAnOriginal() {
        Cli.Result result = run("explain", "--index", indexPath, "--jurisdiction", "co", original);
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(result.output()).contains("no reuse found");
    }

    @Test
    void theDisplayFiltersApply() {
        String output = run("explain", "--index", indexPath, "--jurisdiction", "mt", "--top", "1", copy)
                .output();
        assertThat(Cli.count(output, "evidence that")).isLessThanOrEqualTo(1);
    }

    @Test
    void theConfidenceFloorApplies() {
        String output = run("explain", "--index", indexPath, "--jurisdiction", "mt", "--min-confidence", "strong", copy)
                .output();
        assertThat(output).doesNotContain("weak evidence");
    }

    @Test
    void aMissingDocumentIsAnError() {
        assertThat(run("explain", "--index", indexPath, "--jurisdiction", "mt", "absent.txt")
                        .code())
                .isEqualTo(EXIT_ERROR);
    }
}
