package io.github.rkaushikdev.galleyworks.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class TimingTest {

    @Test
    void aNewStopwatchIsEmpty() {
        Stopwatch watch = new Stopwatch();
        assertThat(watch.size()).isZero();
        assertThat(watch.total()).isEqualTo(0.0);
        assertThat(watch.timings()).isEmpty();
    }

    @Test
    void aTimedBlockIsRecorded() {
        Stopwatch watch = new Stopwatch();
        int sum = watch.stage("ingest", () -> IntStream.range(0, 1000).sum());
        assertThat(sum).isEqualTo(499500);
        assertThat(watch.contains("ingest")).isTrue();
        assertThat(watch.total()).isGreaterThanOrEqualTo(0.0);
        assertThat(watch.timings().getFirst().calls()).isEqualTo(1);
    }

    @Test
    void repeatedStagesAccumulate() {
        Stopwatch watch = new Stopwatch();
        for (int i = 0; i < 3; i++) {
            watch.stage("match", () -> {});
        }
        Timing timing = watch.timings().getFirst();
        assertThat(timing.calls()).isEqualTo(3);
        assertThat(timing.name()).isEqualTo("match");
    }

    @Test
    void aRaisingBlockIsStillRecorded() {
        Stopwatch watch = new Stopwatch();
        assertThatThrownBy(() -> watch.stage("index", () -> {
                    throw new IllegalArgumentException("boom");
                }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("boom");
        assertThat(watch.contains("index")).isTrue();
    }

    @Test
    void directRecordsAreAdded() {
        Stopwatch watch = new Stopwatch();
        watch.record("canon", 1.5);
        watch.record("canon", 0.5);
        assertThat(watch.total()).isCloseTo(2.0, within(1e-9));
        assertThat(watch.timings().getFirst().average()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void aNegativeRecordIsRejected() {
        assertThatThrownBy(() -> new Stopwatch().record("canon", -1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be negative");
    }

    @Test
    void stagesComeBackSlowestFirst() {
        Stopwatch watch = new Stopwatch();
        watch.record("fast", 0.1);
        watch.record("slow", 2.0);
        watch.record("middle", 1.0);
        assertThat(watch.timings().stream().map(Timing::name)).containsExactly("slow", "middle", "fast");
    }

    @Test
    void tiesBreakOnTheName() {
        Stopwatch watch = new Stopwatch();
        watch.record("b", 1.0);
        watch.record("a", 1.0);
        assertThat(watch.timings().stream().map(Timing::name)).containsExactly("a", "b");
    }

    @Test
    void theReportEndsWithATotal() {
        Stopwatch watch = new Stopwatch();
        watch.record("canon", 1.0);
        List<String> report = watch.report();
        assertThat(report.getFirst()).startsWith("canon: 1.000s over 1 calls");
        assertThat(report.getLast()).isEqualTo("total: 1.000s");
    }

    @Test
    void resetClearsEverything() {
        Stopwatch watch = new Stopwatch();
        watch.record("canon", 1.0);
        watch.reset();
        assertThat(watch.size()).isZero();
        assertThat(watch.total()).isEqualTo(0.0);
    }

    @Test
    void aTimingWithNoCallsAveragesZero() {
        assertThat(new Timing("x", 0.0, 0).average()).isEqualTo(0.0);
    }

    @Test
    void aTimingRendersItself() {
        assertThat(new Timing("canon", 0.5, 2)).hasToString("canon: 0.500s over 2 calls");
    }
}
