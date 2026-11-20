package io.github.rkaushikdev.galleyworks.cli;

import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_ERROR;
import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_OK;
import static io.github.rkaushikdev.galleyworks.support.Cli.run;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.index.SqliteIndex;
import io.github.rkaushikdev.galleyworks.support.Cli;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliIndexIncrementalTest {

    @TempDir
    Path tmp;

    private Path corpus;
    private String index;

    @BeforeEach
    void corpus() {
        corpus = Cli.goldenCorpus(tmp);
        index = tmp.resolve("index.sqlite").toString();
    }

    private Cli.Result incremental() {
        return run("index", "--incremental", "--index", index, corpus.toString());
    }

    @Test
    void aFirstIncrementalRunIndexesEverything() {
        Cli.Result result = incremental();
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(result.output()).contains("0 unchanged, 0 changed, 6 new").contains("6 instruments");
    }

    @Test
    void aSecondRunIndexesNothing() {
        incremental();
        Cli.Result result = incremental();
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(result.output()).contains("6 unchanged").contains("0 instruments, 0 segments indexed");
    }

    @Test
    void onlyTheChangedDocumentIsReindexed() {
        incremental();
        Path target = corpus.resolve("mt_2024_HB0311.txt");
        Cli.write(target, Cli.read(target).replace("sixty (60)", "ninety (90)"));
        String output = incremental().output();
        assertThat(output)
                .contains("5 unchanged, 1 changed")
                .contains("indexed mt/2024/lower/HB0311")
                .doesNotContain("indexed wy/2023/lower/HB0142");
    }

    @Test
    void aNewDocumentIsPickedUp() {
        incremental();
        Cli.write(corpus.resolve("ut_2025_HB0500.txt"), Cli.read(corpus.resolve("wy_2023_HB0142.txt")));
        String output = incremental().output();
        assertThat(output).contains("1 new").contains("indexed ut/2025/lower/HB0500");
    }

    @Test
    void aRemovedDocumentIsReported() throws IOException {
        incremental();
        Files.delete(corpus.resolve("co_2025_SB0007.txt"));
        assertThat(incremental().output()).contains("1 no longer present");
    }

    @Test
    void theIndexStillAnswersAfterAnIncrementalRun() {
        incremental();
        incremental();
        try (SqliteIndex store = new SqliteIndex(Path.of(index))) {
            assertThat(store.instruments()).hasSize(6);
            assertThat(store.size()).isGreaterThan(4);
        }
    }

    @Test
    void incrementalNeedsADurableIndex() {
        Cli.Result result = run("index", "--incremental", corpus.toString());
        assertThat(result.code()).isEqualTo(EXIT_ERROR);
        assertThat(result.errors()).contains("incremental indexing needs a durable index");
    }

    @Test
    void aPlainRunStillWorks() {
        Cli.Result result = run("index", "--index", tmp.resolve("i.sqlite").toString(), corpus.toString());
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(result.output()).contains("6 instruments").doesNotContain("unchanged");
    }
}
