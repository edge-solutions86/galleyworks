package io.github.rkaushikdev.galleyworks.cli;

import io.github.rkaushikdev.galleyworks.ConfigError;
import io.github.rkaushikdev.galleyworks.Context;
import io.github.rkaushikdev.galleyworks.Engine;
import io.github.rkaushikdev.galleyworks.index.IndexStats;
import java.nio.file.Path;
import java.util.Optional;
import java.util.ServiceLoader;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/** {@code lexdrift serve}: run the HTTP service over an index. */
@Command(
        name = "serve",
        header = "run the http service over an index",
        customSynopsis = "lexdrift serve [OPTIONS]",
        description = "Serve the index over HTTP.")
public final class ServeCommand extends Common.Base {

    @Mixin
    Common.RequiredIndexOption index;

    @Mixin
    Common.EngineOptions engineOptions;

    @Option(
            names = "--host",
            paramLabel = "TEXT",
            defaultValue = "127.0.0.1",
            showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
            description = "interface to bind")
    String host;

    @Option(
            names = "--port",
            paramLabel = "INTEGER",
            defaultValue = "8000",
            showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
            description = "port to bind")
    int port;

    @Option(names = "--check", description = "load the index and exit without binding")
    boolean check;

    /**
     * A service and the engine behind it.
     *
     * @param app the service, built but not listening
     * @param engine the engine it answers from
     */
    public record Built(ServiceFactory.Service app, Engine engine) {}

    /** Build the service and the engine behind it from the common options. */
    public static Built buildService(
            Optional<Path> indexPath, Optional<Path> configPath, Optional<String> profile, Optional<String> reading) {
        ServiceFactory factory = ServiceLoader.load(ServiceFactory.class)
                .findFirst()
                .orElseThrow(() -> new ConfigError(
                        "the service extra is not installed",
                        Context.of("remedy", "run lexdrift from the service distribution")));
        Engine engine = Common.buildEngine(indexPath, configPath, profile, reading);
        return new Built(factory.create(engine), engine);
    }

    @Override
    public Integer call() {
        Built built = buildService(
                Optional.of(index.path()),
                Optional.ofNullable(engineOptions.config),
                Optional.ofNullable(engineOptions.profile),
                Optional.ofNullable(engineOptions.reading));
        IndexStats stats = built.engine().index().stats();
        line("index loaded: " + stats.instruments() + " instruments, " + stats.segments() + " segments, digest "
                + stats.digest());
        if (check) {
            line("check only, not binding");
            Common.close(built.engine().index());
            return Main.EXIT_OK;
        }
        line("listening on http://" + host + ":" + port);
        built.app().run(host, port);
        return Main.EXIT_OK;
    }
}
