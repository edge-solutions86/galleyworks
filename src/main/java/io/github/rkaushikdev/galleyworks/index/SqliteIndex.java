package io.github.rkaushikdev.galleyworks.index;

import io.github.rkaushikdev.galleyworks.Context;
import io.github.rkaushikdev.galleyworks.EngineConfig;
import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.InstrumentKey;
import io.github.rkaushikdev.galleyworks.StoreError;
import io.github.rkaushikdev.galleyworks.fingerprint.Codec;
import io.github.rkaushikdev.galleyworks.fingerprint.Hashing;
import io.github.rkaushikdev.galleyworks.fingerprint.SegmentFingerprint;
import io.github.rkaushikdev.galleyworks.util.Bitops;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * The sqlite-backed index: the same operations as {@link MemoryIndex}, against a file.
 *
 * <p>The band and posting tables are the on-disk form of the two maps the memory index keeps, and
 * both are rewritten whenever a segment is replaced, so an updated instrument never leaves stale
 * buckets pointing at it. The engine digest is written on first open and checked on every later
 * one: two indexes built with different shingle widths hold incomparable fingerprints, and merging
 * them silently is the failure this guards against.
 */
public final class SqliteIndex implements Index, AutoCloseable {

    private static final String DIGEST_KEY = "engine_digest";

    private final EngineConfig config;
    private final String path;
    private final Connection connection;
    private boolean inBulk;

    /** Open, migrate and check the index at {@code path} under the default configuration. */
    public SqliteIndex(Path path) {
        this(path.toString(), new EngineConfig());
    }

    /** Open, migrate and check the index at {@code path} under {@code config}. */
    public SqliteIndex(Path path, EngineConfig config) {
        this(path.toString(), config);
    }

    /** Open {@code path}, which may be {@code :memory:}, under {@code config}. */
    public SqliteIndex(String path, EngineConfig config) {
        this.config = config;
        this.path = path;
        this.connection = Schema.connect(path);
        try {
            Schema.migrate(connection);
            checkDigest();
        } catch (StoreError exc) {
            close();
            throw exc;
        }
    }

    /** A private in-memory database under {@code config}. */
    public static SqliteIndex inMemory(EngineConfig config) {
        return new SqliteIndex(":memory:", config);
    }

    /**
     * Checkpoint the log, then close. Closing without a checkpoint leaves the log on disk for the
     * next reader to replay, which turns a fast open into a slow one for no reason.
     */
    @Override
    public void close() {
        Schema.checkpoint(connection);
        try {
            connection.close();
        } catch (SQLException exc) {
            throw new StoreError("index database cannot be closed", Context.of("path", path), exc);
        }
    }

    /**
     * The live connection. Maintenance rewrites the derived tables in bulk, which the
     * record-at-a-time API cannot express, so the connection is part of the contract.
     */
    public Connection connection() {
        return connection;
    }

    /** Where the database lives. */
    public String path() {
        return path;
    }

    @Override
    public EngineConfig config() {
        return config;
    }

    private void checkDigest() {
        Optional<String> recorded = Schema.readMeta(connection, DIGEST_KEY);
        if (recorded.isEmpty()) {
            Schema.writeMeta(connection, DIGEST_KEY, digest());
            return;
        }
        if (!recorded.get().equals(digest())) {
            throw new StoreError(
                    "index was built with a different engine configuration",
                    Context.of("found", recorded.get(), "expected", digest(), "path", path));
        }
    }

    @Override
    public int size() {
        return (int) Sql.scalar(connection, "SELECT COUNT(*) AS n FROM segment");
    }

    @Override
    public Iterator<SegmentFingerprint> iterator() {
        List<SegmentFingerprint> found = new ArrayList<>();
        for (Map<String, Object> row : Sql.rows(connection, "SELECT blob FROM segment ORDER BY slug, segment_index")) {
            found.add(Codec.decodeRecord((byte[]) row.get("blob")));
        }
        return Collections.unmodifiableList(found).iterator();
    }

