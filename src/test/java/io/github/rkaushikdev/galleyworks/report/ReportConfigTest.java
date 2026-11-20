package io.github.rkaushikdev.galleyworks.report;

import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_OK;
import static io.github.rkaushikdev.galleyworks.support.Cli.run;
import static io.github.rkaushikdev.galleyworks.support.GoldenCorpus.goldenInstruments;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.galleyworks.Engine;
import io.github.rkaushikdev.galleyworks.EngineConfig;
import io.github.rkaushikdev.galleyworks.ReportConfig;
import io.github.rkaushikdev.galleyworks.match.Confidence;
import io.github.rkaushikdev.galleyworks.support.Cli;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportConfigTest {

    @TempDir
    Path tmp;

    private static InstrumentReport reportFor(EngineConfig config) {
        return new Engine(config)
                .runOver(goldenInstruments())
                .reportFor("nd/2025/lower/HB0044")
                .orElseThrow();
    }

    private static InstrumentReport plain() {
        return reportFor(new EngineConfig());
    }

    private static EngineConfig with(ReportConfig report) {
        return new EngineConfig().withReport(report);
    }

    @Test
    void theDefaultsShowEverything() {
        ReportConfig settings = new ReportConfig();
        assertThat(settings.minConfidence()).isEqualTo("weak");
        assertThat(settings.crossingOnly()).isFalse();
        assertThat(settings.top()).isEmpty();
        assertThat(settings.maxPassages()).isEqualTo(3);
    }

    @Test
    void theDefaultsChangeNothing() {
        InstrumentReport report = plain();
        assertThat(Ranking.applyReportConfig(report, new ReportConfig()).matches())
                .isEqualTo(report.matches());
    }

    @Test
    void aConfidenceFloorNarrowsTheReport() {
        assertThat(reportFor(with(new ReportConfig().withMinConfidence("strong")))
                        .matches())
                .allSatisfy(match -> assertThat(match.confidence()).isEqualTo(Confidence.STRONG));
    }

    @Test
    void aTopLimitNarrowsTheReport() {
        assertThat(reportFor(with(new ReportConfig().withTop(Optional.of(1)))).matches())
                .hasSizeLessThanOrEqualTo(1);
    }

    @Test
    void crossingOnlyDropsSameJurisdictionFindings() {
        InstrumentReport built = reportFor(with(new ReportConfig().withCrossingOnly(true)));
        assertThat(built.matches())
                .allSatisfy(match -> assertThat(match.otherKey().jurisdiction())
                        .isNotEqualTo(built.key().jurisdiction()));
    }

    @Test
    void theSummaryStillDescribesTheWholeDocument() {
        assertThat(reportFor(with(new ReportConfig().withTop(Optional.of(1)))).summary())
                .isEqualTo(plain().summary());
    }

    @Test
    void aZeroTopIsRejected() {
        assertThatThrownBy(() -> new ReportConfig().withTop(Optional.of(0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theReportConfigIsNotPartOfTheDigest() {
        EngineConfig narrowed =
                with(new ReportConfig().withMinConfidence("strong").withTop(Optional.of(1)));
        assertThat(narrowed.digest()).isEqualTo(new EngineConfig().digest());
    }

    @Test
    void theConfigCommandReportsTheDefaults() {
        Cli.Result result = run("config");
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(result.output()).contains("report: confidence >= weak").contains("top all");
    }

    @Test
    void aConfigFileNarrowsARun() {
        Path root = Cli.goldenCorpus(tmp);
        Path settings = Cli.write(tmp.resolve("engine.yaml"), "report:\n  crossing_only: true\n  top: 1\n");
        String output = run("report", "--config", settings.toString(), "--only-findings", root.toString())
                .output();
        assertThat(Cli.count(output, " matches ")).isLessThanOrEqualTo(4);
    }
}
