package io.github.rkaushikdev.galleyworks.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.Chamber;
import io.github.rkaushikdev.galleyworks.InstrumentKey;
import io.github.rkaushikdev.galleyworks.diffusion.ReuseEdge;
import io.github.rkaushikdev.galleyworks.diffusion.ReuseGraph;
import java.io.StringWriter;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DotWriterTest {

    private static final InstrumentKey WY = new InstrumentKey("wy", "2025", Chamber.LOWER, "HB0142");
    private static final InstrumentKey MT = new InstrumentKey("mt", "2025", Chamber.LOWER, "HB0311");
    private static final InstrumentKey MT2 = new InstrumentKey("mt", "2025", Chamber.UPPER, "SB0044");

    private static ReuseGraph graph() {
        return graph(true, true);
    }

    private static ReuseGraph graph(boolean directed, boolean dated) {
        ReuseGraph built = new ReuseGraph();
        built.add(new ReuseEdge(WY, MT, 3, 940, 0.75, directed));
        built.note(MT2);
        if (dated) {
            built.note(WY, Optional.of(LocalDate.of(2024, 7, 1)));
            built.note(MT, Optional.of(LocalDate.of(2025, 7, 1)));
        }
        return built;
    }

    private static ReuseGraph weighted(double weight) {
        ReuseGraph built = new ReuseGraph();
        built.add(new ReuseEdge(WY, MT, 0, 0, weight, true));
        return built;
    }

    @Test
    void theDocumentIsADigraph() {
        String rendered = DotWriter.toDot(graph());
        assertThat(rendered).startsWith("digraph reuse {");
        assertThat(rendered.stripTrailing()).endsWith("}");
    }

    @Test
    void theGraphCanBeNamed() {
        assertThat(DotWriter.toDot(graph(), "diffusion")).startsWith("digraph diffusion {");
    }

    @Test
    void nodeIdsAreQuoted() {
        assertThat(DotWriter.nodeId(WY)).isEqualTo("\"wy/2025/lower/HB0142\"");
    }

    @Test
    void everyNodeAppears() {
        String rendered = DotWriter.toDot(graph());
        for (InstrumentKey key : new InstrumentKey[] {WY, MT, MT2}) {
            assertThat(rendered).contains(DotWriter.nodeId(key));
        }
    }

    @Test
    void nodesAreGroupedByJurisdiction() {
        assertThat(DotWriter.toDot(graph())).contains("subgraph cluster_wy {").contains("subgraph cluster_mt {");
    }

    @Test
    void nodeLabelsCarryTheNumberAndDate() {
        assertThat(DotWriter.toDot(graph())).contains("label=\"HB0142\\n2024-07-01\"");
    }

    @Test
    void anUndatedNodeHasNoDateInItsLabel() {
        assertThat(DotWriter.toDot(graph(true, false))).contains("label=\"HB0142\"");
    }

    @Test
    void edgesCarryTheirSize() {
        assertThat(DotWriter.toDot(graph())).contains("label=\"940 chars / 3p\"");
    }

    @Test
    void aDirectedEdgeIsSolid() {
        assertThat(DotWriter.toDot(graph())).contains("style=solid").contains("arrowhead=normal");
    }

    @Test
    void anUndirectedEdgeIsDashed() {
        assertThat(DotWriter.toDot(graph(false, true))).contains("style=dashed").contains("arrowhead=none");
    }

    @Test
    void penWidthFollowsTheWeight() {
        assertThat(DotWriter.toDot(weighted(0.0))).contains("penwidth=1.0");
        assertThat(DotWriter.toDot(weighted(1.0))).contains("penwidth=6.0");
    }

    @Test
    void anEmptyGraphRenders() {
        String rendered = DotWriter.toDot(new ReuseGraph());
        assertThat(rendered).startsWith("digraph reuse {").doesNotContain("->");
    }

    @Test
    void writeDotSendsItToAStream() {
        StringWriter stream = new StringWriter();
        DotWriter.writeDot(graph(), stream, "reuse");
        assertThat(stream.toString()).isEqualTo(DotWriter.toDot(graph()));
    }
}
