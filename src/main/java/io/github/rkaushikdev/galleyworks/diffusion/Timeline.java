package io.github.rkaushikdev.galleyworks.diffusion;

import io.github.rkaushikdev.galleyworks.InstrumentKey;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;

/**
 * Reading a family as a sequence, and noticing when it cannot be one.
 *
 * <p>A diffusion finding is a story about time: this text appeared here, then there. {@link
 * #timeline} lays that out in order with the reuse each step brought in. {@link #findCycles} is the
 * check that the story is tellable: direction comes from enactment dates, so a directed cycle means
 * two instruments each claim to precede the other, which can only happen if a date is wrong.
 */
public final class Timeline {

    private Timeline() {}

    /**
     * One instrument's place in a family's history.
     *
     * @param position its place in date order, from zero
     * @param key the instrument
     * @param when when it took effect, if known
     * @param inheritedCharacters reused characters arriving from other members
     * @param sources the members it took text from, sorted
     */
    public record TimelineStep(
            int position,
            InstrumentKey key,
            Optional<LocalDate> when,
            int inheritedCharacters,
            List<InstrumentKey> sources) {

        /** Copies the sources. */
        public TimelineStep {
            sources = List.copyOf(sources);
        }

        /** True when nothing in the family came before it. */
        public boolean isOrigin() {
            return sources.isEmpty();
        }

        /** Days after {@code origin} this step landed, when both are dated. */
        public OptionalLong daysAfter(TimelineStep origin) {
            if (when.isEmpty() || origin.when().isEmpty()) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(ChronoUnit.DAYS.between(origin.when().get(), when.get()));
        }
    }

    /**
     * Lay a family out in date order, earliest first. Undated members come last, in slug order,
     * because putting them anywhere in the sequence would assert something the corpus does not know.
     */
    public static List<TimelineStep> timeline(ReuseGraph graph, Lineage lineage) {
        List<InstrumentKey> known = new ArrayList<>();
        List<InstrumentKey> unknown = new ArrayList<>();
        for (InstrumentKey key : lineage.members()) {
            (graph.dateOf(key).isPresent() ? known : unknown).add(key);
        }
        known.sort(Comparator.comparing((InstrumentKey key) -> graph.dateOf(key).orElseThrow())
                .thenComparing(InstrumentKey::slug));
        unknown.sort(Comparator.comparing(InstrumentKey::slug));
        List<InstrumentKey> ordered = new ArrayList<>(known);
        ordered.addAll(unknown);
        Set<InstrumentKey> members = new HashSet<>(lineage.members());
        List<TimelineStep> steps = new ArrayList<>();
        for (int position = 0; position < ordered.size(); position++) {
            InstrumentKey key = ordered.get(position);
            List<ReuseEdge> incoming = graph.inEdges(key).stream()
                    .filter(edge -> members.contains(edge.source()))
                    .toList();
            List<InstrumentKey> sources =
                    new ArrayList<>(incoming.stream().map(ReuseEdge::source).toList());
            sources.sort(null);
            steps.add(new TimelineStep(
                    position,
                    key,
                    graph.dateOf(key),
                    incoming.stream().mapToInt(ReuseEdge::characters).sum(),
                    sources));
        }
        return steps;
    }

    /** Every directed cycle, each reported once rotated to its lowest slug, sorted. */
    public static List<List<String>> findCycles(ReuseGraph graph) {
        Map<String, List<String>> adjacency = new HashMap<>();
        for (InstrumentKey node : graph.nodes()) {
            adjacency.put(node.slug(), new ArrayList<>());
        }
        for (ReuseEdge edge : graph.edges()) {
            if (edge.directed()) {
                adjacency
                        .computeIfAbsent(edge.source().slug(), ignored -> new ArrayList<>())
                        .add(edge.target().slug());
            }
        }
        Set<List<String>> found = new HashSet<>();
        Map<String, Integer> colour = new HashMap<>();
        for (String node : new TreeSet<>(adjacency.keySet())) {
            if (colour.getOrDefault(node, 0) == 0) {
                walk(node, new ArrayList<>(), adjacency, colour, found);
            }
        }
        List<List<String>> sorted = new ArrayList<>(found);
        sorted.sort(Timeline::compareLists);
        return sorted;
    }

    private static void walk(
            String node,
            List<String> path,
            Map<String, List<String>> adjacency,
            Map<String, Integer> colour,
            Set<List<String>> found) {
        colour.put(node, 1);
        path.add(node);
        for (String next : new TreeSet<>(adjacency.getOrDefault(node, List.of()))) {
            int state = colour.getOrDefault(next, 0);
            if (state == 1) {
                List<String> cycle = path.subList(path.indexOf(next), path.size());
                String lowest = new TreeSet<>(cycle).first();
                int pivot = cycle.indexOf(lowest);
                List<String> rotated = new ArrayList<>(cycle.subList(pivot, cycle.size()));
                rotated.addAll(cycle.subList(0, pivot));
                found.add(List.copyOf(rotated));
            } else if (state == 0) {
                walk(next, path, adjacency, colour, found);
            }
        }
        path.removeLast();
        colour.put(node, 2);
    }

    private static int compareLists(List<String> left, List<String> right) {
        for (int i = 0; i < Math.min(left.size(), right.size()); i++) {
            int order = left.get(i).compareTo(right.get(i));
            if (order != 0) {
                return order;
            }
        }
        return Integer.compare(left.size(), right.size());
    }
}
