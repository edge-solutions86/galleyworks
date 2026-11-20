package io.github.rkaushikdev.galleyworks.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.galleyworks.StoreError;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchemaTest {

    @TempDir
    Path tmp;

    private Connection connection;

    @BeforeEach
    void open() {
        connection = Schema.connect(":memory:");
    }

    @AfterEach
    void close() throws SQLException {
        connection.close();
    }

    private Set<String> tables() {
        return Sql.rows(connection, "SELECT name FROM sqlite_master WHERE type = 'table'").stream()
                .map(row -> String.valueOf(row.get("name")))
                .collect(Collectors.toSet());
    }

    @Test
    void aFreshDatabaseStartsAtVersionZero() {
        assertThat(Schema.userVersion(connection)).isZero();
    }

    @Test
    void migrationCreatesEveryTable() {
        assertThat(Schema.migrate(connection)).isEqualTo(Schema.SCHEMA_VERSION);
        assertThat(tables()).contains("meta", "instrument", "segment", "band", "posting");
    }

    @Test
    void migrationIsIdempotent() {
        Schema.migrate(connection);
        assertThat(Schema.migrate(connection)).isEqualTo(Schema.SCHEMA_VERSION);
        assertThat(Schema.userVersion(connection)).isEqualTo(Schema.SCHEMA_VERSION);
    }

    @Test
    void migrationsAreNumberedConsecutively() {
        assertThat(new ArrayList<>(new TreeSet<>(Schema.MIGRATIONS.keySet())))
                .isEqualTo(
                        IntStream.rangeClosed(1, Schema.SCHEMA_VERSION).boxed().toList());
    }

    @Test
    void aNewerSchemaIsRefused() {
        Sql.execute(connection, "PRAGMA user_version = " + (Schema.SCHEMA_VERSION + 1));
        assertThatThrownBy(() -> Schema.migrate(connection)).isInstanceOfSatisfying(StoreError.class, caught -> {
            assertThat(caught.getMessage()).contains("newer schema");
            assertThat(caught.context()).containsEntry("supported", Schema.SCHEMA_VERSION);
        });
    }

    @Test
    void aPartialUpgradeRollsBack() {
        Schema.migrate(connection);
        Sql.execute(connection, "DROP TABLE band");
        Sql.execute(connection, "PRAGMA user_version = 1");
        Sql.execute(connection, "CREATE TABLE band (x INTEGER)");
        assertThatThrownBy(() -> Schema.migrate(connection))
                .isInstanceOf(StoreError.class)
                .hasMessageContaining("migration failed");
        assertThat(Schema.userVersion(connection)).isEqualTo(1);
    }

    @Test
    void metaRoundTrips() {
        Schema.migrate(connection);
        assertThat(Schema.readMeta(connection, "digest")).isEmpty();
        Schema.writeMeta(connection, "digest", "abc123");
        assertThat(Schema.readMeta(connection, "digest")).contains("abc123");
    }

    @Test
    void metaWritesAreUpserts() {
        Schema.migrate(connection);
        Schema.writeMeta(connection, "digest", "one");
        Schema.writeMeta(connection, "digest", "two");
        assertThat(Schema.readMeta(connection, "digest")).contains("two");
    }

    @Test
    void theLaterColumnsExist() {
        Schema.migrate(connection);
        Set<String> columns = Sql.rows(connection, "PRAGMA table_info(instrument)").stream()
                .map(row -> String.valueOf(row.get("name")))
                .collect(Collectors.toSet());
        assertThat(columns).contains("source_format", "content_digest");
    }

    @Test
    void foreignKeysCascade() {
        Schema.migrate(connection);
        Sql.execute(
                connection,
                "INSERT INTO instrument (slug, jurisdiction, session, chamber, number) "
                        + "VALUES ('wy/2025/lower/HB1', 'wy', '2025', 'lower', 'HB1')");
        Sql.execute(
                connection,
                "INSERT INTO segment (slug, segment_index, path, token_count, simhash, blob) "
                        + "VALUES ('wy/2025/lower/HB1', 0, '1', 10, 0, X'00')");
        Sql.execute(connection, "DELETE FROM instrument WHERE slug = 'wy/2025/lower/HB1'");
        assertThat(Sql.scalar(connection, "SELECT COUNT(*) FROM segment")).isZero();
    }

    @Test
    void openingAFileDatabase() throws SQLException {
        Path path = tmp.resolve("index.sqlite");
        try (Connection file = Schema.connect(path)) {
            Schema.migrate(file);
            assertThat(Files.exists(path)).isTrue();
        }
    }

    @Test
    void openingAnUnreachablePath() {
        assertThatThrownBy(() -> Schema.connect(tmp.resolve("missing").resolve("index.sqlite")))
                .isInstanceOf(StoreError.class)
                .hasMessageContaining("cannot be opened");
    }
}
