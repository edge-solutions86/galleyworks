package io.github.rkaushikdev.galleyworks.index;

import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.fingerprints;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.Chamber;
import io.github.rkaushikdev.galleyworks.EngineConfig;
import io.github.rkaushikdev.galleyworks.InstrumentKey;
import io.github.rkaushikdev.galleyworks.LshConfig;
import io.github.rkaushikdev.galleyworks.fingerprint.SegmentFingerprint;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IndexBaseTest {

    @TempDir
    Path tmp;

    private static List<SegmentFingerprint> wyoming() {
        return fingerprints(makeBill("wy", "HB0142", MODEL_CLAUSE));
    }

    @Test
    void bothBackendsSatisfyTheProtocol() {
        assertThat(new MemoryIndex()).isInstanceOf(Index.class);
        try (SqliteIndex store = SqliteIndex.inMemory(new EngineConfig())) {
            assertThat(store).isInstanceOf(Index.class);
        }
    }

    @Test
    void openIndexWithoutAPathIsInMemory() {
        assertThat(Index.open()).isInstanceOf(MemoryIndex.class);
    }

    @Test
    void openIndexWithAPathIsDurable() {
        Index index = Index.open(Optional.of(tmp.resolve("index.sqlite")), new EngineConfig());
        assertThat(index).isInstanceOf(SqliteIndex.class);
        ((SqliteIndex) index).close();
    }

    @Test
    void openIndexPassesTheConfigThrough() {
        EngineConfig config = new EngineConfig().withLsh(new LshConfig(64, 16, 0x5EED));
        assertThat(Index.open(Optional.empty(), config).config()).isEqualTo(config);
    }

    @Test
    void segmentRefsRoundTripThroughTheirSlug() {
        SegmentRef ref = new SegmentRef(new InstrumentKey("wy", "2025", Chamber.LOWER, "HB0142"), 7);
        assertThat(ref.slug()).isEqualTo("wy/2025/lower/HB0142#7");
        assertThat(SegmentRef.parse(ref.slug())).isEqualTo(ref);
    }

    @Test
    void segmentRefsSortByKeyThenIndex() {
        InstrumentKey key = new InstrumentKey("wy", "2025", Chamber.LOWER, "HB0142");
        List<SegmentRef> refs = new ArrayList<>(List.of(new SegmentRef(key, 4), new SegmentRef(key, 1)));
        refs.sort(null);
        assertThat(refs).containsExactly(new SegmentRef(key, 1), new SegmentRef(key, 4));
    }

    @Test
    void aRefCanBeBuiltFromARecord() {
        SegmentFingerprint record = wyoming().getFirst();
        assertThat(SegmentRef.of(record).key()).isEqualTo(record.key());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void theTwoBackendsAnswerAlike(boolean durable) {
        Index index =
                Index.open(durable ? Optional.of(tmp.resolve("index.sqlite")) : Optional.empty(), new EngineConfig());
        List<SegmentFingerprint> records = wyoming();
        assertThat(index.addAll(records)).isEqualTo(records.size());
        assertThat(index.size()).isEqualTo(records.size());
        assertThat(index.instruments()).containsExactly(records.getFirst().key());
        SegmentRef ref = SegmentRef.of(records.getFirst());
        assertThat(index.record(ref).path()).isEqualTo(records.getFirst().path());
        assertThat(index.stats().segments()).isEqualTo(records.size());
        assertThat(index.removeInstrument(records.getFirst().key())).isEqualTo(records.size());
        assertThat(index.size()).isZero();
        if (index instanceof SqliteIndex store) {
            store.close();
        }
    }
}
