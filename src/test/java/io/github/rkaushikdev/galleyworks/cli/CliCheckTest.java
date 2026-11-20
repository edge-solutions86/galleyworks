package io.github.rkaushikdev.galleyworks.cli;

import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_ERROR;
import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_FINDINGS;
import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_OK;
import static io.github.rkaushikdev.galleyworks.support.Cli.run;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.index.Sql;
import io.github.rkaushikdev.galleyworks.index.SqliteIndex;
import io.github.rkaushikdev.galleyworks.support.Cli;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliCheckTest {

    @TempDir
    Path tmp;

    private String indexPath;

    @BeforeEach
    void index() {
        Path root = Cli.mkdir(tmp.resolve("bills"));
        Cli.writeBill2025(root, makeBill("wy", "HB0142", MODEL_CLAUSE));
        indexPath = tmp.resolve("index.sqlite").toString();
        run("index", "--index", indexPath, root.toString());
    }

    private void breakBands() {
        try (SqliteIndex store = new SqliteIndex(Path.of(indexPath))) {
            Sql.execute(store.connection(), "DELETE FROM band");
        }
    }

    @Test
    void aHealthyIndexPasses() {
        Cli.Result result = run("check", "--index", indexPath);
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(result.output()).contains("no problems found");
    }

    @Test
    void aBrokenIndexIsAFinding() {
        breakBands();
        Cli.Result result = run("check", "--index", indexPath);
        assertThat(result.code()).isEqualTo(EXIT_FINDINGS);
        assertThat(result.output()).contains("missing bands").contains("--repair");
    }

    @Test
    void repairFixesTheIndex() {
        breakBands();
        Cli.Result result = run("check", "--repair", "--index", indexPath);
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(result.output()).contains("rebuilt");
        assertThat(result.output().strip()).endsWith("no problems found");
    }

    @Test
    void repairOnAHealthyIndexIsHarmless() {
        Cli.Result result = run("check", "--repair", "--index", indexPath);
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(result.output()).contains("reclaimed");
    }

    @Test
    void theIndexStillAnswersAfterRepair() {
        breakBands();
        run("check", "--repair", "--index", indexPath);
        Path document = Cli.write(
                Path.of(indexPath).getParent().resolve("copy.txt"),
                makeBill("mt", "HB0311", MODEL_CLAUSE).rawText());
        Cli.Result result = run("query", "--index", indexPath, "--jurisdiction", "mt", document.toString());
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(result.output()).contains("wy/2025/lower/HB0142");
    }

    @Test
    void aMissingIndexIsAnError() {
        Cli.Result result = run(
                "check", "--index", tmp.resolve("missing").resolve("i.sqlite").toString());
        assertThat(result.code()).isEqualTo(EXIT_ERROR);
        assertThat(result.errors()).startsWith("lexdrift.store: ");
    }

    @Test
    void aMismatchedConfigIsAnError() {
        Path config = Cli.write(tmp.resolve("engine.yaml"), "shingle:\n  width: 9\n");
        assertThat(run("check", "--index", indexPath, "--config", config.toString())
                        .code())
                .isEqualTo(EXIT_ERROR);
    }
}
