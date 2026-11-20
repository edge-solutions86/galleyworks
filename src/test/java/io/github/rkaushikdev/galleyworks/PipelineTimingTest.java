package io.github.rkaushikdev.galleyworks;

import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.canon.CachingPipeline;
import io.github.rkaushikdev.galleyworks.canon.CanonPipeline;
import io.github.rkaushikdev.galleyworks.canon.Canonicalizer;
import io.github.rkaushikdev.galleyworks.canon.Profiles;
import io.github.rkaushikdev.galleyworks.fingerprint.Fingerprinter;
import io.github.rkaushikdev.galleyworks.fingerprint.SegmentFingerprint;
import io.github.rkaushikdev.galleyworks.util.Timing;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PipelineTimingTest {

    @TempDir
    Path tmp;

    private Path corpusDir(int count) throws IOException {
        for (int position = 0; position < count; position++) {
            String number = String.format("HB%04d", position);
            Files.writeString(
                    tmp.resolve("wy_2025_" + number + ".txt"),
                    makeBill("wy", number, MODEL_CLAUSE).rawText(),
                    StandardCharsets.UTF_8);
        }
        return tmp;
    }

    @Test
    void bothPipelinesSatisfyTheProtocol() {
        assertThat(Profiles.buildPipeline()).isInstanceOf(Canonicalizer.class);
        assertThat(new CachingPipeline(Profiles.buildPipeline())).isInstanceOf(Canonicalizer.class);
    }

    @Test
    void theFingerprinterCachesByDefault() {
        assertThat(new Fingerprinter().pipeline()).isInstanceOf(CachingPipeline.class);
    }

    @Test
    void aPlainPipelineCanBeSupplied() {
        assertThat(new Fingerprinter(new EngineConfig(), Profiles.buildPipeline()).pipeline())
                .isInstanceOf(CanonPipeline.class);
    }

    @Test
    void repeatedSectionsHitTheCache() {
        Fingerprinter fingerprinter = new Fingerprinter();
        fingerprinter.fingerprint(makeBill("wy", "HB0001", MODEL_CLAUSE));
        fingerprinter.fingerprint(makeBill("wy", "HB0001", MODEL_CLAUSE));
        assertThat(fingerprinter.pipeline()).isInstanceOfSatisfying(CachingPipeline.class, cache -> assertThat(
                        cache.stats().hits())
                .isPositive());
    }

    @Test
    void cachingDoesNotChangeTheFingerprints() {
        Instrument instrument = makeBill("wy", "HB0001", MODEL_CLAUSE);
        List<SegmentFingerprint> cached = new Fingerprinter().fingerprint(instrument);
        List<SegmentFingerprint> plain =
                new Fingerprinter(new EngineConfig(), Profiles.buildPipeline()).fingerprint(instrument);
        assertThat(cached.stream().map(SegmentFingerprint::signature).toList())
                .isEqualTo(plain.stream().map(SegmentFingerprint::signature).toList());
        assertThat(cached.stream().map(SegmentFingerprint::selected).toList())
                .isEqualTo(plain.stream().map(SegmentFingerprint::selected).toList());
    }

    @Test
    void aRunRecordsEveryStage() throws IOException {
        Engine engine = new Engine();
        RunResult result = engine.run(Corpus.discover(corpusDir(3)));
        Set<String> stages =
                engine.stopwatch().timings().stream().map(Timing::name).collect(Collectors.toSet());
        assertThat(stages).contains("ingest", "fingerprint", "index", "match", "report", "diffusion");
        assertThat(result.timings()).isNotEmpty();
        assertThat(result.timings().getLast()).startsWith("total:");
    }

    @Test
    void theStopwatchTotalsAreNonNegative() throws IOException {
        Engine engine = new Engine();
        engine.run(Corpus.discover(corpusDir(3)));
        assertThat(engine.stopwatch().total()).isGreaterThanOrEqualTo(0.0);
        assertThat(engine.stopwatch().timings())
                .allSatisfy(timing -> assertThat(timing.seconds()).isGreaterThanOrEqualTo(0.0));
    }

    @Test
    void callCountsFollowTheCorpusSize() throws IOException {
        Engine engine = new Engine();
        engine.run(Corpus.discover(corpusDir(4)));
        Map<String, Timing> byName =
                engine.stopwatch().timings().stream().collect(Collectors.toMap(Timing::name, Function.identity()));
        assertThat(byName.get("ingest").calls()).isEqualTo(1);
        assertThat(byName.get("fingerprint").calls()).isEqualTo(4);
        assertThat(byName.get("match").calls()).isEqualTo(4);
    }
}
