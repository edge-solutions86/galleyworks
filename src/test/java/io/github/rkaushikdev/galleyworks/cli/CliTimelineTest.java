package io.github.rkaushikdev.galleyworks.cli;

import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_FINDINGS;
import static io.github.rkaushikdev.galleyworks.support.Cli.run;
import static io.github.rkaushikdev.galleyworks.support.GoldenCorpus.goldenInstruments;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.Engine;
import io.github.rkaushikdev.galleyworks.RunResult;
import io.github.rkaushikdev.galleyworks.diffusion.Lineage;
import io.github.rkaushikdev.galleyworks.diffusion.ReuseGraph;
import io.github.rkaushikdev.galleyworks.support.Cli;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliTimelineTest {

    @TempDir
    Path tmp;

    private record Family(ReuseGraph graph, Lineage lineage) {}

    private static Family stateFamily() {
        RunResult result = new Engine().runOver(goldenInstruments());
        Lineage family = Lineage.lineages(result.graph()).stream()
                .filter(lineage -> lineage.size() == 3)
                .findFirst()
                .orElseThrow();
        return new Family(result.graph(), family);
    }

    private static List<String> rendered() {
        Family family = stateFamily();
        return DiffusionCommand.renderTimeline(family.graph(), family.lineage());
    }

    @Test
    void theTimelineListsEveryMember() {
        Family family = stateFamily();
        List<String> lines = DiffusionCommand.renderTimeline(family.graph(), family.lineage());
        assertThat(lines).hasSize(family.lineage().size() + 1);
        assertThat(lines.getFirst().strip()).isEqualTo("timeline:");
    }

    @Test
    void theFirstMemberIsTheOrigin() {
        assertThat(rendered().get(1)).contains("origin");
    }

    @Test
    void laterMembersReportTheirOffset() {
        String body = String.join("\n", rendered());
        assertThat(body).contains("days later").contains("characters from");
    }

    @Test
    void theDatesRunForward() {
        List<String> dates = rendered().subList(1, rendered().size()).stream()
                .map(line -> line.strip().split("\\s+", -1)[0])
                .toList();
        assertThat(dates).isSorted();
    }

    @Test
    void theCommandPrintsATimeline() {
        Cli.Result result = run("diffusion", "--timeline", Cli.goldenCorpus(tmp).toString());
        assertThat(result.code()).isEqualTo(EXIT_FINDINGS);
        assertThat(result.output()).contains("timeline:").contains("origin");
    }

    @Test
    void theTimelineIsOffByDefault() {
        assertThat(run("diffusion", Cli.goldenCorpus(tmp).toString()).output()).doesNotContain("timeline:");
    }

    @Test
    void aCleanCorpusReportsNoCycles() {
        assertThat(run("diffusion", Cli.goldenCorpus(tmp).toString()).output()).doesNotContain("dates disagree");
    }
}
