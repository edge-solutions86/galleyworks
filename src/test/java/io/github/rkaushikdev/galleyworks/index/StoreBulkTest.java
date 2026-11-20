package io.github.rkaushikdev.galleyworks.index;

import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.fingerprints;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.galleyworks.EngineConfig;
import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.LshConfig;
import io.github.rkaushikdev.galleyworks.StoreError;
import io.github.rkaushikdev.galleyworks.fingerprint.Fingerprinter;
import io.github.rkaushikdev.galleyworks.fingerprint.SegmentFingerprint;
import io.github.rkaushikdev.galleyworks.support.GoldenCorpus;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StoreBulkTest {

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

    private static List<SegmentFingerprint> records() {
        return fingerprints(makeBill("wy", "HB0142", MODEL_CLAUSE));
    }

    @Test
    void aSingleAddStillCommits() {
        SegmentRef ref = store.add(records().getFirst());
        assertThat(store.record(ref).path()).isNotEmpty();
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void addAllWritesEverything() {
        int written = store.addAll(records());
        assertThat(written).isEqualTo(store.size()).isGreaterThan(1);
    }

    @Test
    void aBulkBlockCommitsAtTheEnd() {
        store.bulk(() -> records().forEach(store::add));
        assertThat(store.size()).isEqualTo(records().size());
    }

    @Test
    void aFailingBulkBlockRollsBack() {
        assertThatThrownBy(() -> store.bulk(() -> {
                    store.add(records().getFirst());
                    throw new IllegalStateException("boom");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("boom");
        assertThat(store.size()).isZero();
    }

    @Test
    void bulkBlocksCannotNest() {
        store.bulk(() -> assertThatThrownBy(() -> store.bulk(() -> {}))
                .isInstanceOf(StoreError.class)
                .hasMessageContaining("cannot be nested"));
    }

    @Test
    void theIndexIsConsistentAfterABulkLoad() {
        Fingerprinter fingerprinter = new Fingerprinter();
        store.bulk(() -> {
            for (Instrument instrument : GoldenCorpus.goldenInstruments()) {
                fingerprinter.fingerprint(instrument).forEach(store::add);
            }
        });
        assertThat(Maintenance.checkIntegrity(store).isClean()).isTrue();
        assertThat(store.instruments()).hasSize(6);
    }

    @Test
    void aBadSignatureWidthStillFails() {
        EngineConfig narrow = new EngineConfig().withLsh(new LshConfig(64, 16, 0x5EED));
        SegmentFingerprint bad = new Fingerprinter(narrow)
                .fingerprint(makeBill("wy", "HB0142", MODEL_CLAUSE))
                .getFirst();
        assertThatThrownBy(() -> store.add(bad))
                .isInstanceOf(StoreError.class)
                .hasMessageContaining("does not match the index");
    }

    @Test
    void aReplacementInsideABulkBlock() {
        SegmentFingerprint first = records().getFirst();
        store.bulk(() -> {
            store.add(first);
            store.add(first);
        });
        assertThat(store.size()).isEqualTo(1);
        assertThat(store.record(SegmentRef.of(first)).path()).isEqualTo(first.path());
    }

    @Test
    void theDataSurvivesReopening() {
        Path path = tmp.resolve("reopened.sqlite");
        int expected;
        try (SqliteIndex first = new SqliteIndex(path)) {
            first.addAll(records());
            expected = first.size();
        }
        try (SqliteIndex reopened = new SqliteIndex(path)) {
            assertThat(reopened.size()).isEqualTo(expected);
        }
    }

    @Test
    void anEmptyBulkBlockIsHarmless() {
        store.bulk(() -> {});
        assertThat(store.size()).isZero();
    }
}
