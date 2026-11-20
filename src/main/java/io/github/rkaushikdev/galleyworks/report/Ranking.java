package io.github.rkaushikdev.galleyworks.report;

import io.github.rkaushikdev.galleyworks.ConfigError;
import io.github.rkaushikdev.galleyworks.Context;
import io.github.rkaushikdev.galleyworks.ReportConfig;
import io.github.rkaushikdev.galleyworks.match.Confidence;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ordering and filtering findings before they are printed.
 *
 * <p>A report with forty findings is not read; the first five are. The order is confidence, then
 * reused characters, then the slug: confidence first because a strong short finding is worth more
 * attention than a weak long one, and the slug last so two runs over the same corpus print the same
 * document.
 */
public final class Ranking {

    /** Where each confidence sorts, strongest first. */
    public static final Map<Confidence, Integer> CONFIDENCE_ORDER =
            Map.of(Confidence.STRONG, 0, Confidence.MODERATE, 1, Confidence.WEAK, 2);

    private static final Comparator<ReportedMatch> ORDER = Comparator.comparingInt(
                    (ReportedMatch match) -> CONFIDENCE_ORDER.get(match.confidence()))
            .thenComparing(Comparator.comparingInt(ReportedMatch::characters).reversed())
            .thenComparing(match -> match.otherKey().slug() + "#" + match.otherPath());

    private Ranking() {}

    /** Sort findings so the ones worth reading come first. */
    public static List<ReportedMatch> rank(List<ReportedMatch> matches) {
        List<ReportedMatch> ranked = new ArrayList<>(matches);
        ranked.sort(ORDER);
        return ranked;
    }

    /** {@code report} with its findings ranked. */
    public static InstrumentReport rankReport(InstrumentReport report) {
        return report.withMatches(rank(report.matches()));
    }

    /**
     * Rank and narrow a report without recomputing anything.
     *
     * <p>The summary is deliberately left alone. It describes the document, not the subset that
     * survived a display filter, and recomputing it here would make two reports of the same run
     * disagree about how much text was reused.
     */
    public static InstrumentReport filterReport(
            InstrumentReport report,
            Optional<Confidence> minConfidence,
            boolean crossingOnly,
            Optional<Integer> limit) {
        if (limit.isPresent() && limit.get() < 0) {
            throw new ConfigError("limit cannot be negative", Context.of("limit", limit.get()));
        }
        Optional<Integer> floor = minConfidence.map(CONFIDENCE_ORDER::get);
        List<ReportedMatch> kept = new ArrayList<>();
        for (ReportedMatch match : rank(report.matches())) {
            if (floor.isPresent() && CONFIDENCE_ORDER.get(match.confidence()) > floor.get()) {
                continue;
            }
            if (crossingOnly
                    && match.otherKey().jurisdiction().equals(report.key().jurisdiction())) {
                continue;
            }
            kept.add(match);
        }
        if (limit.isPresent() && kept.size() > limit.get()) {
            kept = new ArrayList<>(kept.subList(0, limit.get()));
        }
        return report.withMatches(kept);
    }

    /** Rank a report and apply no filter. */
    public static InstrumentReport filterReport(InstrumentReport report) {
        return filterReport(report, Optional.empty(), false, Optional.empty());
    }

    /** Narrow a report the way the configuration says to. */
    public static InstrumentReport applyReportConfig(InstrumentReport report, ReportConfig settings) {
        Confidence floor = Confidence.fromValue(settings.minConfidence());
        return filterReport(
                report,
                floor == Confidence.WEAK ? Optional.empty() : Optional.of(floor),
                settings.crossingOnly(),
                settings.top());
    }
}
