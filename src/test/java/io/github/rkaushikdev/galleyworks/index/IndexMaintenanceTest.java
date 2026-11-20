package io.github.rkaushikdev.galleyworks.index;

import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.fingerprints;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.fingerprint.SegmentFingerprint;
import io.github.rkaushikdev.galleyworks.index.Maintenance.CompactionReport;
import io.github.rkaushikdev.galleyworks.index.Maintenance.IntegrityReport;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IndexMaintenanceTest {

    @TempDir
    Path tmp;

    private SqliteIndex store;

    @BeforeEach
    void open() {
        store = new SqliteIndex(tmp.resolve("index.sqlite"));
        store.addAll(fingerprints(makeBill("wy", "HB0142", MODEL_CLAUSE)));
    }

    @AfterEach
    void close() {
        store.close();
    }

    private void sql(String statement) {
        Sql.execute(store.connection(), statement);
    }

    @Test
    void aFreshIndexIsClean() {
        IntegrityReport report = Maintenance.checkIntegrity(store);
        assertThat(report.isClean()).isTrue();
        assertThat(report.segments()).isPositive();
        assertThat(report.summary()).contains("no problems found");
    }

    @Test
    void anEmptyIndexIsClean() {
        try (SqliteIndex index = new SqliteIndex(tmp.resolve("empty.sqlite"))) {
            assertThat(Maintenance.checkIntegrity(index).isClean()).isTrue();
        }
    }

    @Test
    void anOrphanBandIsFound() {
        sql("INSERT INTO band (band_key, slug, segment_index) VALUES (X'00', 'wy/2025/lower/HB0142', 99)");
        IntegrityReport report = Maintenance.checkIntegrity(store);
        assertThat(report.orphanBands()).isEqualTo(1);
        assertThat(report.isClean()).isFalse();
        assertThat(report.summary()).contains("orphan bands");
    }

    @Test
    void anOrphanPostingIsFound() {
        sql("INSERT INTO posting (value, slug, segment_index) VALUES (7, 'wy/2025/lower/HB0142', 99)");
        assertThat(Maintenance.checkIntegrity(store).orphanPostings()).isEqualTo(1);
    }

    @Test
    void aMissingBandIsFound() {
        sql("DELETE FROM band WHERE rowid = (SELECT MIN(rowid) FROM band)");
        assertThat(Maintenance.checkIntegrity(store).missingBands()).isEqualTo(1);
    }

    @Test
    void aMissingPostingIsFound() {
        sql("DELETE FROM posting WHERE rowid = (SELECT MIN(rowid) FROM posting)");
        assertThat(Maintenance.checkIntegrity(store).missingPostings()).isEqualTo(1);
    }

    @Test
    void anUnreadableRecordIsFound() {
        sql("UPDATE segment SET blob = X'00'");
        IntegrityReport report = Maintenance.checkIntegrity(store);
        assertThat(report.unreadable()).isEqualTo(report.segments());
        assertThat(report.isClean()).isFalse();
    }

    @Test
    void compactionRepairsAMissingBand() {
        sql("DELETE FROM band");
        assertThat(Maintenance.checkIntegrity(store).missingBands()).isPositive();
        Maintenance.compact(store);
        assertThat(Maintenance.checkIntegrity(store).isClean()).isTrue();
    }

    @Test
    void compactionRemovesOrphans() {
        sql("INSERT INTO posting (value, slug, segment_index) VALUES (7, 'wy/2025/lower/HB0142', 99)");
        Maintenance.compact(store);
        assertThat(Maintenance.checkIntegrity(store).isClean()).isTrue();
    }

    @Test
    void compactionReportsWhatItDid() {
        CompactionReport report = Maintenance.compact(store);
        assertThat(report.rebuiltSegments()).isEqualTo(store.size());
        assertThat(report.removedBands()).isPositive();
        assertThat(report.bytesBefore()).isPositive();
        assertThat(report.bytesReclaimed()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void theIndexStillAnswersAfterCompaction() {
        Maintenance.compact(store);
        SegmentFingerprint probe = fingerprints(makeBill("mt", "HB0311", MODEL_CLAUSE)).stream()
                .filter(record -> record.path().equals("1(a)"))
                .findFirst()
                .orElseThrow();
        assertThat(store.candidates(probe)).isNotEmpty();
    }

    @Test
    void compactingAnEmptyIndex() {
        try (SqliteIndex index = new SqliteIndex(tmp.resolve("empty.sqlite"))) {
            assertThat(Maintenance.compact(index).rebuiltSegments()).isZero();
        }
    }
}
