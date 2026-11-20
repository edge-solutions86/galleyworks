package io.github.rkaushikdev.galleyworks.service;

import static io.github.rkaushikdev.galleyworks.support.GoldenCorpus.goldenInstruments;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.galleyworks.Engine;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ServiceDetailTest {

    private static final String WYOMING = "/v1/instruments/wy/2023/lower/HB0142";

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

    private static List<JsonNode> segments() {
        List<JsonNode> found = new ArrayList<>();
        client.get(WYOMING).json().get("segments").forEach(found::add);
        return found;
    }

    @Test
    void theDetailNamesTheInstrument() {
        JsonNode payload = client.get(WYOMING).json();
        assertThat(payload.at("/instrument/key").asText()).isEqualTo("wy/2023/lower/HB0142");
        assertThat(payload.at("/instrument/chamber").asText()).isEqualTo("lower");
    }

    @Test
    void everySegmentIsListed() {
        assertThat(segments())
                .extracting(segment -> segment.get("path").asText())
                .containsExactly("1", "1(a)", "1(b)", "1(b)(i)", "1(b)(ii)", "2");
    }

    @Test
    void segmentsComeBackInDocumentOrder() {
        assertThat(segments())
                .extracting(segment -> segment.get("index").asInt())
                .isSorted();
    }

    @Test
    void eachSegmentReportsItsSize() {
        assertThat(segments()).allSatisfy(segment -> {
            assertThat(segment.get("tokens").asInt()).isPositive();
            assertThat(segment.get("fingerprints").asInt()).isNotNegative();
            assertThat(segment.get("content_ratio").asDouble()).isBetween(0.0, 1.0);
        });
    }

    @Test
    void anUnknownInstrumentIsA404() {
        TestClient.Answer response = client.get("/v1/instruments/wy/2023/lower/HB9999");
        assertThat(response.status()).isEqualTo(404);
        assertThat(response.json().get("detail").asText()).isEqualTo("no such instrument");
    }

    @Test
    void aMalformedKeyIsA404() {
        assertThat(client.get("/v1/instruments/WY/2023/lower/HB0142").status()).isEqualTo(404);
    }

    @Test
    void anUnknownChamberIsA404() {
        assertThat(client.get("/v1/instruments/wy/2023/senate/HB0142").status()).isEqualTo(404);
    }

    @Test
    void theRouteIsDocumented() {
        assertThat(client.get("/openapi.json")
                        .json()
                        .get("paths")
                        .has("/v1/instruments/{jurisdiction}/{session}/{chamber}/{number}"))
                .isTrue();
    }

    @Test
    void theListingStillWorks() {
        assertThat(client.get("/v1/instruments").json().get("total").asInt()).isEqualTo(6);
    }
}
