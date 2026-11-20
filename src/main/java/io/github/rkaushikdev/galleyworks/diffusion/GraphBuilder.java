package io.github.rkaushikdev.galleyworks.diffusion;

import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.InstrumentKey;
import io.github.rkaushikdev.galleyworks.match.Passage;
import io.github.rkaushikdev.galleyworks.match.SegmentMatch;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turning matches into a dated, directed reuse graph.
 *
 * <p>Direction comes from the calendar and nothing else. A match says two instruments share text;
 * it cannot say which one wrote it, and inferring direction from length or score would be inventing
 * evidence. When both instruments carry an effective date the earlier one is the source; otherwise
 * the edge is undirected with a stable source chosen by slug, so a run over the same corpus
 * produces the same graph.
 */
public final class GraphBuilder {

    private GraphBuilder() {}

    /**
     * One pair, oriented.
     *
     * @param source the instrument the edge leaves
     * @param target the instrument the edge reaches
     * @param directed whether dates settled the order
     */
    public record Orientation(InstrumentKey source, InstrumentKey target, boolean directed) {}

    /** The source, target and directedness of one pair under {@code dates}. */
    public static Orientation orient(InstrumentKey left, InstrumentKey right, Map<String, LocalDate> dates) {
        LocalDate leftDate = dates.get(left.slug());
        LocalDate rightDate = dates.get(right.slug());
        if (leftDate != null && rightDate != null && !leftDate.equals(rightDate)) {
            return leftDate.isBefore(rightDate)
                    ? new Orientation(left, right, true)
                    : new Orientation(right, left, true);
        }
        if (left.slug().compareTo(right.slug()) <= 0) {
            return new Orientation(left, right, false);
        }
        return new Orientation(right, left, false);
    }

    /** One edge from one match. */
    public static ReuseEdge edgeFor(SegmentMatch match, Map<String, LocalDate> dates) {
        Orientation oriented = orient(match.query().key(), match.other().key(), dates);
        int characters = match.passages().stream().mapToInt(Passage::length).sum();
        return new ReuseEdge(
                oriented.source(),
                oriented.target(),
                match.passages().size(),
                characters,
                match.score().bestContainment(),
                oriented.directed());
    }

    /** Fold every match into one graph with no dates. */
    public static ReuseGraph buildGraph(Iterable<SegmentMatch> matches) {
        return buildGraph(matches, List.of());
    }

    /** Fold every match into one graph, dated from {@code instruments}. */
    public static ReuseGraph buildGraph(Iterable<SegmentMatch> matches, Iterable<Instrument> instruments) {
        ReuseGraph graph = new ReuseGraph();
        Map<String, LocalDate> dates = new HashMap<>();
        for (Instrument instrument : instruments) {
            graph.note(instrument.key(), instrument.effectiveDate());
            instrument
                    .effectiveDate()
                    .ifPresent(when -> dates.put(instrument.key().slug(), when));
        }
        for (SegmentMatch match : matches) {
            graph.add(edgeFor(match, dates));
        }
        return graph;
    }
}
