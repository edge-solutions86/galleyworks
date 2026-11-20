package io.github.rkaushikdev.galleyworks;

import java.nio.file.Path;

/**
 * The entry points a caller reaches for first, and the release version.
 *
 * <p>Everything else stays where it is: re-exporting the whole tree would make the import surface
 * look like the API surface, and most of the engine is implementation that should be free to move.
 *
 * <pre>{@code
 * Engine engine = new Engine(new EngineConfig());
 * RunResult result = engine.run(Lexdrift.discover(Path.of("bills")));
 * for (InstrumentReport report : result.reports()) {
 *     if (!report.isEmpty()) {
 *         System.out.println(report.key().slug() + " " + report.summary().reuseRatio());
 *     }
 * }
 * }</pre>
 */
public final class Lexdrift {

    /** The release version. */
    public static final String VERSION = "1.0.0";

    private Lexdrift() {}

    /** A corpus discovered from the file names under {@code root}. */
    public static Corpus discover(Path root) {
        return Corpus.discover(root);
    }

    /** A corpus read from a YAML manifest. */
    public static Corpus loadManifest(Path manifest) {
        return Corpus.loadManifest(manifest);
    }

    /** The configuration a YAML file and the environment describe. */
    public static EngineConfig loadConfig(Path path) {
        return Settings.loadConfig(path);
    }

    /** The stable code for {@code error}. */
    public static String errorCodeOf(Throwable error) {
        return LexdriftError.errorCodeOf(error);
    }

    /** The HTTP status {@code error} means. */
    public static int httpStatusOf(Throwable error) {
        return LexdriftError.httpStatusOf(error);
    }
}
