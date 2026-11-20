package io.github.rkaushikdev.galleyworks.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rkaushikdev.galleyworks.Lexdrift;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * The Spring wiring of the service. The engine, the token policy and the timing filter are built by
 * {@link LexdriftService} and handed in, so Spring never builds an engine of its own.
 *
 * <p>The token check runs outside the timing filter: a refused request is answered before it is
 * timed.
 */
@SpringBootApplication
public class ServiceApplication {

    private static final int AUTH_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;
    private static final int TIMING_ORDER = Ordered.HIGHEST_PRECEDENCE + 20;

    /** The token check, ahead of everything else. */
    @Bean
    FilterRegistrationBean<AuthFilter> authFilter(TokenPolicy policy, ObjectMapper json) {
        FilterRegistrationBean<AuthFilter> registration = new FilterRegistrationBean<>(new AuthFilter(policy, json));
        registration.setOrder(AUTH_ORDER);
        return registration;
    }

    /** Request ids, duration headers and the access log. */
    @Bean
    FilterRegistrationBean<TimingFilter> timingFilter(TimingFilter timing) {
        FilterRegistrationBean<TimingFilter> registration = new FilterRegistrationBean<>(timing);
        registration.setOrder(TIMING_ORDER);
        return registration;
    }

    /** The title, version and summary of the API document. */
    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("lexdrift")
                        .version(Lexdrift.VERSION)
                        .summary("Passage-reuse detection for legislative instruments."));
    }
}
