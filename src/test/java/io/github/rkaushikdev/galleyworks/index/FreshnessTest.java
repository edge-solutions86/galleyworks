package io.github.rkaushikdev.galleyworks.index;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.fingerprint.Fingerprinter;
import io.github.rkaushikdev.galleyworks.index.Freshness.FreshnessReport;
import io.github.rkaushikdev.galleyworks.support.GoldenCorpus;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FreshnessTest {

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

    private void load(List<Instrument> instruments) {
        Fingerprinter fingerprinter = new Fingerprinter();
        store.bulk(() -> {
            for (Instrument instrument : instruments) {
                store.register(instrument);
                fingerprinter.fingerprint(instrument).forEach(store::add);
            }
        });
    }

    private static List<Instrument> editedMontana(List<Instrument> instruments) {
        List<Instrument> edited = new ArrayList<>(instruments);
        edited.set(1, edited.get(1).withRawText(edited.get(1).rawText().replace("sixty", "ninety")));
        return edited;
    }

    @Test
    void aDigestIsStable() {
        Instrument one = GoldenCorpus.goldenInstruments().getFirst();
        assertThat(Freshness.contentDigest(one))
                .isEqualTo(
                        Freshness.contentDigest(GoldenCorpus.goldenInstruments().getFirst()))
                .hasSize(16);
    }

    @Test
    void aChangedTextChangesTheDigest() {
        Instrument one = GoldenCorpus.goldenInstruments().getFirst();
        Instrument other = one.withRawText(one.rawText() + " ");
        assertThat(Freshness.contentDigest(one)).isNotEqualTo(Freshness.contentDigest(other));
    }

    @Test
    void anEmptyIndexNeedsEverything() {
        FreshnessReport report = Freshness.planReindex(store, GoldenCorpus.goldenInstruments());
        assertThat(report.added()).hasSize(6);
        assertThat(report.unchanged()).isEmpty();
        assertThat(report.isCurrent()).isFalse();
        assertThat(report.toIndex()).hasSize(6);
    }

    @Test
    void aLoadedIndexIsCurrent() {
        List<Instrument> instruments = GoldenCorpus.goldenInstruments();
        load(instruments);
        FreshnessReport report = Freshness.planReindex(store, instruments);
        assertThat(report.isCurrent()).isTrue();
        assertThat(report.unchanged()).hasSize(6);
        assertThat(report.toIndex()).isEmpty();
    }

    @Test
    void aChangedDocumentIsDetected() {
        List<Instrument> instruments = GoldenCorpus.goldenInstruments();
        load(instruments);
        FreshnessReport report = Freshness.planReindex(store, editedMontana(instruments));
        assertThat(report.changed()).containsExactly("mt/2024/lower/HB0311");
        assertThat(report.toIndex()).containsExactly("mt/2024/lower/HB0311");
    }

    @Test
    void aNewDocumentIsDetected() {
        List<Instrument> instruments = GoldenCorpus.goldenInstruments();
        load(instruments.subList(0, 5));
        FreshnessReport report = Freshness.planReindex(store, instruments);
        assertThat(report.added()).containsExactly("cheyenne/2025/council/2025-08");
        assertThat(report.unchanged()).hasSize(5);
    }

    @Test
    void aRemovedDocumentIsDetected() {
        List<Instrument> instruments = GoldenCorpus.goldenInstruments();
        load(instruments);
        FreshnessReport report = Freshness.planReindex(store, instruments.subList(0, 5));
        assertThat(report.removed()).containsExactly("cheyenne/2025/council/2025-08");
        assertThat(report.isCurrent()).isFalse();
    }

    @Test
    void theSummaryReadsClearly() {
        List<Instrument> instruments = GoldenCorpus.goldenInstruments();
        load(instruments);
        assertThat(Freshness.planReindex(store, instruments).summary()).contains("6 unchanged");
    }

    @Test
    void aDigestCanBeRecordedAfterTheFact() {
        Instrument instrument = GoldenCorpus.goldenInstruments().getFirst();
        store.addAll(new Fingerprinter().fingerprint(instrument));
        assertThat(Freshness.planReindex(store, List.of(instrument)).added()).isNotEmpty();
        Freshness.recordDigest(store, instrument);
        assertThat(Freshness.planReindex(store, List.of(instrument)).isCurrent())
                .isTrue();
    }

    @Test
    void reindexingOnlyWhatChanged() {
        List<Instrument> instruments = GoldenCorpus.goldenInstruments();
        load(instruments);
        List<Instrument> edited = editedMontana(instruments);
        FreshnessReport plan = Freshness.planReindex(store, edited);
        Fingerprinter fingerprinter = new Fingerprinter();
        for (Instrument instrument : edited) {
            if (plan.toIndex().contains(instrument.key().slug())) {
                store.removeInstrument(instrument.key());
                store.register(instrument);
                store.addAll(fingerprinter.fingerprint(instrument));
            }
        }
        assertThat(Freshness.planReindex(store, edited).isCurrent()).isTrue();
        assertThat(store.instruments()).hasSize(6);
    }
}
