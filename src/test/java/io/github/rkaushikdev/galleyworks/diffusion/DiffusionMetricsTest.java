package io.github.rkaushikdev.galleyworks.diffusion;

import static io.github.rkaushikdev.galleyworks.diffusion.ReuseGraphTest.key;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.InstrumentKey;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DiffusionMetricsTest {

    private static final InstrumentKey A = key("wy", "HB0142");
    private static final InstrumentKey B = key("mt", "HB0311");
    private static final InstrumentKey C = key("nd", "HB0044");

    private static ReuseEdge link(InstrumentKey source, InstrumentKey target) {
        return link(source, target, true);
    }

    private static ReuseEdge link(InstrumentKey source, InstrumentKey target, boolean directed) {
        return new ReuseEdge(source, target, 1, 100, 0.7, directed);
    }

    private static ReuseGraph chain() {
        ReuseGraph graph = new ReuseGraph();
        graph.note(A, Optional.of(LocalDate.of(2023, 1, 1)));
        graph.note(B, Optional.of(LocalDate.of(2024, 1, 1)));
        graph.note(C, Optional.of(LocalDate.of(2025, 1, 1)));
        graph.add(link(A, B));
        graph.add(link(B, C));
        graph.add(link(A, C));
        return graph;
    }

    private static DiffusionMetrics measured(ReuseGraph graph) {
        return DiffusionMetrics.measure(graph, Lineage.lineages(graph).getFirst());
    }

    @Test
    void aShortcutEdgeIsRemoved() {
        Set<List<String>> pairs = new HashSet<>();
        DiffusionMetrics.transitiveReduction(chain()).forEach(edge -> pairs.add(edge.pair()));
        assertThat(pairs).isEqualTo(Set.of(List.of(A.slug(), B.slug()), List.of(B.slug(), C.slug())));
    }

    @Test
    void reductionKeepsTheNodesAndTheirDates() {
        ReuseGraph reduced = DiffusionMetrics.transitiveReduction(chain());
        assertThat(reduced.nodes()).isEqualTo(chain().nodes());
        assertThat(reduced.dateOf(A)).contains(LocalDate.of(2023, 1, 1));
    }

    @Test
    void anUndirectedEdgeIsNeverRemoved() {
        ReuseGraph graph = chain();
        graph.add(link(A, C, false));
        assertThat(DiffusionMetrics.transitiveReduction(graph).edgeBetween(A, C))
                .isPresent();
    }

    @Test
    void aReductionWithNothingToRemove() {
        ReuseGraph graph = new ReuseGraph();
        graph.add(link(A, B));
        graph.add(link(A, C));
        assertThat(DiffusionMetrics.transitiveReduction(graph).size()).isEqualTo(2);
    }

    @Test
    void reductionOfAnEmptyGraph() {
        assertThat(DiffusionMetrics.transitiveReduction(new ReuseGraph()).size())
                .isZero();
    }

    @Test
    void metricsCountMembersAndJurisdictions() {
        DiffusionMetrics metrics = measured(chain());
        assertThat(metrics.members()).isEqualTo(3);
        assertThat(metrics.jurisdictions()).isEqualTo(3);
        assertThat(metrics.edges()).isEqualTo(3);
        assertThat(metrics.directedEdges()).isEqualTo(3);
        assertThat(metrics.isSingleJurisdiction()).isFalse();
    }

    @Test
    void metricsSumTheReusedCharacters() {
        assertThat(measured(chain()).characters()).isEqualTo(300);
    }

    @Test
    void metricsSpanTheDates() {
        DiffusionMetrics metrics = measured(chain());
        assertThat(metrics.first()).contains(LocalDate.of(2023, 1, 1));
        assertThat(metrics.last()).contains(LocalDate.of(2025, 1, 1));
        assertThat(metrics.spanDays()).hasValue(731);
    }

    @Test
    void metricsOnAnUndatedFamily() {
        ReuseGraph graph = new ReuseGraph();
        graph.add(link(A, B));
        DiffusionMetrics metrics = measured(graph);
        assertThat(metrics.first()).isEmpty();
        assertThat(metrics.spanDays()).isEmpty();
    }

    @Test
    void aSingleJurisdictionFamilyIsFlagged() {
        ReuseGraph graph = new ReuseGraph();
        graph.add(link(key("wy", "HB0001"), key("wy", "HB0002")));
        assertThat(measured(graph).isSingleJurisdiction()).isTrue();
    }

    @Test
    void metricsIgnoreEdgesOutsideTheFamily() {
        ReuseGraph graph = chain();
        graph.add(link(key("ut", "HB0500"), key("ut", "HB0501")));
        List<Lineage> families = new ArrayList<>(Lineage.lineages(graph));
        families.sort(Comparator.comparingInt(Lineage::size).reversed());
        DiffusionMetrics metrics = DiffusionMetrics.measure(graph, families.getFirst());
        assertThat(metrics.members()).isEqualTo(3);
        assertThat(metrics.edges()).isEqualTo(3);
    }
}
