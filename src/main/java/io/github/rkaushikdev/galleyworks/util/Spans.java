package io.github.rkaushikdev.galleyworks.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** The arithmetic over collections of {@link TextSpan}s that the rest of the engine needs. */
public final class Spans {

    private Spans() {}

    /** Collapse overlapping spans. */
    public static List<TextSpan> merge(Collection<TextSpan> spans) {
        return merge(spans, 0);
    }

    /** Collapse overlapping spans, joining neighbours separated by at most {@code gap}. */
    public static List<TextSpan> merge(Collection<TextSpan> spans, int gap) {
        if (gap < 0) {
            throw new IllegalArgumentException("gap must be non-negative, got " + gap);
        }
        List<TextSpan> ordered = new ArrayList<>(spans);
        ordered.sort(null);
        List<TextSpan> merged = new ArrayList<>();
        for (TextSpan span : ordered) {
            if (!merged.isEmpty() && span.start() - merged.getLast().end() <= gap) {
                TextSpan last = merged.removeLast();
                merged.add(new TextSpan(last.start(), Math.max(last.end(), span.end())));
            } else {
                merged.add(span);
            }
        }
        return merged;
    }

    /** Total number of distinct characters covered by {@code spans}. */
    public static int coverage(Collection<TextSpan> spans) {
        return merge(spans).stream().mapToInt(TextSpan::length).sum();
    }

    /** Remove {@code holes} from {@code span}, returning the surviving pieces in order. */
    public static List<TextSpan> subtract(TextSpan span, Collection<TextSpan> holes) {
        List<TextSpan> pieces = new ArrayList<>();
        int cursor = span.start();
        for (TextSpan hole : merge(holes)) {
            if (hole.end() <= cursor) {
                continue;
            }
            if (hole.start() >= span.end()) {
                break;
            }
            if (hole.start() > cursor) {
                pieces.add(new TextSpan(cursor, Math.min(hole.start(), span.end())));
            }
            cursor = Math.max(cursor, hole.end());
        }
        if (cursor < span.end()) {
            pieces.add(new TextSpan(cursor, span.end()));
        }
        return pieces.stream().filter(piece -> !piece.isEmpty()).toList();
    }
}
