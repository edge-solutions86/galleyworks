package io.github.rkaushikdev.galleyworks.diffusion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.rkaushikdev.galleyworks.Chamber;
import io.github.rkaushikdev.galleyworks.InstrumentKey;
import io.github.rkaushikdev.galleyworks.MatchError;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReuseGraphTest {

    static final InstrumentKey WY = key("wy", "HB0142");
    static final InstrumentKey MT = key("mt", "HB0311");
    static final InstrumentKey ND = key("nd", "HB0044");

    static InstrumentKey key(String jurisdiction, String number) {
        return new InstrumentKey(jurisdiction, "2025", Chamber.LOWER, number);
    }

    private static ReuseEdge edge(InstrumentKey source, InstrumentKey target) {
        return new ReuseEdge(source, target, 1, 200, 0.5, true);
    }

    private static <T extends Comparable<T>> List<T> sorted(List<T> values) {
        List<T> copy = new ArrayList<>(values);
        copy.sort(null);
        return copy;
    }

    @Test
    void anEmptyGraph() {
        ReuseGraph graph = new ReuseGraph();
        assertThat(graph.size()).isZero();
        assertThat(graph.nodes()).isEmpty();
        assertThat(graph.totalWeight()).isEqualTo(0.0);
    }

    @Test
    void addingAnEdgeRegistersBothNodes() {
        ReuseGraph graph = new ReuseGraph();
        graph.add(edge(WY, MT));
        assertThat(graph.nodes()).isEqualTo(sorted(List.of(WY, MT)));
        assertThat(graph.size()).isEqualTo(1);
    }

    @Test
    void edgesBetweenTheSamePairMerge() {
        ReuseGraph graph = new ReuseGraph();
        graph.add(edge(WY, MT));
        graph.add(new ReuseEdge(WY, MT, 3, 600, 1.5, true));
        ReuseEdge merged = graph.edgeBetween(WY, MT).orElseThrow();
        assertThat(merged.passages()).isEqualTo(4);
        assertThat(merged.characters()).isEqualTo(800);
        assertThat(merged.weight()).isCloseTo(1.5, within(1e-9));
    }

    @Test
    void theTwoDirectionsAreSeparateEdges() {
        ReuseGraph graph = new ReuseGraph();
        graph.add(edge(WY, MT));
        graph.add(edge(MT, WY));
        assertThat(graph.size()).isEqualTo(2);
    }

    @Test
    void mergingUnrelatedEdgesIsRejected() {
        assertThatThrownBy(() -> edge(WY, MT).merged(edge(MT, ND)))
                .isInstanceOf(MatchError.class)
                .hasMessageContaining("different pairs");
    }

    @Test
    void mergingKeepsTheStrongestWeight() {
        ReuseEdge merged = new ReuseEdge(WY, MT, 1, 200, 0.4, true).merged(new ReuseEdge(WY, MT, 1, 200, 0.9, true));
        assertThat(merged.weight()).isCloseTo(0.9, within(1e-9));
        assertThat(merged.passages()).isEqualTo(2);
    }

    @Test
    void mergingKeepsUndirectedIfEitherSideIs() {
        assertThat(edge(WY, MT)
                        .merged(new ReuseEdge(WY, MT, 1, 200, 0.5, false))
                        .directed())
                .isFalse();
    }

    @Test
    void outAndInEdges() {
        ReuseGraph graph = new ReuseGraph();
        graph.add(edge(WY, MT));
        graph.add(edge(WY, ND));
        graph.add(edge(ND, MT));
        assertThat(graph.outEdges(WY).stream().map(ReuseEdge::target).toList()).isEqualTo(sorted(List.of(MT, ND)));
        assertThat(graph.inEdges(MT).stream().map(ReuseEdge::source).toList()).isEqualTo(sorted(List.of(WY, ND)));
    }

    @Test
    void neighboursIgnoreDirection() {
        ReuseGraph graph = new ReuseGraph();
        graph.add(edge(WY, MT));
        graph.add(edge(ND, WY));
        assertThat(graph.neighbours(WY)).isEqualTo(Set.of(MT, ND));
    }

    @Test
    void anAbsentEdgeIsNone() {
        assertThat(new ReuseGraph().edgeBetween(WY, MT)).isEmpty();
    }

    @Test
    void datesAreRecordedAndKept() {
        ReuseGraph graph = new ReuseGraph();
        graph.note(WY, Optional.of(LocalDate.of(2025, 3, 4)));
        graph.note(WY);
        assertThat(graph.dateOf(WY)).contains(LocalDate.of(2025, 3, 4));
        assertThat(graph.dateOf(MT)).isEmpty();
    }

    @Test
    void addingAnEdgeDoesNotEraseAKnownDate() {
        ReuseGraph graph = new ReuseGraph();
        graph.note(WY, Optional.of(LocalDate.of(2025, 3, 4)));
        graph.add(edge(WY, MT));
        assertThat(graph.dateOf(WY)).contains(LocalDate.of(2025, 3, 4));
    }

    @Test
    void iterationIsOrderedByPair() {
        ReuseGraph graph = new ReuseGraph();
        graph.add(edge(WY, MT));
        graph.add(edge(MT, ND));
        List<String> pairs = new ArrayList<>();
        graph.forEach(found -> pairs.add(String.join("|", found.pair())));
        assertThat(pairs).isEqualTo(sorted(pairs));
    }

    @Test
    void addAllCountsWhatItAdded() {
        ReuseGraph graph = new ReuseGraph();
        assertThat(graph.addAll(List.of(edge(WY, MT), edge(MT, ND)))).isEqualTo(2);
        assertThat(graph.totalWeight()).isCloseTo(1.0, within(1e-9));
    }
}
