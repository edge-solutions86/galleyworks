package io.github.rkaushikdev.galleyworks.service;

import io.github.rkaushikdev.galleyworks.Engine;
import java.util.function.Function;

/**
 * One engine, shared by every request.
 *
 * <p>Requests run on many threads and the engine keeps the texts it has seen, so every route reaches
 * the engine through {@link #read}, one request at a time.
 *
 * @param engine the engine the service answers from
 */
public record ServiceState(Engine engine) {

    /** The digest of the configuration the engine runs under. */
    public String digest() {
        return engine.config().digest();
    }

    /** Run {@code work} against the engine, with no other request touching it meanwhile. */
    public <T> T read(Function<Engine, T> work) {
        synchronized (this) {
            return work.apply(engine);
        }
    }
}
