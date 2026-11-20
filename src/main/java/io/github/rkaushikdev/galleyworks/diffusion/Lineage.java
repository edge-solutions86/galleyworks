package io.github.rkaushikdev.galleyworks.diffusion;

import io.github.rkaushikdev.galleyworks.InstrumentKey;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * One family of instruments sharing text, directly or through intermediaries, rooted at its
 * earliest dated member: the best available candidate for where the language originated.
 *
 * <p>"Best available" is doing real work: the earliest instrument in the corpus is only the
 * earliest one indexed, and a model bill drafted by an association that never appears in any
 * legislature will never be the root. {@link #confident()} says whether the dates support the claim
 * at all.
 *
 * @param root the earliest dated member, if any member is dated
 * @param members every member, sorted
 * @param rootDate when the root took effect
 * @param datedMembers members with a known date
 */
public record Lineage(
        Optional<InstrumentKey> root, List<InstrumentKey> members, Optional<LocalDate> rootDate, int datedMembers) {

    /** Copies the members. */
    public Lineage {
        members = List.copyOf(members);
    }

    /** Number of members. */
    public int size() {
        return members.size();
    }

    /** The members' jurisdictions, sorted and distinct. */
    public List<String> jurisdictions() {
        return List.copyOf(
                new TreeSet<>(members.stream().map(InstrumentKey::jurisdiction).toList()));
    }

    /** True when the family crosses more than one jurisdiction. */
    public boolean spreads() {
        return jurisdictions().size() > 1;
    }

    /** True only when every member is dated, so the root is not a guess. */
    public boolean confident() {
        return root.isPresent() && datedMembers == size();
    }

    /** Connected components of the graph, ignoring edge direction, largest first. */
    public static List<List<InstrumentKey>> components(ReuseGraph graph) {
        Set<String> seen = new HashSet<>();
        List<List<InstrumentKey>> found = new ArrayList<>();
        for (InstrumentKey start : graph.nodes()) {
            if (!seen.add(start.slug())) {
                continue;
            }
            Deque<InstrumentKey> stack = new ArrayDeque<>(List.of(start));
            List<InstrumentKey> member = new ArrayList<>();
            while (!stack.isEmpty()) {
                InstrumentKey current = stack.pop();
                member.add(current);
                for (InstrumentKey neighbour : graph.neighbours(current)) {
                    if (seen.add(neighbour.slug())) {
                        stack.push(neighbour);
                    }
                }
            }
            member.sort(null);
            found.add(member);
        }
        found.sort(Comparator.comparingInt((List<InstrumentKey> group) -> group.size())
                .reversed()
                .thenComparing(group -> group.getFirst().slug()));
        return found;
    }

    /** The earliest dated instrument among {@code members}, ties broken on the slug. */
    public static Optional<InstrumentKey> earliest(ReuseGraph graph, Iterable<InstrumentKey> members) {
        InstrumentKey best = null;
        LocalDate bestDate = null;
        for (InstrumentKey member : members) {
            Optional<LocalDate> when = graph.dateOf(member);
            if (when.isEmpty()) {
                continue;
            }
            int order = bestDate == null ? -1 : when.get().compareTo(bestDate);
            if (order < 0 || (order == 0 && member.slug().compareTo(best.slug()) < 0)) {
                best = member;
                bestDate = when.get();
            }
        }
        return Optional.ofNullable(best);
    }

    /** Split the graph into families, each with its earliest member as root. */
    public static List<Lineage> lineages(ReuseGraph graph) {
        List<Lineage> found = new ArrayList<>();
        for (List<InstrumentKey> group : components(graph)) {
            Optional<InstrumentKey> root = earliest(graph, group);
            int dated = (int) group.stream()
                    .filter(member -> graph.dateOf(member).isPresent())
                    .count();
            found.add(new Lineage(root, group, root.flatMap(graph::dateOf), dated));
        }
        return found;
    }
}
