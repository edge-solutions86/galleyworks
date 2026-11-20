package io.github.rkaushikdev.galleyworks.service;

import io.github.rkaushikdev.galleyworks.Engine;
import io.github.rkaushikdev.galleyworks.cli.ServiceFactory;
import io.github.rkaushikdev.galleyworks.util.Log;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;

/**
 * The HTTP service over one engine, built but not yet listening.
 *
 * <p>Logging stays as the command line configured it: Spring's own logging setup is switched off and
 * the web server's own log goes through the same loggers, so {@code --log-level} and
 * {@code --log-format} still decide what reaches stderr.
 */
public final class LexdriftService implements ServiceFactory.Service {

    private static final Logger LOG = Log.get("service");
    private static final String UNROUTED = "org.springframework.web.servlet.PageNotFound";

    private final ServiceState state;
    private final TokenPolicy policy;
    private final double slowMs;

    private LexdriftService(Engine engine, TokenPolicy policy, double slowMs) {
        this.state = new ServiceState(engine);
        this.policy = policy;
        this.slowMs = slowMs;
    }

    /** A service over a fresh engine, with the token policy the environment configures. */
    public static LexdriftService create() {
        return create(new Engine());
    }

    /** A service over {@code engine}, with the token policy the environment configures. */
    public static LexdriftService create(Engine engine) {
        return create(engine, TokenPolicy.fromEnvironment(System.getenv()));
    }

    /** A service over {@code engine} enforcing {@code policy}. */
    public static LexdriftService create(Engine engine, TokenPolicy policy) {
        return create(engine, policy, TimingFilter.DEFAULT_SLOW_MS);
    }

    /** A service over {@code engine} enforcing {@code policy}, warning about requests over {@code slowMs}. */
    public static LexdriftService create(Engine engine, TokenPolicy policy, double slowMs) {
        return new LexdriftService(engine, policy, slowMs);
    }

    /** What every request shares. */
    public ServiceState state() {
        return state;
    }

    /** The token policy the service enforces. */
    public TokenPolicy policy() {
        return policy;
    }

    /** Requests at least this slow are logged as warnings. */
    public double slowMs() {
        return slowMs;
    }

    /** Start listening on {@code host}:{@code port}; port {@code 0} picks a free one. */
    public Running start(String host, int port) {
        System.setProperty(LoggingSystem.SYSTEM_PROPERTY, LoggingSystem.NONE);
        if (!SLF4JBridgeHandler.isInstalled()) {
            SLF4JBridgeHandler.removeHandlersForRootLogger();
            SLF4JBridgeHandler.install();
        }
        // A request for a path no route serves is answered with a 404 and timed like any other;
        // it is not a warning about the service.
        if (LoggerFactory.getLogger(UNROUTED) instanceof ch.qos.logback.classic.Logger unrouted) {
            unrouted.setLevel(ch.qos.logback.classic.Level.ERROR);
        }
        ConfigurableApplicationContext context = new SpringApplicationBuilder(ServiceApplication.class)
                .initializers(ctx -> {
                    ctx.getBeanFactory().registerSingleton("serviceState", state);
                    ctx.getBeanFactory().registerSingleton("tokenPolicy", policy);
                    ctx.getBeanFactory().registerSingleton("timing", new TimingFilter(slowMs));
                })
                .run("--server.address=" + host, "--server.port=" + port);
        int segments = state.read(engine -> engine.index().size());
        LOG.atInfo()
                .addKeyValue("digest", state.digest())
                .addKeyValue("segments", segments)
                .addKeyValue("authenticated", policy.isEnforced())
                .log("service starting");
        return new Running(context);
    }

    /** Listen on {@code host}:{@code port} until the process is stopped. */
    @Override
    public void run(String host, int port) {
        start(host, port).awaitClose();
    }

    /**
     * A listening service.
     *
     * @param context the running application
     */
    public record Running(ConfigurableApplicationContext context) implements AutoCloseable {

        /** The port the service listens on. */
        public int port() {
            return ((WebServerApplicationContext) context).getWebServer().getPort();
        }

        /** Block until the application is closed, by {@link #close} or by the process stopping. */
        public void awaitClose() {
            CountDownLatch closed = new CountDownLatch(1);
            context.addApplicationListener(event -> {
                if (event instanceof ContextClosedEvent) {
                    closed.countDown();
                }
            });
            boolean done = false;
            try {
                while (!done && context.isActive()) {
                    done = closed.await(1, TimeUnit.SECONDS);
                }
            } catch (InterruptedException exc) {
                Thread.currentThread().interrupt();
            }
        }

        /** Stop listening. */
        @Override
        public void close() {
            LOG.info("service stopping");
            context.close();
        }
    }
}
