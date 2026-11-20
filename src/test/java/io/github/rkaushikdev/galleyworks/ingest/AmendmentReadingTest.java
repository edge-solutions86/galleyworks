package io.github.rkaushikdev.galleyworks.ingest;

import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_OK;
import static io.github.rkaushikdev.galleyworks.support.Cli.run;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.galleyworks.Engine;
import io.github.rkaushikdev.galleyworks.EngineConfig;
import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.cli.Common;
import io.github.rkaushikdev.galleyworks.support.Cli;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AmendmentReadingTest {

    private static final String DRAFT = "AN ACT relating to water rights.\n\n"
            + "Be It Enacted by the Legislature:\n\n"
            + "Section 1.  W.S. 41-3-101 is amended to read:\n\n"
            + "41-3-101.  Schedule of appropriations.\n\n"
            + "(a)  The board shall publish a schedule not later than "
            + "{-sixty (60)-}{+ninety (90)+}\ndays after the close of each irrigation season.\n";

    @TempDir
    Path tmp;

    private static EngineConfig current() {
        EngineConfig defaults = new EngineConfig();
        return defaults.withIngest(defaults.ingest().withAmendmentReading("current"));
    }

    @Test
    void theDefaultReadingIsProposed() {
        assertThat(new EngineConfig().ingest().amendmentReading()).isEqualTo("proposed");
    }

    @Test
    void onlyOneAmendmentReaderIsRegistered() {
        assertThat(ReaderRegistry.defaultRegistry().names())
                .containsOnlyOnce("amendment-proposed")
                .doesNotContain("amendment-current");
    }

    @Test
    void theRegistryCanBeBuiltForEitherReading() {
        assertThat(ReaderRegistry.defaultRegistry(Reading.CURRENT).names()).contains("amendment-current");
        assertThat(ReaderRegistry.defaultRegistry("current").names()).contains("amendment-current");
    }

    @Test
    void theEngineUsesTheConfiguredReading() {
        assertThat(new Engine(current()).registry().names()).contains("amendment-current");
    }

    @Test
    void theReadingIsPartOfTheIndexDigest() {
        assertThat(new EngineConfig().digest()).isNotEqualTo(current().digest());
    }

    @Test
    void anUnknownReadingIsRejected() {
        assertThatThrownBy(() -> new EngineConfig().ingest().withAmendmentReading("sideways"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theTwoReadingsParseDifferentText() {
        Path path = Cli.write(tmp.resolve("draft.txt"), DRAFT);
        SourceDocument document =
                SourceDocument.fromPath(path, Map.of("jurisdiction", "wy", "session", "2025", "number", "HB0142"));
        Instrument proposed = ReaderRegistry.defaultRegistry("proposed").read(document);
        Instrument current = ReaderRegistry.defaultRegistry("current").read(document);
        assertThat(proposed.sourceFormat()).isEqualTo("amendment-proposed");
        assertThat(current.sourceFormat()).isEqualTo("amendment-current");
        assertThat(proposed.rawText()).isEqualTo(current.rawText());
    }

    @Test
    void theFlagReachesTheConfig() {
        assertThat(Common.buildConfig(Optional.empty(), Optional.empty(), Optional.of("current"))
                        .ingest()
                        .amendmentReading())
                .isEqualTo("current");
        assertThat(Common.buildConfig(Optional.empty(), Optional.empty(), Optional.empty())
                        .ingest()
                        .amendmentReading())
                .isEqualTo("proposed");
    }

    @Test
    void theConfigCommandReportsTheReading() {
        assertThat(run("config", "--reading", "current").output()).contains("amendment reading: current");
    }

    @Test
    void theIndexCommandTakesTheFlag() {
        Path root = Cli.mkdir(tmp.resolve("bills"));
        Cli.write(root.resolve("wy_2025_HB0142.txt"), DRAFT);
        Cli.Result result = run("index", "--reading", "current", "--stats", root.toString());
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(result.output()).contains("1 instruments");
    }

    @Test
    void aPlainBillIsUnaffectedByTheReading() {
        Path root = Cli.mkdir(tmp.resolve("bills"));
        Cli.writeBill2025(root, makeBill("wy", "HB0142", MODEL_CLAUSE));
        String proposed = run("index", "--stats", root.toString()).output();
        String current =
                run("index", "--reading", "current", "--stats", root.toString()).output();
        assertThat(proposed).contains("1 instruments, ");
        assertThat(proposed.split("engine digest", -1)[0]).isEqualTo(current.split("engine digest", -1)[0]);
    }
}
