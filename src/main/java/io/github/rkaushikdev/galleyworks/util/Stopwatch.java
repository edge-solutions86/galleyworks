package io.github.rkaushikdev.galleyworks.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Accumulates elapsed wall-clock time per named pipeline stage.
 *
 * <p>A corpus pass has four stages and one of them is always the problem. Which one depends on the
 * corpus, so the pipeline carries a stopwatch rather than guessing. Wall clock, deliberately: the
 * interesting stalls are sqlite waits and page faults, and CPU time hides both.
 */
public final class Stopwatch {

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private final Map<String, Double> seconds = new LinkedHashMap<>();
    private final Map<String, Integer> calls = new LinkedHashMap<>();

    /** Time {@code block}, adding it to {@code name}; a block that throws is still recorded. */
    public void stage(String name, Runnable block) {
        long started = System.nanoTime();
        try {
            block.run();
        } finally {
            record(name, (System.nanoTime() - started) / NANOS_PER_SECOND);
        }
    }

    /** Time {@code block}, adding it to {@code name}, and return its value. */
    public <T> T stage(String name, Supplier<T> block) {
        long started = System.nanoTime();
        try {
            return block.get();
        } finally {
            record(name, (System.nanoTime() - started) / NANOS_PER_SECOND);
        }
    }

    /** Add one call of {@code elapsed} seconds to {@code name}. */
    public void record(String name, double elapsed) {
        if (elapsed < 0) {
            throw new IllegalArgumentException("elapsed time cannot be negative, got " + Floats.repr(elapsed));
        }
        seconds.merge(name, elapsed, Double::sum);
        calls.merge(name, 1, Integer::sum);
    }

    /** Number of distinct stages recorded. */
    public int size() {
        return seconds.size();
    }

    /** True when {@code name} has been recorded at least once. */
    public boolean contains(String name) {
        return seconds.containsKey(name);
    }

    /** Seconds across every stage. */
    public double total() {
        double sum = 0.0;
        for (double value : seconds.values()) {
            sum += value;
        }
        return sum;
    }

    /** Every stage, slowest first, ties broken on the name. */
    public List<Timing> timings() {
        List<Timing> found = new ArrayList<>();
        seconds.forEach((name, value) -> found.add(new Timing(name, value, calls.get(name))));
        found.sort(Comparator.comparingDouble(Timing::seconds).reversed().thenComparing(Timing::name));
        return found;
    }

    /** One line per stage, plus a total. */
    public List<String> report() {
        List<String> lines = new ArrayList<>();
        for (Timing timing : timings()) {
            lines.add(timing.toString());
        }
        lines.add("total: " + Floats.fixed(total(), 3) + "s");
        return lines;
    }

    /** Forget every stage. */
    public void reset() {
        seconds.clear();
        calls.clear();
    }
}