    /** Record an instrument's identity and metadata so its segments can hang off it. */
    public void register(Instrument instrument) {
        InstrumentKey key = instrument.key();
        Sql.execute(
                connection,
                "INSERT INTO instrument "
                        + "(slug, jurisdiction, session, chamber, number, title, enacted_on, source_format, "
                        + "content_digest) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(slug) DO UPDATE SET title = excluded.title, "
                        + "enacted_on = excluded.enacted_on, source_format = excluded.source_format, "
                        + "content_digest = excluded.content_digest",
                key.slug(),
                key.jurisdiction(),
                key.session(),
                key.chamber().value(),
                key.number(),
                instrument.title(),
                instrument.effectiveDate().map(Object::toString).orElse(null),
                instrument.sourceFormat(),
                contentDigest(instrument));
    }

    @Override
    public SegmentRef add(SegmentFingerprint record) {
        if (inBulk) {
            return write(record);
        }
        return transaction(() -> write(record), SegmentRef.of(record).slug());
    }

    /**
     * Run {@code block} with every write inside it in one transaction.
     *
     * <p>A per-record transaction costs an fsync each, and indexing a session of bills is tens of
     * thousands of them. Grouping the writes turns a corpus load from minutes into seconds; a
     * failure rolls the whole block back, which for an index rebuilt from documents is the behaviour
     * you want anyway.
     */
    public void bulk(Runnable block) {
        bulk(() -> {
            block.run();
            return null;
        });
    }

    /** As {@link #bulk(Runnable)}, returning the block's value. */
    public <T> T bulk(Supplier<T> block) {
        if (inBulk) {
            throw new StoreError("bulk writes cannot be nested", Context.of("path", path));
        }
        Sql.execute(connection, "BEGIN");
        inBulk = true;
        T result;
        try {
            result = block.get();
        } catch (RuntimeException | Error exc) {
            inBulk = false;
            Sql.execute(connection, "ROLLBACK");
            throw exc;
        }
        inBulk = false;
        Sql.execute(connection, "COMMIT");
        return result;
    }

    private <T> T transaction(Supplier<T> work, String ref) {
        Sql.execute(connection, "BEGIN");
        T result;
        try {
            result = work.get();
        } catch (StoreError exc) {
            Sql.execute(connection, "ROLLBACK");
            if (exc.getCause() instanceof SQLException) {
                throw new StoreError("index write failed", Context.of("ref", ref), exc);
            }
            throw exc;
        } catch (RuntimeException exc) {
            Sql.execute(connection, "ROLLBACK");
            throw exc;
        }
        Sql.execute(connection, "COMMIT");
        return result;
    }

    private SegmentRef write(SegmentFingerprint record) {
        if (record.signature().size() != config.lsh().permutations()) {
            throw new StoreError(
                    "record signature width does not match the index",
                    Context.of(
                            "width",
                            record.signature().size(),
                            "expected",
                            config.lsh().permutations()));
        }
        SegmentRef ref = SegmentRef.of(record);
        String slug = record.key().slug();
        ensureInstrument(record.key());
        deleteSegment(slug, record.segmentIndex());
        Sql.execute(
                connection,
                "INSERT INTO segment (slug, segment_index, path, token_count, simhash, blob) VALUES (?, ?, ?, ?, ?, ?)",
                slug,
                record.segmentIndex(),
                record.path(),
                record.tokenCount(),
                Bitops.toSigned64(record.simhash()),
                Codec.encodeRecord(record));
        List<Object[]> bands = new ArrayList<>();
        for (BandKey key : Lsh.bandKeys(record.signature(), config.lsh())) {
            bands.add(new Object[] {key.bytes(), slug, record.segmentIndex()});
        }
        Sql.executeMany(
                connection, "INSERT OR IGNORE INTO band (band_key, slug, segment_index) VALUES (?, ?, ?)", bands);
        List<Object[]> values = new ArrayList<>();
        for (long value : record.fingerprintValues()) {
            values.add(new Object[] {Bitops.toSigned64(value), slug, record.segmentIndex()});
        }
        Sql.executeMany(
                connection, "INSERT OR IGNORE INTO posting (value, slug, segment_index) VALUES (?, ?, ?)", values);
        return ref;
    }

    /** Write every record in one transaction. */
    @Override
    public int addAll(Iterable<SegmentFingerprint> records) {
        return bulk(() -> {
            int written = 0;
            for (SegmentFingerprint record : records) {
                write(record);
                written++;
            }
            return written;
        });
    }

