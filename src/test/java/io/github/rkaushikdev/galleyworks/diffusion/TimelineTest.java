package io.github.rkaushikdev.galleyworks.diffusion;

import static io.github.rkaushikdev.galleyworks.diffusion.ReuseGraphTest.key;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.Engine;
import io.github.rkaushikdev.galleyworks.InstrumentKey;
import io.github.rkaushikdev.galleyworks.RunResult;
import io.github.rkaushikdev.galleyworks.diffusion.Timeline.TimelineStep;
import io.github.rkaushikdev.galleyworks.support.GoldenCorpus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TimelineTest {

    private static final InstrumentKey A = key("wy", "HB0142");
    private static final InstrumentKey B = key("mt", "HB0311");
    private static final InstrumentKey C = key("nd", "HB0044");

    private static ReuseEdge link(InstrumentKey source, InstrumentKey target) {
        return link(source, target, true);
    }

    private static ReuseEdge link(InstrumentKey source, InstrumentKey target, boolean directed) {
        return new ReuseEdge(source, target, 1, 200, 0.8, directed);
    }

    private static ReuseGraph chain() {
        ReuseGraph graph = new ReuseGraph();
        graph.note(A, Optional.of(LocalDate.of(2023, 1, 1)));
        graph.note(B, Optional.of(LocalDate.of(2024, 1, 1)));
        graph.note(C, Optional.of(LocalDate.of(2025, 1, 1)));
        graph.add(link(A, B));
        graph.add(link(B, C));
        return graph;
    }

    private static List<TimelineStep> steps(ReuseGraph graph) {
        return Timeline.timeline(graph, Lineage.lineages(graph).getFirst());
    }

    @Test
    void aTimelineRunsInDateOrder() {
        List<TimelineStep> steps = steps(chain());
        assertThat(steps.stream().map(TimelineStep::key)).containsExactly(A, B, C);
        assertThat(steps.stream().map(TimelineStep::position)).containsExactly(0, 1, 2);
    }

    @Test
    void daysAfterMeasuresFromTheOrigin() {
        List<TimelineStep> steps = steps(chain());
        assertThat(steps.get(0).daysAfter(steps.get(0))).hasValue(0);
        assertThat(steps.get(1).daysAfter(steps.get(0))).hasValue(365);
    }

    @Test
    void daysAfterIsNoneWithoutDates() {
        ReuseGraph graph = new ReuseGraph();
        graph.add(link(A, B));
        List<TimelineStep> steps = steps(graph);
        assertThat(steps.get(0).daysAfter(steps.get(1))).isEmpty();
    }

    @Test
    void theFirstStepIsTheOrigin() {
        List<TimelineStep> steps = steps(chain());
        assertThat(steps.get(0).isOrigin()).isTrue();
        assertThat(steps.get(0).sources()).isEmpty();
        assertThat(steps.get(1).isOrigin()).isFalse();
    }

    @Test
    void laterStepsNameTheirSources() {
        List<TimelineStep> steps = steps(chain());
        assertThat(steps.get(1).sources()).containsExactly(A);
        assertThat(steps.get(1).inheritedCharacters()).isEqualTo(200);
    }

    @Test
    void undatedMembersComeLast() {
        ReuseGraph graph = chain();
        InstrumentKey stray = key("co", "SB0007");
        graph.add(link(C, stray));
        List<TimelineStep> steps = steps(graph);
        assertThat(steps.getLast().key()).isEqualTo(stray);
        assertThat(steps.getLast().when()).isEmpty();
    }

    @Test
    void aWhollyUndatedFamilyIsSlugOrdered() {
        ReuseGraph graph = new ReuseGraph();
        graph.add(link(B, A));
        List<String> slugs =
                steps(graph).stream().map(step -> step.key().slug()).toList();
        List<String> sorted = new ArrayList<>(slugs);
        sorted.sort(null);
        assertThat(slugs).isEqualTo(sorted);
    }

    @Test
    void anAcyclicGraphHasNoCycles() {
        assertThat(Timeline.findCycles(chain())).isEmpty();
    }

    @Test
    void aTwoNodeCycleIsFound() {
        ReuseGraph graph = chain();
        graph.add(link(C, A));
        List<List<String>> cycles = Timeline.findCycles(graph);
        assertThat(cycles).isNotEmpty();
        Set<String> members = new HashSet<>(cycles.getFirst());
        assertThat(members).isEqualTo(Set.of(A.slug(), B.slug(), C.slug()));
    }

    @Test
    void aCycleIsReportedOnce() {
        ReuseGraph graph = new ReuseGraph();
        graph.add(link(A, B));
        graph.add(link(B, A));
        assertThat(Timeline.findCycles(graph)).hasSize(1);
    }

    @Test
    void anUndirectedEdgeNeverMakesACycle() {
        ReuseGraph graph = new ReuseGraph();
        graph.add(link(A, B, false));
        graph.add(link(B, A, false));
        assertThat(Timeline.findCycles(graph)).isEmpty();
    }

    @Test
    void anEmptyGraphHasNoCycles() {
        assertThat(Timeline.findCycles(new ReuseGraph())).isEmpty();
    }

    @Test
    void theGoldenCorpusIsAcyclicAndOrdered() {
        RunResult result = new Engine().runOver(GoldenCorpus.goldenInstruments());
        assertThat(Timeline.findCycles(result.graph())).isEmpty();
        Lineage family = Lineage.lineages(result.graph()).stream()
                .filter(found -> found.size() > 1)
                .findFirst()
                .orElseThrow();
        List<TimelineStep> steps = Timeline.timeline(result.graph(), family);
        List<LocalDate> dates =
                steps.stream().flatMap(step -> step.when().stream()).toList();
        List<LocalDate> sorted = new ArrayList<>(dates);
        sorted.sort(null);
        assertThat(dates).isEqualTo(sorted);
        assertThat(steps.getFirst().key().jurisdiction()).isEqualTo("wy");
    }
}
