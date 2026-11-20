package io.github.rkaushikdev.galleyworks.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import(GoldenService.class)
class ServiceDetailTest {

    private static final String WYOMING = "/v1/instruments/wy/2023/lower/HB0142";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationContext context;

    private Http client;

    @BeforeEach
    void client() {
        client = new Http(mvc);
    }

    private List<JsonNode> segments() {
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
        Http.Answer response = client.get("/v1/instruments/wy/2023/lower/HB9999");
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
        try (TestClient app = TestClient.of(LexdriftService.create(GoldenService.engine(), TokenPolicy.open()))) {
            assertThat(app.get("/openapi.json")
                            .json()
                            .get("paths")
                            .has("/v1/instruments/{jurisdiction}/{session}/{chamber}/{number}"))
                    .isTrue();
        }
    }

    @Test
    void theListingStillWorks() {
        assertThat(client.get("/v1/instruments").json().get("total").asInt()).isEqualTo(6);
    }
}
