package io.github.rkaushikdev.galleyworks;

import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.LOCAL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.diffusion.ReuseEdge;
import io.github.rkaushikdev.galleyworks.report.InstrumentReport;
import io.github.rkaushikdev.galleyworks.report.ReportedPassage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PipelineTest {

    private static final Optional<OffsetDateTime> FIXED =
            Optional.of(OffsetDateTime.of(2026, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC));

    @TempDir
    Path tmp;

    private Path corpusDir() throws IOException {
        Instrument earlier = makeBill("wy", "HB0142", MODEL_CLAUSE, LocalDate.of(2024, 7, 1));
        Instrument later = makeBill("mt", "HB0311", MODEL_CLAUSE, LocalDate.of(2025, 7, 1));
        Instrument other = makeBill("co", "SB0007", LOCAL_CLAUSE, LocalDate.of(2025, 8, 1));
        for (Instrument instrument : List.of(earlier, later, other)) {
            String name = instrument.key().jurisdiction() + "_2025_"
                    + instrument.key().number() + ".txt";
            Files.writeString(tmp.resolve(name), instrument.rawText(), StandardCharsets.UTF_8);
        }
        return tmp;
    }

    private RunResult run() throws IOException {
        return new Engine().run(Corpus.discover(corpusDir()), FIXED);
    }

    @Test
    void aRunIndexesAndMatchesInOrder() throws IOException {
        RunResult result = run();
        assertThat(result.instrumentCount()).isEqualTo(3);
        assertThat(result.indexedSegments()).isPositive();
        assertThat(result.matchCount()).isPositive();
    }

    @Test
    void theFirstInstrumentMatchesNothing() throws IOException {
        assertThat(run().reports().getFirst().isEmpty()).isTrue();
    }

    @Test
    void aLaterCopyIsReported() throws IOException {
        InstrumentReport report = run().reportFor("wy/2025/lower/HB0142").orElseThrow();
        assertThat(report.isEmpty()).isFalse();
        assertThat(report.matches().getFirst().otherKey().jurisdiction()).isEqualTo("mt");
    }

    @Test
    void reportsQuoteTheOtherDocument() throws IOException {
        List<ReportedPassage> quoted = run().reports().stream()
                .flatMap(report -> report.matches().stream())
                .flatMap(match -> match.passages().stream())
                .toList();
        assertThat(quoted).isNotEmpty().allSatisfy(passage -> assertThat(passage.resolved())
                .isTrue());
    }

    @Test
    void noInstrumentMatchesItself() throws IOException {
        for (InstrumentReport report : run().reports()) {
            assertThat(report.matches())
                    .allSatisfy(match -> assertThat(match.otherKey()).isNotEqualTo(report.key()));
        }
    }

    @Test
    void theGraphIsDatedAndDirected() throws IOException {
        RunResult result = run();
        assertThat(result.graph().size()).isGreaterThanOrEqualTo(1);
        ReuseEdge edge = result.graph().edges().getFirst();
        assertThat(edge.directed()).isTrue();
        assertThat(result.graph().dateOf(edge.source())).isPresent();
    }

    @Test
    void anUnrelatedBillProducesAnEmptyReport() throws IOException {
        assertThat(run().reportFor("co/2025/lower/SB0007").orElseThrow().isEmpty())
                .isTrue();
    }

    @Test
    void reportForAnUnknownSlugIsNone() throws IOException {
        assertThat(run().reportFor("nd/2025/lower/HB0001")).isEmpty();
    }

    @Test
    void boilerplateCanBeLearnedAfterIndexing() throws IOException {
        Engine engine = new Engine();
        engine.run(Corpus.discover(corpusDir()), FIXED);
        assertThat(engine.learnBoilerplate(2)).isGreaterThanOrEqualTo(0);
        assertThat(engine.matcher().boilerplate()).isSameAs(engine.boilerplate());
    }

    @Test
    void addingOneInstrumentAtATime() {
        Engine engine = new Engine();
        assertThat(engine.add(makeBill("wy", "HB0142", MODEL_CLAUSE))).isPositive();
        Engine.Analysis analysis = engine.analyse(makeBill("mt", "HB0311", MODEL_CLAUSE), FIXED);
        assertThat(analysis.matches()).isNotEmpty();
        assertThat(analysis.report().isEmpty()).isFalse();
    }

    @Test
    void runOverAPreparedSequence() {
        RunResult result = new Engine()
                .runOver(
                        List.of(makeBill("wy", "HB0142", MODEL_CLAUSE), makeBill("mt", "HB0311", MODEL_CLAUSE)), FIXED);
        assertThat(result.instrumentCount()).isEqualTo(2);
        assertThat(result.matchCount()).isPositive();
    }

    @Test
    void anEmptyCorpusRunsClean() {
        RunResult result = new Engine().run(Corpus.discover(tmp), FIXED);
        assertThat(result.instrumentCount()).isZero();
        assertThat(result.matchCount()).isZero();
        assertThat(result.graph().size()).isZero();
    }
}
