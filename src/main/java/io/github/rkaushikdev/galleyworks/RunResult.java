package io.github.rkaushikdev.galleyworks;

import io.github.rkaushikdev.galleyworks.diffusion.ReuseGraph;
import io.github.rkaushikdev.galleyworks.match.SegmentMatch;
import io.github.rkaushikdev.galleyworks.report.InstrumentReport;
import java.util.List;
import java.util.Optional;

/**
 * What one pass over a corpus produced.
 *
 * @param instruments every instrument, in corpus order
 * @param reports one report per instrument, in the same order
 * @param matches every match found, in the order found
 * @param graph the dated reuse graph over the corpus
 * @param indexedSegments segments written to the index
 * @param timings the stopwatch report, one line per stage and a total
 */
public record RunResult(
        List<Instrument> instruments,
        List<InstrumentReport> reports,
        List<SegmentMatch> matches,
        ReuseGraph graph,
        int indexedSegments,
        List<String> timings) {

    /** Copies the lists. */
    public RunResult {
        instruments = List.copyOf(instruments);
        reports = List.copyOf(reports);
        matches = List.copyOf(matches);
        timings = List.copyOf(timings);
    }

    /** Number of instruments. */
    public int instrumentCount() {
        return instruments.size();
    }

    /** Number of matches. */
    public int matchCount() {
        return matches.size();
    }

    /** The report about the instrument with {@code slug}. */
    public Optional<InstrumentReport> reportFor(String slug) {
        return reports.stream()
                .filter(report -> report.key().slug().equals(slug))
                .findFirst();
    }
}
