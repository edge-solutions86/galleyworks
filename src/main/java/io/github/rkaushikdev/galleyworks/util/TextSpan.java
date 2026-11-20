package io.github.rkaushikdev.galleyworks.util;

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * A half-open {@code [start, end)} range of character offsets into one specific text.
 *
 * <p>Every offset in the engine is a character offset, and the canonicalizer rewrites text, so
 * spans have to be translatable between the raw source and the canonical form. Spans order by
 * start, then end.
 *
 * @param start the first offset inside the span
 * @param end the first offset past the span
 */
public record TextSpan(int start, int end) implements Comparable<TextSpan> {

    private static final Comparator<TextSpan> ORDER =
            Comparator.comparingInt(TextSpan::start).thenComparingInt(TextSpan::end);

    /** Validates the bounds. */
    public TextSpan {
        if (start < 0) {
            throw new IllegalArgumentException("span start must be non-negative, got " + start);
        }
        if (end < start) {
            throw new IllegalArgumentException("span end " + end + " precedes start " + start);
        }
    }

    /** Number of characters covered. */
    public int length() {
        return end - start;
    }

    /** True when the span covers no characters. */
    public boolean isEmpty() {
        return end == start;
    }

    /** True when {@code offset} falls inside the half-open range. */
    public boolean containsOffset(int offset) {
        return start <= offset && offset < end;
    }

    /** True when {@code other} lies entirely inside this span. */
    public boolean contains(TextSpan other) {
        return start <= other.start && other.end <= end;
    }

    /** True when the spans share at least one character. */
    public boolean overlaps(TextSpan other) {
        return start < other.end && other.start < end;
    }

    /** True when the spans overlap or sit immediately next to each other. */
    public boolean touches(TextSpan other) {
        return start <= other.end && other.start <= end;
    }

    /** The shared characters, or empty when the spans are disjoint. */
    public Optional<TextSpan> intersection(TextSpan other) {
        int lower = Math.max(start, other.start);
        int upper = Math.min(end, other.end);
        if (upper <= lower) {
            return Optional.empty();
        }
        return Optional.of(new TextSpan(lower, upper));
    }

    /** The smallest span covering both operands, gap included. */
    public TextSpan hull(TextSpan other) {
        return new TextSpan(Math.min(start, other.start), Math.max(end, other.end));
    }

    /** The same span moved by {@code delta} characters. */
    public TextSpan shift(int delta) {
        return new TextSpan(start + delta, end + delta);
    }

    /** The span cut down to {@code [lower, upper]}. */
    public TextSpan clamp(int lower, int upper) {
        if (upper < lower) {
            throw new IllegalArgumentException("clamp bounds inverted: [" + lower + ", " + upper + "]");
        }
        int clampedStart = Math.min(Math.max(start, lower), upper);
        int clampedEnd = Math.min(Math.max(end, lower), upper);
        return new TextSpan(clampedStart, clampedEnd);
    }

    /** The characters of {@code text} this span covers. */
    public String sliceOf(String text) {
        int length = text.length();
        int from = Math.min(start, length);
        int to = Math.min(end, length);
        return text.substring(from, to);
    }

    /** Every offset inside the span, in order. */
    public IntStream offsets() {
        return IntStream.range(start, end);
    }

    @Override
    public int compareTo(TextSpan other) {
        return ORDER.compare(this, other);
    }

    @Override
    public String toString() {
        return "TextSpan(" + start + ", " + end + ")";
    }
}
