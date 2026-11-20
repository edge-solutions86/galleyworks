package io.github.rkaushikdev.galleyworks.cli;

import io.github.rkaushikdev.galleyworks.Engine;

/**
 * Builds the HTTP service over an engine.
 *
 * <p>The service lives in its own module so that the library and the command line stay free of a
 * web stack. {@code lexdrift serve} finds an implementation through {@link java.util.ServiceLoader};
 * when none is on the class path the command says so.
 */
public interface ServiceFactory {

    /** The service over {@code engine}, built but not yet listening. */
    Service create(Engine engine);

    /** A built service. */
    interface Service {

        /** Listen on {@code host}:{@code port} until the process is stopped. */
        void run(String host, int port);
    }
}
