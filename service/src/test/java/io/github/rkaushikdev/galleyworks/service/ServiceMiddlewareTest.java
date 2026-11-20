package io.github.rkaushikdev.galleyworks.service;

import static io.github.rkaushikdev.galleyworks.support.GoldenCorpus.goldenInstruments;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.Engine;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

class ServiceMiddlewareTest {

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

    private static double duration(TestClient.Answer response) {
        return Double.parseDouble(response.header(TimingFilter.DURATION_HEADER).orElseThrow());
    }

    @Test
    void requestIdsAreUniqueAndShort() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            ids.add(TimingFilter.newRequestId());
        }
        assertThat(ids).hasSize(50).allSatisfy(id -> assertThat(id).hasSize(16));
    }

    @Test
    void theMiddlewareIsInstalled() {
        assertThat(client.running()
                        .context()
                        .getBeansOfType(FilterRegistrationBean.class)
                        .values())
                .anyMatch(registration -> registration.getFilter() instanceof TimingFilter);
    }

    @Test
    void aResponseCarriesARequestId() {
        assertThat(client.get("/health").header(TimingFilter.REQUEST_ID_HEADER))
                .hasValueSatisfying(id -> assertThat(id).hasSize(16));
    }

    @Test
    void aSuppliedRequestIdIsEchoed() {
        assertThat(client.get("/health", TimingFilter.REQUEST_ID_HEADER, "abc123")
                        .header(TimingFilter.REQUEST_ID_HEADER))
                .contains("abc123");
    }

    @Test
    void aResponseCarriesItsDuration() {
        assertThat(duration(client.get("/health"))).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void everyEndpointIsTimed() {
        for (String path : new String[] {"/health", "/v1/instruments"}) {
            assertThat(client.get(path).header(TimingFilter.DURATION_HEADER))
                    .as(path)
                    .isPresent();
        }
    }

    @Test
    void aQueryIsTimed() {
        TestClient.Answer response = client.postJson(
                "/v1/query", Map.of("text", goldenInstruments().get(1).rawText(), "jurisdiction", "mt"));
        assertThat(response.status()).isEqualTo(200);
        assertThat(duration(response)).isPositive();
    }

    @Test
    void anErrorResponseIsStillTimed() {
        TestClient.Answer response = client.postJson("/v1/query", Map.of("text", "AN ACT\n", "jurisdiction", "mt"));
        assertThat(response.status()).isEqualTo(422);
        assertThat(response.header(TimingFilter.DURATION_HEADER)).isPresent();
    }

    @Test
    void aMissingRouteIsStillTimed() {
        TestClient.Answer response = client.get("/nowhere");
        assertThat(response.status()).isEqualTo(404);
        assertThat(response.header(TimingFilter.REQUEST_ID_HEADER)).isPresent();
        assertThat(response.json().get("detail").asText()).isEqualTo("Not Found");
    }

    @Test
    void theSlowThresholdIsConfigurable() {
        assertThat(LexdriftService.create(new Engine()).slowMs()).isPositive();
        assertThat(LexdriftService.create(new Engine(), TokenPolicy.open(), 5.0).slowMs())
                .isEqualTo(5.0);
    }
}
