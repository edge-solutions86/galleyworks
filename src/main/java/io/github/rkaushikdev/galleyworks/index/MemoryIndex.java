package io.github.rkaushikdev.galleyworks.index;

import io.github.rkaushikdev.galleyworks.Context;
import io.github.rkaushikdev.galleyworks.EngineConfig;
import io.github.rkaushikdev.galleyworks.InstrumentKey;
import io.github.rkaushikdev.galleyworks.StoreError;
import io.github.rkaushikdev.galleyworks.fingerprint.SegmentFingerprint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The in-memory reference implementation of the index contract.
 *
 * <p>Three structures, all derived from the records added: the records themselves by reference,
 * band key to references (candidate generation), and fingerprint value to references (passage
 * alignment). The sqlite store implements the same operations against a file, and both are
 * exercised by the same tests, so a divergence between them is a test failure rather than a mystery
 * in production.
 */
public final class MemoryIndex implements Index {

    private final EngineConfig config;
    private final Map<SegmentRef, SegmentFingerprint> records = new LinkedHashMap<>();
    private final Map<BandKey, Set<SegmentRef>> buckets = new HashMap<>();
    private final Map<Long, Set<SegmentRef>> postings = new HashMap<>();

    /** An empty index under the default configuration. */
    public MemoryIndex() {
        this(new EngineConfig());
    }

    /** An empty index under {@code config}. */
    public MemoryIndex(EngineConfig config) {
        this.config = config;
    }

    @Override
    public EngineConfig config() {
        return config;
    }

    @Override
    public int size() {
        return records.size();
    }

    /** True when {@code ref} is indexed. */
    public boolean contains(SegmentRef ref) {
        return records.containsKey(ref);
    }

    @Override
    public Iterator<SegmentFingerprint> iterator() {
        return List.copyOf(records.values()).iterator();
    }

    @Override
    public SegmentRef add(SegmentFingerprint record) {
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
        if (records.containsKey(ref)) {
            detach(ref);
        }
        records.put(ref, record);
        for (BandKey key : Lsh.bandKeys(record.signature(), config.lsh())) {
            buckets.computeIfAbsent(key, ignored -> new HashSet<>()).add(ref);
        }
        for (long value : record.fingerprintValues()) {
            postings.computeIfAbsent(value, ignored -> new HashSet<>()).add(ref);
        }
        return ref;
    }

    @Override
    public int addAll(Iterable<SegmentFingerprint> batch) {
        int written = 0;
        for (SegmentFingerprint record : batch) {
            add(record);
            written++;
        }
        return written;
    }

    private void detach(SegmentRef ref) {
        SegmentFingerprint record = records.remove(ref);
        for (BandKey key : Lsh.bandKeys(record.signature(), config.lsh())) {
            Set<SegmentRef> bucket = buckets.get(key);
            if (bucket != null) {
                bucket.remove(ref);
                if (bucket.isEmpty()) {
                    buckets.remove(key);
                }
            }
        }
        for (long value : record.fingerprintValues()) {
            Set<SegmentRef> posting = postings.get(value);
            if (posting != null) {
                posting.remove(ref);
                if (posting.isEmpty()) {
                    postings.remove(value);
                }
            }
        }
    }

    @Override
    public int removeInstrument(InstrumentKey key) {
        List<SegmentRef> doomed =
                records.keySet().stream().filter(ref -> ref.key().equals(key)).toList();
        doomed.forEach(this::detach);
        return doomed.size();
    }

    @Override
    public SegmentFingerprint record(SegmentRef ref) {
        SegmentFingerprint found = records.get(ref);
        if (found == null) {
            throw new StoreError("no such segment in the index", Context.of("ref", ref.slug()));
        }
        return found;
    }

    @Override
    public List<SegmentRef> candidates(SegmentFingerprint record) {
        Set<SegmentRef> found = new TreeSet<>();
        for (BandKey key : Lsh.bandKeys(record.signature(), config.lsh())) {
            found.addAll(buckets.getOrDefault(key, Set.of()));
        }
        found.remove(SegmentRef.of(record));
        return List.copyOf(found);
    }

    @Override
    public List<SegmentRef> postings(long value) {
        return List.copyOf(new TreeSet<>(postings.getOrDefault(value, Set.of())));
    }

    @Override
    public int documentFrequency(long value) {
        return postings.getOrDefault(value, Set.of()).size();
    }

    @Override
    public List<InstrumentKey> instruments() {
        return new ArrayList<>(
                new TreeSet<>(records.keySet().stream().map(SegmentRef::key).toList()));
    }

    @Override
    public IndexStats stats() {
        return new IndexStats(instruments().size(), records.size(), buckets.size(), postings.size(), digest());
    }
}
