package io.github.rkaushikdev.galleyworks.service;

import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.LOCAL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.galleyworks.ConfigError;
import io.github.rkaushikdev.galleyworks.Engine;
import io.github.rkaushikdev.galleyworks.IngestError;
import io.github.rkaushikdev.galleyworks.Lexdrift;
import io.github.rkaushikdev.galleyworks.LexdriftError;
import io.github.rkaushikdev.galleyworks.StoreError;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ServiceAppTest {

    private static TestClient client;

    @BeforeAll
    static void client() {
        Engine engine = new Engine();
        engine.add(makeBill("wy", "HB0142", MODEL_CLAUSE));
        client = TestClient.of(LexdriftService.create(engine, TokenPolicy.open()));
    }

    @AfterAll
    static void stop() {
        client.close();
    }

    private static Map<String, Object> queryBody(Object... overrides) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", makeBill("mt", "HB0311", MODEL_CLAUSE).rawText());
        body.put("jurisdiction", "mt");
        for (int i = 0; i + 1 < overrides.length; i += 2) {
            body.put((String) overrides[i], overrides[i + 1]);
        }
        return body;
    }

    @Test
    void healthReportsTheIndex() {
        JsonNode payload = client.get("/health").json();
        assertThat(payload.get("status").asText()).isEqualTo("ok");
        assertThat(payload.get("version").asText()).isEqualTo(Lexdrift.VERSION);
        assertThat(payload.get("instruments").asInt()).isEqualTo(1);
        assertThat(payload.get("segments").asInt()).isPositive();
        assertThat(payload.get("engine_digest").asText()).hasSize(16);
    }

    @Test
    void instrumentsAreListed() {
        JsonNode payload = client.get("/v1/instruments").json();
        assertThat(payload.get("total").asInt()).isEqualTo(1);
        assertThat(payload.at("/instruments/0/key").asText()).isEqualTo("wy/2025/lower/HB0142");
    }

    @Test
    void aCopiedBillIsReported() {
        TestClient.Answer response = client.postJson("/v1/query", queryBody());
        assertThat(response.status()).isEqualTo(200);
        JsonNode payload = response.json();
        assertThat(payload.get("instrument").asText()).isEqualTo("mt/2025/lower/HB0311");
        assertThat(payload.get("matches")).isNotEmpty();
        assertThat(payload.at("/matches/0/other_instrument").asText()).isEqualTo("wy/2025/lower/HB0142");
    }

    @Test
    void anOriginalBillReportsNothing() {
        String original = makeBill("co", "SB0007", LOCAL_CLAUSE).rawText();
        TestClient.Answer response = client.postJson("/v1/query", Map.of("text", original, "jurisdiction", "co"));
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().get("matches")).isEmpty();
    }

    @Test
    void textCanBeLeftOut() {
        JsonNode payload =
                client.postJson("/v1/query", queryBody("include_text", false)).json();
        assertThat(payload.at("/matches/0/passages/0/query_text").isNull()).isTrue();
    }

    @Test
    void theQueryDocumentIsNotIndexed() {
        client.postJson("/v1/query", queryBody());
        assertThat(client.get("/health").json().get("instruments").asInt()).isEqualTo(1);
    }

    @Test
    void aMatchCarriesItsConfidence() {
        JsonNode payload = client.postJson("/v1/query", queryBody()).json();
        assertThat(payload.at("/matches/0/confidence").asText()).isIn("weak", "moderate", "strong");
    }

    @Test
    void aDocumentWithNoIdentityIsA422() {
        TestClient.Answer response = client.postJson("/v1/query", Map.of("text", "AN ACT\n", "jurisdiction", "mt"));
        assertThat(response.status()).isEqualTo(422);
        assertThat(response.json().get("code").asText()).startsWith("lexdrift.ingest");
    }

    @Test
    void anUnknownReaderIsA415() {
        TestClient.Answer response = client.postJson("/v1/query", queryBody("format_name", "nonesuch"));
        assertThat(response.status()).isEqualTo(415);
        assertThat(response.json().get("code").asText()).isEqualTo("lexdrift.ingest.format");
    }

    @Test
    void aMalformedRequestIsRejected() {
        assertThat(client.postJson("/v1/query", Map.of("jurisdiction", "mt")).status())
                .isEqualTo(422);
        assertThat(client.postJson("/v1/query", Map.of("text", "", "jurisdiction", "mt"))
                        .status())
                .isEqualTo(422);
    }

    @Test
    void unknownFieldsAreRejected() {
        assertThat(client.postJson("/v1/query", queryBody("nonsense", 1)).status())
                .isEqualTo(422);
    }

    @Test
    void theErrorMapIsUsed() {
        assertThat(LexdriftError.httpStatusOf(new IngestError("x"))).isEqualTo(422);
        assertThat(LexdriftError.httpStatusOf(new StoreError("x"))).isEqualTo(503);
        assertThat(LexdriftError.httpStatusOf(new IllegalArgumentException("x")))
                .isEqualTo(500);
        assertThat(LexdriftError.httpStatusOf(new ConfigError("x"))).isEqualTo(400);
    }

    @Test
    void aRunningServiceBlocksUntilItIsClosed() throws InterruptedException {
        LexdriftService service = LexdriftService.create(new Engine(), TokenPolicy.open());
        LexdriftService.Running running = service.start("127.0.0.1", 0);
        assertThat(running.port()).isPositive();
        Thread closer = new Thread(running::close);
        closer.start();
        running.awaitClose();
        closer.join();
        assertThat(running.context().isActive()).isFalse();
    }

    @Test
    void theFactoryOnTheClassPathBuildsTheService() {
        assertThat(new LexdriftServiceFactory().create(new Engine())).isInstanceOf(LexdriftService.class);
    }

    @Test
    void theOpenapiDocumentIsServed() {
        JsonNode payload = client.get("/openapi.json").json();
        assertThat(payload.at("/info/title").asText()).isEqualTo("lexdrift");
        assertThat(payload.get("paths").has("/v1/query")).isTrue();
    }
}
