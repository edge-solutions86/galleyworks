package io.github.rkaushikdev.galleyworks.fingerprint;

import io.github.rkaushikdev.galleyworks.Context;
import io.github.rkaushikdev.galleyworks.MatchError;
import java.util.ArrayList;
import java.util.List;

/**
 * Winnowing: keeping a fraction of the shingles without losing long matches.
 *
 * <p>Winnowing keeps the minimum hash of every window of {@code w} consecutive shingles, which
 * selects about {@code 2/(w+1)} of them while guaranteeing that any shared run of at least {@code
 * w + k - 1} tokens is still detected. Two details decide whether that guarantee holds: on a tie
 * the rightmost minimum is taken, so overlapping windows agree on the same occurrence in both
 * documents; and a hash already selected by the previous window is not recorded twice. Hashes
 * compare as unsigned values.
 */
public final class Winnow {

    private Winnow() {}

    /** Select the winnowed subset of {@code shingles}. */
    public static List<Shingle> winnow(List<Shingle> shingles, int window) {
        if (window < 1) {
            throw new MatchError("winnow window must be at least one", Context.of("window", window));
        }
        if (shingles.isEmpty()) {
            return List.of();
        }
        if (window == 1) {
            return List.copyOf(shingles);
        }
        List<Shingle> selected = new ArrayList<>();
        int lastPosition = -1;
        for (int end = window - 1; end < shingles.size(); end++) {
            int start = end - window + 1;
            int best = start;
            for (int index = start; index <= end; index++) {
                // "<=" takes the rightmost minimum, which is what makes two documents agree on
                // the same occurrence inside a shared run.
                if (Long.compareUnsigned(
                                shingles.get(index).value(), shingles.get(best).value())
                        <= 0) {
                    best = index;
                }
            }
            if (best != lastPosition) {
                selected.add(shingles.get(best));
                lastPosition = best;
            }
        }
        if (selected.isEmpty()) {
            Shingle lowest = shingles.getFirst();
            for (Shingle shingle : shingles.subList(0, Math.min(window, shingles.size()))) {
                if (Long.compareUnsigned(shingle.value(), lowest.value()) < 0) {
                    lowest = shingle;
                }
            }
            selected.add(lowest);
        }
        return List.copyOf(selected);
    }

    /** The fraction of shingles winnowing is expected to keep. */
    public static double expectedDensity(int window) {
        if (window < 1) {
            throw new MatchError("winnow window must be at least one", Context.of("window", window));
        }
        return 2.0 / (window + 1);
    }

    /** Shortest shared run this configuration is guaranteed to detect. */
    public static int guaranteeLength(int shingleWidth, int window) {
        if (shingleWidth < 1 || window < 1) {
            throw new MatchError(
                    "shingle width and winnow window must both be at least one",
                    Context.of("width", shingleWidth, "window", window));
        }
        return shingleWidth + window - 1;
    }
}
