package io.github.rkaushikdev.galleyworks.service;

import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_ERROR;
import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_OK;
import static io.github.rkaushikdev.galleyworks.support.Cli.run;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.cli.ServeCommand;
import io.github.rkaushikdev.galleyworks.support.Cli;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliServeTest {

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

    private ServeCommand.Built built() {
        return ServeCommand.buildService(
                Optional.of(Path.of(indexPath)), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static TestClient client(ServeCommand.Built built) {
        assertThat(built.app()).isInstanceOf(LexdriftService.class);
        return TestClient.of((LexdriftService) built.app());
    }

    @Test
    void checkLoadsTheIndexWithoutBinding() {
        Cli.Result result = run("serve", "--index", indexPath, "--check");
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(result.output()).contains("index loaded: 1 instruments").contains("check only, not binding");
    }

    @Test
    void checkReportsTheDigest() {
        assertThat(run("serve", "--index", indexPath, "--check").output()).contains("digest ");
    }

    @Test
    void aMissingIndexDirectoryIsAnError() {
        String missing = tmp.resolve("missing").resolve("i.sqlite").toString();
        assertThat(run("serve", "--index", missing, "--check").code()).isEqualTo(EXIT_ERROR);
    }

    @Test
    void theBuiltAppServesTheIndex() {
        ServeCommand.Built built = built();
        assertThat(built.engine().index().size()).isPositive();
        try (TestClient client = client(built)) {
            assertThat(client.get("/health").json().get("instruments").asInt()).isEqualTo(1);
        }
    }

    @Test
    void theBuiltAppAnswersAQuery() {
        String copy = makeBill("mt", "HB0311", MODEL_CLAUSE).rawText();
        try (TestClient client = client(built())) {
            TestClient.Answer response = client.postJson("/v1/query", Map.of("text", copy, "jurisdiction", "mt"));
            assertThat(response.status()).isEqualTo(200);
            assertThat(response.json().get("matches")).isNotEmpty();
        }
    }

    @Test
    void aProfileReachesTheService() {
        ServeCommand.Built built = ServeCommand.buildService(
                Optional.empty(), Optional.empty(), Optional.of("verbatim"), Optional.empty());
        assertThat(built.engine().config().canon().profile()).isEqualTo("verbatim");
    }

    @Test
    void anUnknownProfileIsAnError() {
        assertThat(run("serve", "--index", indexPath, "--profile", "nowhere", "--check")
                        .code())
                .isEqualTo(EXIT_ERROR);
    }
}
