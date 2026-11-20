package io.github.rkaushikdev.galleyworks.service;

import io.github.rkaushikdev.galleyworks.Engine;
import io.github.rkaushikdev.galleyworks.cli.ServiceFactory;

/** What {@code lexdrift serve} finds on the class path: the service, with the environment's token policy. */
public final class LexdriftServiceFactory implements ServiceFactory {

    @Override
    public Service create(Engine engine) {
        return LexdriftService.create(engine);
    }
}