    private void ensureInstrument(InstrumentKey key) {
        Sql.execute(
                connection,
                "INSERT OR IGNORE INTO instrument (slug, jurisdiction, session, chamber, number) VALUES (?, ?, ?, ?, ?)",
                key.slug(),
                key.jurisdiction(),
                key.session(),
                key.chamber().value(),
                key.number());
    }

    private void deleteSegment(String slug, int segmentIndex) {
        for (String table : List.of("band", "posting", "segment")) {
            Sql.execute(
                    connection, "DELETE FROM " + table + " WHERE slug = ? AND segment_index = ?", slug, segmentIndex);
        }
    }

    @Override
    public int removeInstrument(InstrumentKey key) {
        int count = (int) Sql.scalar(connection, "SELECT COUNT(*) AS n FROM segment WHERE slug = ?", key.slug());
        Sql.execute(connection, "DELETE FROM band WHERE slug = ?", key.slug());
        Sql.execute(connection, "DELETE FROM posting WHERE slug = ?", key.slug());
        Sql.execute(connection, "DELETE FROM instrument WHERE slug = ?", key.slug());
        return count;
    }

    @Override
    public SegmentFingerprint record(SegmentRef ref) {
        return Sql.first(
                        connection,
                        "SELECT blob FROM segment WHERE slug = ? AND segment_index = ?",
                        ref.key().slug(),
                        ref.segmentIndex())
                .map(row -> Codec.decodeRecord((byte[]) row.get("blob")))
                .orElseThrow(() -> new StoreError("no such segment in the index", Context.of("ref", ref.slug())));
    }

    @Override
    public List<SegmentRef> candidates(SegmentFingerprint record) {
        List<BandKey> keys = Lsh.bandKeys(record.signature(), config.lsh());
        String placeholders = String.join(",", Collections.nCopies(keys.size(), "?"));
        Object[] params = keys.stream().map(BandKey::bytes).toArray();
        TreeSet<SegmentRef> found = new TreeSet<>();
        for (Map<String, Object> row : Sql.rows(
                connection,
                "SELECT DISTINCT slug, segment_index FROM band WHERE band_key IN (" + placeholders + ")",
                params)) {
            found.add(ref(row));
        }
        found.remove(SegmentRef.of(record));
        return List.copyOf(found);
    }

    @Override
    public List<SegmentRef> postings(long value) {
        TreeSet<SegmentRef> found = new TreeSet<>();
        for (Map<String, Object> row : Sql.rows(
                connection, "SELECT slug, segment_index FROM posting WHERE value = ?", Bitops.toSigned64(value))) {
            found.add(ref(row));
        }
        return List.copyOf(found);
    }

    @Override
    public int documentFrequency(long value) {
        return (int)
                Sql.scalar(connection, "SELECT COUNT(*) AS n FROM posting WHERE value = ?", Bitops.toSigned64(value));
    }

    @Override
    public List<InstrumentKey> instruments() {
        return Sql.rows(connection, "SELECT DISTINCT slug FROM segment ORDER BY slug").stream()
                .map(row -> InstrumentKey.parse(String.valueOf(row.get("slug"))))
                .toList();
    }

    @Override
    public IndexStats stats() {
        return new IndexStats(
                instruments().size(),
                (int) Sql.scalar(connection, "SELECT COUNT(*) AS n FROM segment"),
                (int) Sql.scalar(connection, "SELECT COUNT(DISTINCT band_key) AS n FROM band"),
                (int) Sql.scalar(connection, "SELECT COUNT(DISTINCT value) AS n FROM posting"),
                digest());
    }

    /** A stable digest of an instrument's raw text, as 16 hex digits. */
    public static String contentDigest(Instrument instrument) {
        return Hashing.hex(Hashing.hashBytes(instrument.rawText().getBytes(StandardCharsets.UTF_8)));
    }

    private static SegmentRef ref(Map<String, Object> row) {
        return new SegmentRef(
                InstrumentKey.parse(String.valueOf(row.get("slug"))), ((Number) row.get("segment_index")).intValue());
    }
}
