package io.github.rkaushikdev.galleyworks.report;

import io.github.rkaushikdev.galleyworks.InstrumentKey;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Everything found about one instrument: the report a person actually reads.
 *
 * @param key the reported instrument
 * @param title its title
 * @param generatedAt when the report was built
 * @param summary totals for the document
 * @param matches the findings, in the order they are printed
 */
public record InstrumentReport(
        InstrumentKey key,
        String title,
        OffsetDateTime generatedAt,
        ReportSummary summary,
        List<ReportedMatch> matches) {

    /** Copies the findings. */
    public InstrumentReport {
        matches = List.copyOf(matches);
    }

    /** True when nothing was found. */
    public boolean isEmpty() {
        return matches.isEmpty();
    }

    /** This report with {@code kept} as its findings; the summary is unchanged. */
    public InstrumentReport withMatches(List<ReportedMatch> kept) {
        return new InstrumentReport(key, title, generatedAt, summary, kept);
    }
}
