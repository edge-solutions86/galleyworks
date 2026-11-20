package io.github.rkaushikdev.galleyworks.index;

import io.github.rkaushikdev.galleyworks.Context;
import io.github.rkaushikdev.galleyworks.StoreError;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The sqlite schema of the index and its migrations.
 *
 * <p>Migrations are a plain list of statements keyed by the version they produce. Opening a
 * database runs whatever is missing inside one transaction, so an interrupted upgrade leaves the
 * file at the version it started from rather than somewhere in between. The engine digest is
 * recorded in {@code meta} on first write and checked on every open.
 */
public final class Schema {

    /** The schema version this code writes. */
    public static final int SCHEMA_VERSION = 4;

    /** The statements that produce each schema version, in order. */
    public static final Map<Integer, List<String>> MIGRATIONS = Map.of(
            1,
            List.of(
                    """
                    CREATE TABLE meta (
                        name  TEXT PRIMARY KEY,
                        value TEXT NOT NULL
                    )""",
                    """
                    CREATE TABLE instrument (
                        slug         TEXT PRIMARY KEY,
                        jurisdiction TEXT NOT NULL,
                        session      TEXT NOT NULL,
                        chamber      TEXT NOT NULL,
                        number       TEXT NOT NULL,
                        title        TEXT NOT NULL DEFAULT '',
                        enacted_on   TEXT
                    )""",
                    """
                    CREATE TABLE segment (
                        slug          TEXT NOT NULL,
                        segment_index INTEGER NOT NULL,
                        path          TEXT NOT NULL,
                        token_count   INTEGER NOT NULL,
                        simhash       INTEGER NOT NULL,
                        blob          BLOB NOT NULL,
                        PRIMARY KEY (slug, segment_index),
                        FOREIGN KEY (slug) REFERENCES instrument(slug) ON DELETE CASCADE
                    )"""),
            2,
            List.of(
                    """
                    CREATE TABLE band (
                        band_key      BLOB NOT NULL,
                        slug          TEXT NOT NULL,
                        segment_index INTEGER NOT NULL,
                        PRIMARY KEY (band_key, slug, segment_index)
                    )""",
                    "CREATE INDEX band_by_segment ON band (slug, segment_index)",
                    """
                    CREATE TABLE posting (
                        value         INTEGER NOT NULL,
                        slug          TEXT NOT NULL,
                        segment_index INTEGER NOT NULL,
                        PRIMARY KEY (value, slug, segment_index)
                    )""",
                    "CREATE INDEX posting_by_segment ON posting (slug, segment_index)"),
            3,
            List.of(
                    "ALTER TABLE instrument ADD COLUMN source_format TEXT NOT NULL DEFAULT 'unknown'",
                    "CREATE INDEX instrument_by_jurisdiction ON instrument (jurisdiction, session)"),
            4,
            List.of("ALTER TABLE instrument ADD COLUMN content_digest TEXT"));

    /**
     * Tuned for the two shapes of work the index sees: one long write during a corpus load, and many
     * small reads afterwards. WAL so a reader is never blocked by the writer; NORMAL sync because the
     * index is derived data; a 64 MiB page cache; memory temp storage so a query's sorts never touch
     * the disk.
     */
    private static final List<String> PRAGMAS = List.of(
            "PRAGMA journal_mode = WAL",
            "PRAGMA synchronous = NORMAL",
            "PRAGMA foreign_keys = ON",
            "PRAGMA busy_timeout = 5000",
            "PRAGMA cache_size = -65536",
            "PRAGMA temp_store = MEMORY");

    private static final String MEMORY = ":memory:";
    private static final String MEMORY_ONLY_SKIP = "PRAGMA journal_mode";

    private Schema() {}

    /** Open the database at {@code path} with the pragmas the store depends on. */
    public static Connection connect(Path path) {
        return connect(path.toString());
    }

    /** Open {@code target}, a file path or {@code :memory:}, with the store's pragmas. */
    public static Connection connect(String target) {
        Connection connection;
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + target);
        } catch (SQLException exc) {
            throw new StoreError("index database cannot be opened", Context.of("path", target), exc);
        }
        try (Statement statement = connection.createStatement()) {
            for (String pragma : PRAGMAS) {
                if (target.equals(MEMORY) && pragma.startsWith(MEMORY_ONLY_SKIP)) {
                    continue;
                }
                statement.execute(pragma);
            }
        } catch (SQLException exc) {
            StoreError error = new StoreError("index database cannot be opened", Context.of("path", target), exc);
            try {
                connection.close();
            } catch (SQLException closing) {
                error.addSuppressed(closing);
            }
            throw error;
        }
        return connection;
    }

    /**
     * Fold the write-ahead log back into the database file, returning the pages it held. Without this
     * a long-lived writer leaves a log beside the index that can outgrow it.
     */
    public static int checkpoint(Connection connection) {
        try (Statement statement = connection.createStatement();
                ResultSet row = statement.executeQuery("PRAGMA wal_checkpoint(TRUNCATE)")) {
            if (!row.next()) {
                return 0;
            }
            // A database with no write-ahead log answers with -1 frames: nothing to fold back.
            return Math.max(0, row.getInt(2));
        } catch (SQLException exc) {
            return 0;
        }
    }

    /** The schema version recorded in the database header. */
    public static int userVersion(Connection connection) {
        return (int) Sql.scalar(connection, "PRAGMA user_version");
    }

    /** Bring the database up to {@link #SCHEMA_VERSION}, returning the version. */
    public static int migrate(Connection connection) {
        int current = userVersion(connection);
        if (current > SCHEMA_VERSION) {
            throw new StoreError(
                    "index was written by a newer schema", Context.of("found", current, "supported", SCHEMA_VERSION));
        }
        if (current == SCHEMA_VERSION) {
            return current;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("BEGIN");
            try {
                for (int version = current + 1; version <= SCHEMA_VERSION; version++) {
                    for (String sql : MIGRATIONS.get(version)) {
                        statement.execute(sql);
                    }
                }
                statement.execute("PRAGMA user_version = " + SCHEMA_VERSION);
                statement.execute("COMMIT");
            } catch (SQLException exc) {
                statement.execute("ROLLBACK");
                throw new StoreError("index migration failed", Context.of("from_version", current), exc);
            }
        } catch (SQLException exc) {
            throw new StoreError("index migration failed", Context.of("from_version", current), exc);
        }
        return SCHEMA_VERSION;
    }

    /** The value stored under {@code name} in {@code meta}. */
    public static Optional<String> readMeta(Connection connection, String name) {
        return Sql.first(connection, "SELECT value FROM meta WHERE name = ?", name)
                .map(row -> String.valueOf(row.get("value")));
    }

    /** Store {@code value} under {@code name} in {@code meta}, replacing any earlier value. */
    public static void writeMeta(Connection connection, String name, String value) {
        Sql.execute(
                connection,
                "INSERT INTO meta (name, value) VALUES (?, ?) ON CONFLICT(name) DO UPDATE SET value = excluded.value",
                name,
                value);
    }
}
