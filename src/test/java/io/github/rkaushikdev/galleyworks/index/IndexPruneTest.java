package io.github.rkaushikdev.galleyworks.index;

import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_FINDINGS;
import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_OK;
import static io.github.rkaushikdev.galleyworks.support.Cli.run;
import static io.github.rkaushikdev.galleyworks.support.GoldenCorpus.goldenInstruments;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.fingerprint.Codec;
import io.github.rkaushikdev.galleyworks.fingerprint.Fingerprinter;
import io.github.rkaushikdev.galleyworks.fingerprint.SegmentFingerprint;
import io.github.rkaushikdev.galleyworks.support.Cli;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IndexPruneTest {

    @TempDir
    Path tmp;

    private SqliteIndex store;

    @BeforeEach
    void store() {
        store = new SqliteIndex(tmp.resolve("index.sqlite"));
        Fingerprinter fingerprinter = new Fingerprinter();
        store.bulk(() -> {
            for (Instrument instrument : goldenInstruments().subList(0, 2)) {
                store.register(instrument);
                fingerprinter.fingerprint(instrument).forEach(store::add);
            }
        });
    }

    @AfterEach
    void close() {
        store.close();
    }

    /** Empty the fingerprints of one stored segment, returning its {@code slug#index}. */
    private static String blankOneSegment(SqliteIndex index) {
        Map<String, Object> row = Sql.first(index.connection(), "SELECT slug, segment_index, blob FROM segment LIMIT 1")
                .orElseThrow();
        SegmentFingerprint record = Codec.decodeRecord((byte[]) row.get("blob"));
        SegmentFingerprint blank = new SegmentFingerprint(
                record.key(),
                record.segmentIndex(),
                record.path(),
                record.tokenCount(),
                record.signature(),
                record.simhash(),
                List.of(),
                record.canonical(),
                record.contentRatio());
        Sql.execute(
                index.connection(),
                "UPDATE segment SET blob = ? WHERE slug = ? AND segment_index = ?",
                Codec.encodeRecord(blank),
                row.get("slug"),
                row.get("segment_index"));
        return row.get("slug") + "#" + row.get("segment_index");
    }

    @Test
    void aHealthyIndexHasNothingToPrune() {
        Prune.PruneReport report = Prune.findPrunable(store);
        assertThat(report.isClean()).isTrue();
        assertThat(report.summary()).isEqualTo("nothing to prune");
        assertThat(report.total()).isZero();
    }

    @Test
    void aSegmentWithNoFingerprintsIsFound() {
        String slug = blankOneSegment(store);
        Prune.PruneReport report = Prune.findPrunable(store);
        assertThat(report.emptySegments()).containsExactly(slug);
        assertThat(report.isClean()).isFalse();
    }

    @Test
    void anInstrumentWithNoSegmentsIsFound() {
        Sql.execute(store.connection(), "DELETE FROM segment WHERE slug = 'wy/2023/lower/HB0142'");
        assertThat(Prune.findPrunable(store).emptyInstruments()).contains("wy/2023/lower/HB0142");
    }

    @Test
    void pruningRemovesTheSegment() {
        blankOneSegment(store);
        int before = store.size();
        assertThat(Prune.prune(store).total()).isEqualTo(1);
        assertThat(store.size()).isEqualTo(before - 1);
        assertThat(Prune.findPrunable(store).isClean()).isTrue();
    }

    @Test
    void pruningRemovesTheInstrumentRow() {
        Sql.execute(store.connection(), "DELETE FROM segment WHERE slug = 'wy/2023/lower/HB0142'");
        Prune.prune(store);
        assertThat(Sql.scalar(store.connection(), "SELECT COUNT(*) AS n FROM instrument"))
                .isEqualTo(1);
    }

    @Test
    void pruningLeavesTheIndexConsistent() {
        blankOneSegment(store);
        Prune.prune(store);
        assertThat(Maintenance.checkIntegrity(store).isClean()).isTrue();
    }

    @Test
    void pruningACleanIndexChangesNothing() {
        int before = store.size();
        assertThat(Prune.prune(store).isClean()).isTrue();
        assertThat(store.size()).isEqualTo(before);
    }

    @Test
    void theSummaryCountsBothKinds() {
        blankOneSegment(store);
        assertThat(Prune.findPrunable(store).summary()).contains("segments with no fingerprints");
    }

    @Test
    void theCheckCommandReportsPrunableRows() {
        Path path = tmp.resolve("other.sqlite");
        try (SqliteIndex index = new SqliteIndex(path)) {
            index.addAll(new Fingerprinter().fingerprint(goldenInstruments().getFirst()));
        }
        Cli.Result result = run("check", "--index", path.toString());
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(result.output()).contains("nothing to prune");
    }

    @Test
    void theCheckCommandCanPrune() {
        Path path = tmp.resolve("other.sqlite");
        try (SqliteIndex index = new SqliteIndex(path)) {
            index.addAll(new Fingerprinter().fingerprint(goldenInstruments().getFirst()));
            blankOneSegment(index);
        }
        Cli.Result result = run("check", "--prune", "--index", path.toString());
        assertThat(result.code()).isIn(EXIT_OK, EXIT_FINDINGS);
        assertThat(result.output()).contains("pruned 1 rows");
    }
}
