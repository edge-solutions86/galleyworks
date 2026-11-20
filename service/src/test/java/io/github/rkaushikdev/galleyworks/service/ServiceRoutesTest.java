package io.github.rkaushikdev.galleyworks.service;

import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static io.github.rkaushikdev.galleyworks.support.GoldenCorpus.goldenInstruments;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.galleyworks.Engine;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ServiceRoutesTest {

    private static TestClient client;

    @BeforeAll
    static void client() {
        Engine engine = new Engine();
        goldenInstruments().forEach(engine::add);
        client = TestClient.of(LexdriftService.create(engine, TokenPolicy.open()));
    }

    @AfterAll
    static void stop() {
        client.close();
    }

    private static List<String> keys(JsonNode payload) {
        List<String> keys = new ArrayList<>();
        payload.get("instruments").forEach(item -> keys.add(item.get("key").asText()));
        return keys;
    }

    @Test
    void theStateIsOnTheApplication() {
        ServiceState state = client.running().context().getBean(ServiceState.class);
        assertThat(state).isSameAs(client.service().state());
        assertThat(state.digest()).hasSize(16);
    }

    @Test
    void healthStillAnswers() {
        JsonNode payload = client.get("/health").json();
        assertThat(payload.get("status").asText()).isEqualTo("ok");
        assertThat(payload.get("instruments").asInt()).isEqualTo(6);
    }

    @Test
    void theListingIsPaginated() {
        JsonNode payload = client.get("/v1/instruments?limit=2").json();
        assertThat(payload.get("total").asInt()).isEqualTo(6);
        assertThat(payload.get("limit").asInt()).isEqualTo(2);
        assertThat(payload.get("offset").asInt()).isZero();
        assertThat(payload.get("instruments")).hasSize(2);
    }

    @Test
    void theSecondPageContinues() {
        Set<String> seen =
                new HashSet<>(keys(client.get("/v1/instruments?limit=2").json()));
        seen.addAll(keys(client.get("/v1/instruments?limit=2&offset=2").json()));
        assertThat(seen).hasSize(4);
    }

    @Test
    void anOffsetPastTheEndIsEmpty() {
        JsonNode payload = client.get("/v1/instruments?offset=99").json();
        assertThat(payload.get("instruments")).isEmpty();
        assertThat(payload.get("total").asInt()).isEqualTo(6);
    }

    @Test
    void theListingCanBeFiltered() {
        JsonNode payload = client.get("/v1/instruments?jurisdiction=wy").json();
        assertThat(payload.get("total").asInt()).isEqualTo(1);
        assertThat(payload.at("/instruments/0/jurisdiction").asText()).isEqualTo("wy");
    }

    @Test
    void instrumentRowsAreFullyNamed() {
        JsonNode row = client.get("/v1/instruments?jurisdiction=wy").json().at("/instruments/0");
        assertThat(row.get("key").asText()).isEqualTo("wy/2023/lower/HB0142");
        assertThat(row.get("chamber").asText()).isEqualTo("lower");
        assertThat(row.get("number").asText()).isEqualTo("HB0142");
    }

    @Test
    void aPageLargerThanTheCapIsRejected() {
        TestClient.Answer response = client.get("/v1/instruments?limit=" + (Routes.MAX_PAGE + 1));
        assertThat(response.status()).isEqualTo(422);
        assertThat(response.json().at("/detail/0/loc").toString()).isEqualTo("[\"query\",\"limit\"]");
        assertThat(response.json().at("/detail/0/type").asText()).isEqualTo("less_than_equal");
    }

    @Test
    void aNegativeOffsetIsRejected() {
        TestClient.Answer response = client.get("/v1/instruments?offset=-1");
        assertThat(response.status()).isEqualTo(422);
        assertThat(response.json().at("/detail/0/type").asText()).isEqualTo("greater_than_equal");
    }

    @Test
    void aQueryCarriesItsExplanation() {
        String copy = makeBill("mt", "HB0999", MODEL_CLAUSE).rawText();
        TestClient.Answer response = client.postJson("/v1/query", Map.of("text", copy, "jurisdiction", "mt"));
        assertThat(response.status()).isEqualTo(200);
        JsonNode matches = response.json().get("matches");
        assertThat(matches).allSatisfy(match -> {
            assertThat(match.get("confidence").asText()).isIn("weak", "moderate", "strong");
            assertThat(match.get("headline").asText()).isNotEmpty();
            assertThat(match.get("reasons")).isNotEmpty();
        });
    }

    @Test
    void aRealCopyIsReported() {
        TestClient.Answer response = client.postJson(
                "/v1/query", Map.of("text", goldenInstruments().get(1).rawText(), "jurisdiction", "mt"));
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().get("matches")).isNotEmpty();
    }

    @Test
    void theOpenapiDocumentListsTheRoutes() {
        Set<String> paths = new HashSet<>();
        client.get("/openapi.json").json().get("paths").fieldNames().forEachRemaining(paths::add);
        assertThat(paths)
                .containsExactlyInAnyOrder(
                        "/health",
                        "/v1/instruments",
                        "/v1/instruments/{jurisdiction}/{session}/{chamber}/{number}",
                        "/v1/query");
    }

    @Test
    void aBodyThatIsNotJsonIsRefused() {
        TestClient.Answer broken = client.post("/v1/query", "{\"text\": ", "application/json");
        assertThat(broken.status()).isEqualTo(422);
        assertThat(broken.json().at("/detail/0/type").asText()).isEqualTo("json_invalid");
        assertThat(broken.json().at("/detail/0/loc/0").asText()).isEqualTo("body");

        TestClient.Answer empty = client.post("/v1/query", "", "application/json");
        assertThat(empty.status()).isEqualTo(422);
        assertThat(empty.json().at("/detail/0/type").asText()).isEqualTo("missing");

        TestClient.Answer plain = client.post("/v1/query", "{\"text\": \"x\", \"jurisdiction\": \"wy\"}", "text/plain");
        assertThat(plain.status()).isEqualTo(422);
        assertThat(plain.json().at("/detail/0/type").asText()).isEqualTo("model_attributes_type");
    }

    @Test
    void aQueryParameterThatIsNotANumberIsRefused() {
        TestClient.Answer response = client.get("/v1/instruments?limit=many&offset=-3&jurisdiction=" + "x".repeat(33));
        assertThat(response.status()).isEqualTo(422);
        List<String> types = new ArrayList<>();
        response.json()
                .get("detail")
                .forEach(problem -> types.add(problem.get("type").asText()));
        assertThat(types).containsExactly("int_parsing", "greater_than_equal", "string_too_long");
    }

    @Test
    void theLastValueOfARepeatedParameterWins() {
        assertThat(client.get("/v1/instruments?limit=9&limit=1").json().get("instruments"))
                .hasSize(1);
    }

    @Test
    void aWrongMethodIsA405() {
        TestClient.Answer response = client.get("/v1/query");
        assertThat(response.status()).isEqualTo(405);
        assertThat(response.json().get("detail").asText()).isEqualTo("Method Not Allowed");
        assertThat(response.header("Allow")).contains("POST");
    }

    @Test
    void errorMappingStillApplies() {
        TestClient.Answer response = client.postJson("/v1/query", Map.of("text", "AN ACT\n", "jurisdiction", "mt"));
        assertThat(response.status()).isEqualTo(422);
        assertThat(response.json().get("code").asText()).startsWith("lexdrift.ingest");
    }
}
