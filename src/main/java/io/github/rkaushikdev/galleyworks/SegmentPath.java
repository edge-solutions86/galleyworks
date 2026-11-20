package io.github.rkaushikdev.galleyworks;

import java.util.ArrayList;
import java.util.List;

/**
 * The printed label chain that locates a segment, such as {@code 3(a)(ii)}. Paths order label by
 * label, a shorter prefix first.
 *
 * @param labels the labels from the outermost inward
 */
public record SegmentPath(List<String> labels) implements Comparable<SegmentPath> {

    /** The path of a segment with no label. */
    public static final SegmentPath ROOT = new SegmentPath(List.of());

    /** Copies the labels. */
    public SegmentPath {
        labels = List.copyOf(labels);
    }

    /** A path made of {@code labels}. */
    public static SegmentPath of(String... labels) {
        return new SegmentPath(List.of(labels));
    }

    /** This path extended by one label, which must not be blank. */
    public SegmentPath child(String label) {
        String cleaned = label.strip();
        if (cleaned.isEmpty()) {
            throw new IngestError("segment label is empty", Context.of("path", dotted()));
        }
        List<String> extended = new ArrayList<>(labels);
        extended.add(cleaned);
        return new SegmentPath(extended);
    }

    /** Number of labels. */
    public int depth() {
        return labels.size();
    }

    /** {@code 3.a.ii}. */
    public String dotted() {
        return String.join(".", labels);
    }

    /** {@code 3(a)(ii)}. */
    public String printed() {
        if (labels.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(labels.getFirst());
        for (String part : labels.subList(1, labels.size())) {
            out.append('(').append(part).append(')');
        }
        return out.toString();
    }

    /** True when this path is a strict prefix of {@code other}. */
    public boolean isAncestorOf(SegmentPath other) {
        return labels.size() < other.labels.size()
                && other.labels.subList(0, labels.size()).equals(labels);
    }

    @Override
    public int compareTo(SegmentPath other) {
        int shared = Math.min(labels.size(), other.labels.size());
        for (int i = 0; i < shared; i++) {
            int order = labels.get(i).compareTo(other.labels.get(i));
            if (order != 0) {
                return order;
            }
        }
        return Integer.compare(labels.size(), other.labels.size());
    }

    @Override
    public String toString() {
        return printed();
    }
}
