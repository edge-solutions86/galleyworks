package io.github.rkaushikdev.galleyworks.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.fingerprint.Fingerprinter;
import io.github.rkaushikdev.galleyworks.support.GoldenCorpus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteTuningTest {

    @TempDir
    Path tmp;

    private static Map<String, Object> settings(Connection connection) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String name : List.of("journal_mode", "synchronous", "foreign_keys", "cache_size", "temp_store")) {
            values.put(
                    name,
                    Sql.first(connection, "PRAGMA " + name)
                            .orElseThrow()
                            .values()
                            .iterator()
                            .next());
        }
        return values;
    }

    @Test
    void aFileDatabaseUsesTheWriteAheadLog() throws SQLException {
        try (Connection connection = Schema.connect(tmp.resolve("index.sqlite"))) {
            assertThat(settings(connection)).containsEntry("journal_mode", "wal");
        }
    }

    @Test
    void theCacheAndTempStoreAreTuned() throws SQLException {
        try (Connection connection = Schema.connect(tmp.resolve("index.sqlite"))) {
            Map<String, Object> values = settings(connection);
            assertThat(((Number) values.get("cache_size")).intValue()).isEqualTo(-65536);
            assertThat(((Number) values.get("temp_store")).intValue()).isEqualTo(2);
            assertThat(((Number) values.get("foreign_keys")).intValue()).isEqualTo(1);
        }
    }

    @Test
    void aMemoryDatabaseSkipsOnlyTheJournalMode() throws SQLException {
        try (Connection connection = Schema.connect(":memory:")) {
            Map<String, Object> values = settings(connection);
            assertThat(values).containsEntry("journal_mode", "memory");
            assertThat(((Number) values.get("cache_size")).intValue()).isEqualTo(-65536);
        }
    }

    @Test
    void aCheckpointOnAnEmptyDatabase() throws SQLException {
        try (Connection connection = Schema.connect(tmp.resolve("index.sqlite"))) {
            Schema.migrate(connection);
            assertThat(Schema.checkpoint(connection)).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void aCheckpointOnAMemoryDatabaseIsHarmless() throws SQLException {
        try (Connection connection = Schema.connect(":memory:")) {
            Schema.migrate(connection);
            assertThat(Schema.checkpoint(connection)).isZero();
        }
    }

    @Test
    void closingLeavesNoWriteAheadLog() throws IOException {
        Path path = tmp.resolve("index.sqlite");
        Fingerprinter fingerprinter = new Fingerprinter();
        try (SqliteIndex store = new SqliteIndex(path)) {
            store.bulk(() -> {
                for (Instrument instrument : GoldenCorpus.goldenInstruments()) {
                    fingerprinter.fingerprint(instrument).forEach(store::add);
                }
            });
        }
        Path wal = path.resolveSibling(path.getFileName() + "-wal");
        assertThat(!Files.exists(wal) || Files.size(wal) == 0).isTrue();
    }

    @Test
    void theIndexReopensAfterACheckpoint() {
        Path path = tmp.resolve("index.sqlite");
        int expected;
        try (SqliteIndex store = new SqliteIndex(path)) {
            store.addAll(new Fingerprinter()
                    .fingerprint(GoldenCorpus.goldenInstruments().getFirst()));
            expected = store.size();
        }
        try (SqliteIndex reopened = new SqliteIndex(path)) {
            assertThat(reopened.size()).isEqualTo(expected);
        }
    }

    @Test
    void aClosedConnectionDoesNotRaise() {
        SqliteIndex store = new SqliteIndex(tmp.resolve("index.sqlite"));
        store.close();
        assertThatThrownBy(() -> {
                    try (Statement statement = store.connection().createStatement()) {
                        statement.execute("SELECT 1");
                    }
                })
                .isInstanceOf(SQLException.class);
    }
}
