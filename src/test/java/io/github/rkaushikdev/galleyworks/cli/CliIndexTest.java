package io.github.rkaushikdev.galleyworks.cli;

import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_ERROR;
import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_OK;
import static io.github.rkaushikdev.galleyworks.support.Cli.run;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.EngineConfig;
import io.github.rkaushikdev.galleyworks.index.SqliteIndex;
import io.github.rkaushikdev.galleyworks.support.Cli;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliIndexTest {

    @TempDir
    Path tmp;

    private String corpusDir() {
        Path root = Cli.mkdir(tmp.resolve("bills"));
        Cli.writeBill2025(root, makeBill("wy", "HB0142", MODEL_CLAUSE));
        Cli.writeBill2025(root, makeBill("mt", "HB0311", MODEL_CLAUSE));
        return root.toString();
    }

    @Test
    void indexingWritesADatabase() {
        Path index = tmp.resolve("index.sqlite");
        Cli.Result result = run("index", "--index", index.toString(), corpusDir());
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(index).exists();
        assertThat(result.output()).contains("2 instruments");
    }

    @Test
    void theDatabaseHoldsTheSegments() {
        Path index = tmp.resolve("index.sqlite");
        run("index", "--index", index.toString(), corpusDir());
        try (SqliteIndex store = new SqliteIndex(index)) {
            assertThat(store.size()).isPositive();
            assertThat(store.instruments()).hasSize(2);
        }
    }

    @Test
    void eachInstrumentIsNamed() {
        String output = run("index", "--index", tmp.resolve("i.sqlite").toString(), corpusDir())
                .output();
        assertThat(output).contains("indexed wy/2025/lower/HB0142").contains("indexed mt/2025/lower/HB0311");
    }

    @Test
    void indexingTwiceDoesNotDoubleTheIndex() {
        Path index = tmp.resolve("index.sqlite");
        String root = corpusDir();
        run("index", "--index", index.toString(), root);
        int first;
        try (SqliteIndex store = new SqliteIndex(index)) {
            first = store.size();
        }
        run("index", "--index", index.toString(), root);
        try (SqliteIndex store = new SqliteIndex(index)) {
            assertThat(store.size()).isEqualTo(first);
        }
    }

    @Test
    void replaceClearsBeforeWriting() {
        Path index = tmp.resolve("index.sqlite");
        String root = corpusDir();
        run("index", "--index", index.toString(), root);
        assertThat(run("index", "--replace", "--index", index.toString(), root).code())
                .isEqualTo(EXIT_OK);
        try (SqliteIndex store = new SqliteIndex(index)) {
            assertThat(store.instruments()).hasSize(2);
        }
    }

    @Test
    void statsArePrintedOnRequest() {
        String output = run(
                        "index", "--stats", "--index", tmp.resolve("i.sqlite").toString(), corpusDir())
                .output();
        assertThat(output).contains("index now holds 2 instruments").contains("engine digest");
    }

    @Test
    void anInMemoryIndexIsAllowed() {
        Cli.Result result = run("index", corpusDir());
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(result.output()).contains("2 instruments");
    }

    @Test
    void aProfileChangesTheDigest() {
        String root = corpusDir();
        String plain = run("index", "--stats", root).output();
        String verbatim = run("index", "--stats", "--profile", "verbatim", root).output();
        assertThat(plain).isNotEqualTo(verbatim);
    }

    @Test
    void anUnknownProfileIsAnError() {
        assertThat(run("index", "--profile", "nowhere", corpusDir()).code()).isEqualTo(EXIT_ERROR);
    }

    @Test
    void aConfigFileIsHonoured() {
        Path config = Cli.write(tmp.resolve("engine.yaml"), "shingle:\n  width: 7\n");
        String output = run("index", "--stats", "--config", config.toString(), corpusDir())
                .output();
        String expected =
                EngineConfig.fromMapping(Map.of("shingle", Map.of("width", 7))).digest();
        assertThat(output).contains(expected);
    }
}
