package io.github.rkaushikdev.galleyworks.service;

import static io.github.rkaushikdev.galleyworks.support.GoldenCorpus.goldenInstruments;

import io.github.rkaushikdev.galleyworks.Engine;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * What {@link LexdriftService#start} hands the application, for a route test: the golden corpus
 * indexed in memory, no token, and the default slow-request threshold.
 */
@TestConfiguration(proxyBeanMethods = false)
class GoldenService {

    /** An engine holding the six golden instruments. */
    static Engine engine() {
        Engine engine = new Engine();
        goldenInstruments().forEach(engine::add);
        return engine;
    }

    @Bean
    ServiceState serviceState() {
        return new ServiceState(engine());
    }

    @Bean
    TokenPolicy tokenPolicy() {
        return TokenPolicy.open();
    }

    @Bean
    TimingFilter timing() {
        return new TimingFilter(TimingFilter.DEFAULT_SLOW_MS);
    }
}
