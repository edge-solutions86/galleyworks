package io.github.rkaushikdev.galleyworks.index;

import io.github.rkaushikdev.galleyworks.EngineConfig;
import io.github.rkaushikdev.galleyworks.InstrumentKey;
import io.github.rkaushikdev.galleyworks.fingerprint.SegmentFingerprint;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * What the matcher needs from an index, whichever backend provides it. Iterating an index yields
 * every record it holds.
 */
public interface Index extends Iterable<SegmentFingerprint> {

    /** The configuration the index was built under. */
    EngineConfig config();

    /** Number of indexed segments. */
    int size();

    /** Index one record, replacing any earlier record for that segment. */
    SegmentRef add(SegmentFingerprint record);

    /** Index every record, returning how many were written. */
    int addAll(Iterable<SegmentFingerprint> records);

    /** Drop every segment of one instrument, returning how many went. */
    int removeInstrument(InstrumentKey key);

    /** The record {@code ref} points at. */
    SegmentFingerprint record(SegmentRef ref);

    /** References sharing at least one band with {@code record}, itself excluded, sorted. */
    List<SegmentRef> candidates(SegmentFingerprint record);

    /** References whose winnowed fingerprints include {@code value}, sorted. */
    List<SegmentRef> postings(long value);

    /** How many segments carry the fingerprint {@code value}. */
    int documentFrequency(long value);

    /** Every instrument with at least one indexed segment. */
    List<InstrumentKey> instruments();

    /** A summary of the index. */
    IndexStats stats();

    /** The engine digest the index was built under. */
    default String digest() {
        return config().digest();
    }

    /** A durable index at {@code path}, or an in-memory one when there is none. */
    static Index open(Optional<Path> path, EngineConfig config) {
        return path.<Index>map(location -> new SqliteIndex(location, config)).orElseGet(() -> new MemoryIndex(config));
    }

    /** An in-memory index under the default configuration. */
    static Index open() {
        return open(Optional.empty(), new EngineConfig());
    }
}
