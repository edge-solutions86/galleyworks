package io.github.rkaushikdev.galleyworks.diffusion;

import io.github.rkaushikdev.galleyworks.Context;
import io.github.rkaushikdev.galleyworks.InstrumentKey;
import io.github.rkaushikdev.galleyworks.MatchError;
import java.util.List;

/**
 * Accumulated reuse from {@code source} into {@code target}.
 *
 * <p>Counts accumulate, weight does not: ten passages copied out of one act are one edge with ten
 * passages behind it, and the weight stays the strongest containment seen on the pair, so it keeps
 * its meaning as a fraction.
 *
 * @param source the instrument the text came from
 * @param target the instrument it appears in
 * @param passages passages behind the edge
 * @param characters reused characters behind the edge
 * @param weight the strongest containment seen on the pair
 * @param directed whether the dates settled which instrument came first
 */
public record ReuseEdge(
        InstrumentKey source, InstrumentKey target, int passages, int characters, double weight, boolean directed) {

    /** A directed edge with nothing behind it yet. */
    public ReuseEdge(InstrumentKey source, InstrumentKey target) {
        this(source, target, 0, 0, 0.0, true);
    }

    /** The pair of slugs the edge joins, source first. */
    public List<String> pair() {
        return List.of(source.slug(), target.slug());
    }

    /** This edge with {@code other}'s counts folded in. */
    public ReuseEdge merged(ReuseEdge other) {
        if (!pair().equals(other.pair())) {
            throw new MatchError(
                    "cannot merge edges between different pairs",
                    Context.of("pair", "('" + source.slug() + "', '" + target.slug() + "')"));
        }
        return new ReuseEdge(
                source,
                target,
                passages + other.passages,
                characters + other.characters,
                Math.max(weight, other.weight),
                directed && other.directed);
    }
}
