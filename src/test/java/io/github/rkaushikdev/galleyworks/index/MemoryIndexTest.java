package io.github.rkaushikdev.galleyworks.index;

import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.LOCAL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.fingerprints;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.galleyworks.EngineConfig;
import io.github.rkaushikdev.galleyworks.LshConfig;
import io.github.rkaushikdev.galleyworks.StoreError;
import io.github.rkaushikdev.galleyworks.fingerprint.SegmentFingerprint;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MemoryIndexTest {

    static List<SegmentFingerprint> wyoming() {
        return fingerprints(makeBill("wy", "HB0142", MODEL_CLAUSE));
    }

    static List<SegmentFingerprint> montana() {
        return fingerprints(makeBill("mt", "HB0311", MODEL_CLAUSE));
    }

    static List<SegmentFingerprint> unrelated() {
        return fingerprints(makeBill("co", "SB0007", LOCAL_CLAUSE));
    }

    static SegmentFingerprint subsection(List<SegmentFingerprint> records) {
        return records.stream()
                .filter(record -> record.path().equals("1(a)"))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void addingRecordsGrowsTheIndex() {
        MemoryIndex index = new MemoryIndex();
        int added = index.addAll(wyoming());
        assertThat(added).isEqualTo(index.size()).isPositive();
    }

    @Test
    void aReferenceNamesItsSegment() {
        MemoryIndex index = new MemoryIndex();
        SegmentRef ref = index.add(wyoming().getFirst());
        assertThat(ref.slug()).startsWith("wy/2025/lower/HB0142#");
        assertThat(index.contains(ref)).isTrue();
    }

    @Test
    void recordsCanBeReadBack() {
        MemoryIndex index = new MemoryIndex();
        SegmentFingerprint record = wyoming().getFirst();
        assertThat(index.record(index.add(record))).isEqualTo(record);
    }

    @Test
    void anUnknownReferenceIsAnError() {
        MemoryIndex index = new MemoryIndex();
        SegmentRef ref = new SegmentRef(wyoming().getFirst().key(), 99);
        assertThatThrownBy(() -> index.record(ref))
                .isInstanceOf(StoreError.class)
                .hasMessageContaining("no such segment");
    }

    @Test
    void addingTheSameSegmentTwiceReplacesIt() {
        MemoryIndex index = new MemoryIndex();
        SegmentFingerprint record = wyoming().getFirst();
        index.add(record);
        index.add(record);
        assertThat(index.size()).isEqualTo(1);
    }

    @Test
    void aSignatureOfTheWrongWidthIsRejected() {
        MemoryIndex index = new MemoryIndex(new EngineConfig().withLsh(new LshConfig(64, 16, 0x5EED)));
        assertThatThrownBy(() -> index.add(wyoming().getFirst()))
                .isInstanceOf(StoreError.class)
                .hasMessageContaining("does not match the index");
    }

    @Test
    void aCopiedClauseIsACandidate() {
        MemoryIndex index = new MemoryIndex();
        index.addAll(wyoming());
        assertThat(index.candidates(subsection(montana()))).isNotEmpty();
    }

    @Test
    void anUnrelatedClauseIsNotACandidate() {
        MemoryIndex index = new MemoryIndex();
        index.addAll(wyoming());
        assertThat(index.candidates(subsection(unrelated()))).isEmpty();
    }

    @Test
    void aRecordIsNeverItsOwnCandidate() {
        MemoryIndex index = new MemoryIndex();
        List<SegmentFingerprint> records = wyoming();
        index.addAll(records);
        assertThat(index.candidates(records.getFirst())).doesNotContain(SegmentRef.of(records.getFirst()));
    }

    @Test
    void postingsFindSharedFingerprints() {
        MemoryIndex index = new MemoryIndex();
        index.addAll(wyoming());
        List<SegmentRef> hits = new ArrayList<>();
        for (long value : subsection(montana()).fingerprintValues()) {
            hits.addAll(index.postings(value));
        }
        assertThat(hits).isNotEmpty();
    }

    @Test
    void documentFrequencyCountsSegments() {
        MemoryIndex index = new MemoryIndex();
        List<SegmentFingerprint> records = wyoming();
        index.addAll(records);
        long value = records.getFirst().fingerprintValues().getFirst();
        assertThat(index.documentFrequency(value)).isGreaterThanOrEqualTo(1);
        assertThat(index.documentFrequency(0)).isZero();
    }

    @Test
    void removingAnInstrumentClearsItsPostings() {
        MemoryIndex index = new MemoryIndex();
        List<SegmentFingerprint> records = wyoming();
        index.addAll(records);
        long value = records.getFirst().fingerprintValues().getFirst();
        assertThat(index.removeInstrument(records.getFirst().key())).isEqualTo(records.size());
        assertThat(index.size()).isZero();
        assertThat(index.postings(value)).isEmpty();
    }

    @Test
    void removingAnAbsentInstrumentIsANoOp() {
        MemoryIndex index = new MemoryIndex();
        index.addAll(wyoming());
        assertThat(index.removeInstrument(unrelated().getFirst().key())).isZero();
    }

    @Test
    void removingOneInstrumentLeavesTheOther() {
        MemoryIndex index = new MemoryIndex();
        index.addAll(wyoming());
        index.addAll(montana());
        index.removeInstrument(wyoming().getFirst().key());
        assertThat(index.instruments()).containsExactly(montana().getFirst().key());
    }

    @Test
    void statsSummariseTheIndex() {
        MemoryIndex index = new MemoryIndex();
        index.addAll(wyoming());
        index.addAll(unrelated());
        IndexStats stats = index.stats();
        assertThat(stats.instruments()).isEqualTo(2);
        assertThat(stats.segments()).isEqualTo(index.size());
        assertThat(stats.buckets()).isPositive();
        assertThat(stats.digest()).isEqualTo(index.digest());
    }

    @Test
    void iterationYieldsEveryRecord() {
        MemoryIndex index = new MemoryIndex();
        index.addAll(wyoming());
        List<SegmentFingerprint> all = new ArrayList<>();
        index.forEach(all::add);
        assertThat(all).hasSize(index.size());
    }
}
