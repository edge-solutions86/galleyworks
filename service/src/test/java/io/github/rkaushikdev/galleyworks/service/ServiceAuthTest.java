package io.github.rkaushikdev.galleyworks.service;

import static io.github.rkaushikdev.galleyworks.support.GoldenCorpus.goldenInstruments;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.Engine;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ServiceAuthTest {

    private static final String TOKEN = "s3cret-token-value";

    private static TestClient guarded;
    private static TestClient open;

    private static Engine engine() {
        Engine built = new Engine();
        built.add(goldenInstruments().getFirst());
        return built;
    }

    @BeforeAll
    static void clients() {
        guarded = TestClient.of(LexdriftService.create(engine(), TokenPolicy.requiring(TOKEN)));
        open = TestClient.of(LexdriftService.create(engine(), TokenPolicy.open()));
    }

    @AfterAll
    static void stop() {
        guarded.close();
        open.close();
    }

    @Test
    void noTokenMeansNoEnforcement() {
        TokenPolicy policy = TokenPolicy.open();
        assertThat(policy.isEnforced()).isFalse();
        assertThat(policy.accepts(Optional.empty())).isTrue();
        assertThat(policy.accepts(Optional.of("anything"))).isTrue();
    }

    @Test
    void aConfiguredTokenIsEnforced() {
        TokenPolicy policy = TokenPolicy.requiring(TOKEN);
        assertThat(policy.isEnforced()).isTrue();
        assertThat(policy.accepts(Optional.of(TOKEN))).isTrue();
        assertThat(policy.accepts(Optional.of("wrong"))).isFalse();
        assertThat(policy.accepts(Optional.empty())).isFalse();
    }

    @Test
    void thePolicyReadsTheEnvironment() {
        assertThat(TokenPolicy.fromEnvironment(Map.of(TokenPolicy.TOKEN_ENV, TOKEN))
                        .token())
                .contains(TOKEN);
        assertThat(TokenPolicy.fromEnvironment(Map.of(TokenPolicy.TOKEN_ENV, "   "))
                        .token())
                .isEmpty();
        assertThat(TokenPolicy.fromEnvironment(Map.of()).token()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            nullValues = "NONE",
            textBlock =
                    """
            Bearer s3cret-token-value | s3cret-token-value
            bearer s3cret-token-value | s3cret-token-value
            Basic abc                 | NONE
            Bearer                    | NONE
            'Bearer   '               | NONE
            NONE                      | NONE
            ''                        | NONE
            """)
    void theHeaderIsParsed(String header, String expected) {
        assertThat(TokenPolicy.bearerToken(Optional.ofNullable(header))).isEqualTo(Optional.ofNullable(expected));
    }

    @Test
    void anOpenServiceAnswersWithoutAToken() {
        assertThat(open.get("/v1/instruments").status()).isEqualTo(200);
    }

    @Test
    void aGuardedServiceRefusesWithoutAToken() {
        TestClient.Answer response = guarded.get("/v1/instruments");
        assertThat(response.status()).isEqualTo(401);
        assertThat(response.header("WWW-Authenticate")).contains("Bearer");
        assertThat(response.json().get("code").asText()).isEqualTo("lexdrift.unauthorized");
    }

    @Test
    void aGuardedServiceAcceptsTheRightToken() {
        assertThat(guarded.get("/v1/instruments", "Authorization", "Bearer " + TOKEN)
                        .status())
                .isEqualTo(200);
    }

    @Test
    void aGuardedServiceRefusesTheWrongToken() {
        assertThat(guarded.get("/v1/instruments", "Authorization", "Bearer nope")
                        .status())
                .isEqualTo(401);
    }

    @Test
    void healthIsExempt() {
        assertThat(guarded.get("/health").status()).isEqualTo(200);
    }

    @Test
    void theOpenapiDocumentIsExempt() {
        assertThat(guarded.get("/openapi.json").status()).isEqualTo(200);
    }

    @Test
    void theQueryEndpointIsGuarded() {
        Map<String, Object> body = Map.of("text", goldenInstruments().get(1).rawText(), "jurisdiction", "mt");
        assertThat(guarded.postJson("/v1/query", body).status()).isEqualTo(401);
        assertThat(guarded.postJson("/v1/query", body, "Authorization", "Bearer " + TOKEN)
                        .status())
                .isEqualTo(200);
    }

    @Test
    void thePolicyIsRecordedOnTheApp() {
        assertThat(guarded.running().context().getBean(TokenPolicy.class).isEnforced())
                .isTrue();
    }
}
