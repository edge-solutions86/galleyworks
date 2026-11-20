package io.github.rkaushikdev.galleyworks.index;

import static io.github.rkaushikdev.galleyworks.index.MemoryIndexTest.montana;
import static io.github.rkaushikdev.galleyworks.index.MemoryIndexTest.subsection;
import static io.github.rkaushikdev.galleyworks.index.MemoryIndexTest.unrelated;
import static io.github.rkaushikdev.galleyworks.index.MemoryIndexTest.wyoming;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.galleyworks.EngineConfig;
import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.LshConfig;
import io.github.rkaushikdev.galleyworks.ShingleConfig;
import io.github.rkaushikdev.galleyworks.StoreError;
import io.github.rkaushikdev.galleyworks.fingerprint.Fingerprinter;
import io.github.rkaushikdev.galleyworks.fingerprint.SegmentFingerprint;
import io.github.rkaushikdev.galleyworks.match.Matcher;
import io.github.rkaushikdev.galleyworks.match.SegmentMatch;
import io.github.rkaushikdev.galleyworks.support.GoldenCorpus;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteIndexTest {

    @TempDir
    Path tmp;

    private SqliteIndex store;

    @BeforeEach
    void open() {
        store = new SqliteIndex(tmp.resolve("index.sqlite"));
    }

    @AfterEach
    void close() {
        store.close();
    }

    @Test
    void recordsRoundTrip() {
        SegmentFingerprint record = wyoming().getFirst();
        SegmentFingerprint readBack = store.record(store.add(record));
        assertThat(readBack.key()).isEqualTo(record.key());
        assertThat(readBack.signature()).isEqualTo(record.signature());
        assertThat(readBack.selected()).isEqualTo(record.selected());
    }

    @Test
    void lengthCountsSegments() {
        int added = store.addAll(wyoming());
        assertThat(store.size()).isEqualTo(added);
    }

    @Test
    void anUnknownReferenceIsAnError() {
        assertThatThrownBy(
                        () -> store.record(new SegmentRef(wyoming().getFirst().key(), 99)))
                .isInstanceOf(StoreError.class)
                .hasMessageContaining("no such segment");
    }

    @Test
    void addingTheSameSegmentTwiceReplacesIt() {
        SegmentFingerprint record = wyoming().getFirst();
        store.add(record);
        store.add(record);
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void aCopiedClauseIsACandidate() {
        store.addAll(wyoming());
        assertThat(store.candidates(subsection(montana()))).isNotEmpty();
    }

    @Test
    void anUnrelatedClauseIsNotACandidate() {
        store.addAll(wyoming());
        assertThat(store.candidates(subsection(unrelated()))).isEmpty();
    }

    @Test
    void theStoreAgreesWithTheMemoryIndex() {
        MemoryIndex memory = new MemoryIndex();
        memory.addAll(wyoming());
        try (SqliteIndex inMemory = SqliteIndex.inMemory(new EngineConfig())) {
            inMemory.addAll(wyoming());
            SegmentFingerprint probe = subsection(montana());
            assertThat(inMemory.candidates(probe)).isEqualTo(memory.candidates(probe));
            long value = wyoming().getFirst().fingerprintValues().getFirst();
            assertThat(inMemory.postings(value)).isEqualTo(memory.postings(value));
            assertThat(inMemory.instruments()).isEqualTo(memory.instruments());
        }
    }

    @Test
    void removingAnInstrumentClearsItsRows() {
        List<SegmentFingerprint> records = wyoming();
        store.addAll(records);
        long value = records.getFirst().fingerprintValues().getFirst();
        assertThat(store.removeInstrument(records.getFirst().key())).isEqualTo(records.size());
        assertThat(store.size()).isZero();
        assertThat(store.postings(value)).isEmpty();
        assertThat(store.documentFrequency(value)).isZero();
    }

    @Test
    void registeringAnInstrumentKeepsItsMetadata() {
        Instrument instrument = makeBill("wy", "HB0142", MODEL_CLAUSE);
        store.register(instrument);
        store.register(instrument);
        assertThat(store.instruments()).isEmpty();
    }

    @Test
    void statsSummariseTheStore() {
        store.addAll(wyoming());
        store.addAll(unrelated());
        IndexStats stats = store.stats();
        assertThat(stats.instruments()).isEqualTo(2);
        assertThat(stats.segments()).isEqualTo(store.size());
        assertThat(stats.buckets()).isPositive();
    }

    @Test
    void theIndexSurvivesReopening() {
        Path path = tmp.resolve("reopened.sqlite");
        int expected;
        try (SqliteIndex first = new SqliteIndex(path)) {
            first.addAll(wyoming());
            expected = first.size();
        }
        try (SqliteIndex reopened = new SqliteIndex(path)) {
            assertThat(reopened.size()).isEqualTo(expected);
            assertThat(reopened.candidates(subsection(montana()))).isNotEmpty();
        }
    }

    @Test
    void aDifferentEngineConfigIsRefused() {
        Path path = tmp.resolve("other.sqlite");
        try (SqliteIndex first = new SqliteIndex(path)) {
            first.addAll(wyoming());
        }
        EngineConfig other = new EngineConfig().withShingle(new ShingleConfig().withWidth(9));
        assertThatThrownBy(() -> new SqliteIndex(path, other))
                .isInstanceOf(StoreError.class)
                .hasMessageContaining("different engine configuration");
    }

    @Test
    void aSignatureOfTheWrongWidthIsRejected() {
        // A record signed under a narrower banding than the store's has the wrong width.
        EngineConfig narrow = new EngineConfig().withLsh(new LshConfig(64, 16, 0x5EED));
        SegmentFingerprint bad = new Fingerprinter(narrow)
                .fingerprint(makeBill("wy", "HB0142", MODEL_CLAUSE))
                .getFirst();
        assertThatThrownBy(() -> store.add(bad))
                .isInstanceOf(StoreError.class)
                .hasMessageContaining("does not match the index");
    }

    @Test
    void iterationYieldsEveryRecord() {
        store.addAll(wyoming());
        List<SegmentFingerprint> all = new ArrayList<>();
        store.forEach(all::add);
        assertThat(all).hasSize(store.size());
    }

    // The fixture is an index file of the golden corpus written under schema version 4, with its
    // write-ahead log checkpointed. The on-disk format is unchanged, so such a file opens, holds
    // exactly the rows a fresh index of the same corpus holds, and answers the same queries.

    private static final List<String> TABLES = List.of(
            "SELECT name, value FROM meta ORDER BY name",
            "SELECT slug, jurisdiction, session, chamber, number, title, enacted_on FROM instrument ORDER BY slug",
            "SELECT slug, segment_index, path, token_count, simhash, blob FROM segment ORDER BY slug, segment_index",
            "SELECT band_key, slug, segment_index FROM band ORDER BY band_key, slug, segment_index",
            "SELECT value, slug, segment_index FROM posting ORDER BY value, slug, segment_index");

    private Path existingIndex() throws IOException {
        Path copy = tmp.resolve("existing.sqlite");
        try (InputStream fixture = getClass().getResourceAsStream("/index/golden-schema-4.db")) {
            assertThat(fixture).as("index fixture").isNotNull();
            Files.copy(fixture, copy);
        }
        return copy;
    }

    private void indexGoldenCorpus(SqliteIndex target) {
        Fingerprinter fingerprinter = new Fingerprinter();
        target.bulk(() -> {
            for (Instrument instrument : GoldenCorpus.goldenInstruments()) {
                target.register(instrument);
                fingerprinter.fingerprint(instrument).forEach(target::add);
            }
        });
    }

    private static List<String> dump(SqliteIndex index, String query) {
        List<String> rows = new ArrayList<>();
        for (Map<String, Object> row : Sql.rows(index.connection(), query)) {
            List<String> cells = new ArrayList<>();
            row.values()
                    .forEach(cell -> cells.add(
                            cell instanceof byte[] bytes ? HexFormat.of().formatHex(bytes) : String.valueOf(cell)));
            rows.add(String.join("|", cells));
        }
        return rows;
    }

    @Test
    void anExistingIndexFileHoldsTheRowsAFreshIndexWrites() throws IOException {
        indexGoldenCorpus(store);
        try (SqliteIndex existing = new SqliteIndex(existingIndex())) {
            assertThat(existing.stats()).isEqualTo(store.stats());
            assertThat(existing.size()).isEqualTo(26);
            for (String query : TABLES) {
                List<String> rows = dump(existing, query);
                assertThat(rows).as(query).isNotEmpty().isEqualTo(dump(store, query));
            }
            Sql.execute(existing.connection(), "DELETE FROM posting WHERE rowid = (SELECT MIN(rowid) FROM posting)");
            assertThat(dump(existing, TABLES.getLast()))
                    .as("a planted difference")
                    .isNotEqualTo(dump(store, TABLES.getLast()));
        }
    }

    @Test
    void anExistingIndexFileAnswersQueriesAsAFreshIndexDoes() throws IOException {
        indexGoldenCorpus(store);
        Instrument montana = GoldenCorpus.goldenInstruments().get(1);
        try (SqliteIndex existing = new SqliteIndex(existingIndex())) {
            List<SegmentMatch> fromExisting = new Matcher(existing).matchInstrument(montana);
            List<SegmentMatch> fromFresh = new Matcher(store).matchInstrument(montana);
            assertThat(fromExisting).isNotEmpty().isEqualTo(fromFresh);
            assertThat(existing.record(fromExisting.getFirst().other()))
                    .isEqualTo(store.record(fromFresh.getFirst().other()));
        }
    }
}
