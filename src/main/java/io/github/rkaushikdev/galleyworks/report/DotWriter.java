package io.github.rkaushikdev.galleyworks.report;

import io.github.rkaushikdev.galleyworks.InstrumentKey;
import io.github.rkaushikdev.galleyworks.diffusion.ReuseEdge;
import io.github.rkaushikdev.galleyworks.diffusion.ReuseGraph;
import io.github.rkaushikdev.galleyworks.util.Floats;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Graphviz DOT output for a reuse graph.
 *
 * <p>A diffusion finding is a shape before it is a table: one act at the top and a fan of later
 * ones below it reads instantly. Nodes are grouped into a subgraph per jurisdiction, because the
 * question the picture answers is almost always "where did this cross a state line", and undirected
 * edges are drawn dashed, so an edge the dates could not orient is visibly a weaker claim.
 */
public final class DotWriter {

    private static final double MIN_PEN = 1.0;
    private static final double MAX_PEN = 6.0;

    private DotWriter() {}

    /** A DOT-safe identifier for an instrument. */
    public static String nodeId(InstrumentKey key) {
        return "\"" + key.slug().replace("\"", "'") + "\"";
    }

    private static double penWidth(ReuseEdge edge) {
        double weight = Math.max(0.0, Math.min(1.0, edge.weight()));
        return Floats.round(MIN_PEN + weight * (MAX_PEN - MIN_PEN), 2);
    }

    /** The graph as DOT source named {@code reuse}. */
    public static String toDot(ReuseGraph graph) {
        return toDot(graph, "reuse");
    }

    /** The graph as DOT source. */
    public static String toDot(ReuseGraph graph, String name) {
        List<String> lines = new ArrayList<>(List.of(
                "digraph " + name + " {",
                "  rankdir=TB;",
                "  node [shape=box, fontname=\"Helvetica\", fontsize=10];",
                "  edge [fontname=\"Helvetica\", fontsize=8];"));
        Map<String, List<InstrumentKey>> grouped = new TreeMap<>();
        for (InstrumentKey member : graph.nodes()) {
            grouped.computeIfAbsent(member.jurisdiction(), ignored -> new ArrayList<>())
                    .add(member);
        }
        grouped.forEach((jurisdiction, nodes) -> {
            lines.add("  subgraph cluster_" + jurisdiction + " {");
            lines.add("    label=\"" + jurisdiction + "\";");
            lines.add("    style=dotted;");
            for (InstrumentKey member : nodes) {
                String suffix = graph.dateOf(member).map(when -> "\\n" + when).orElse("");
                lines.add("    " + nodeId(member) + " [label=\"" + member.number() + suffix + "\"];");
            }
            lines.add("  }");
        });
        for (ReuseEdge edge : graph.edges()) {
            String style = edge.directed() ? "solid" : "dashed";
            String arrow = edge.directed() ? "normal" : "none";
            lines.add("  " + nodeId(edge.source()) + " -> " + nodeId(edge.target()) + " [label=\"" + edge.characters()
                    + " chars / " + edge.passages() + "p\", penwidth=" + Floats.repr(penWidth(edge)) + ", style="
                    + style + ", arrowhead=" + arrow + "];");
        }
        lines.add("}");
        return String.join("\n", lines) + "\n";
    }

    /** Write DOT source to {@code stream}. */
    public static void writeDot(ReuseGraph graph, Writer stream, String name) {
        try {
            stream.write(toDot(graph, name));
        } catch (IOException exc) {
            throw new UncheckedIOException(exc);
        }
    }
}
