package io.github.rkaushikdev.galleyworks.index;

import io.github.rkaushikdev.galleyworks.fingerprint.Codec;
import io.github.rkaushikdev.galleyworks.fingerprint.SegmentFingerprint;
import io.github.rkaushikdev.galleyworks.util.Bitops;
import io.github.rkaushikdev.galleyworks.util.Log;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;

/**
 * Checking and compacting a sqlite index.
 *
 * <p>The band and posting tables are derived data. If a write is interrupted between deleting a
 * segment and deleting its buckets, they disagree with the segment table, and the symptom is a
 * candidate reference that cannot be dereferenced during a query, hours later. {@link
 * #checkIntegrity} looks for that disagreement in both directions, and {@link #compact} repairs it
 * by rebuilding the derived tables from the stored records, the only thing that cannot be
 * recomputed.
 */
public final class Maintenance {

    private static final Logger LOG = Log.get("index.maintenance");

    private Maintenance() {}

    /**
     * What a consistency check found.
     *
     * @param segments stored segments
     * @param orphanBands band rows with no segment
     * @param orphanPostings posting rows with no segment
     * @param missingBands band keys a record implies that are not stored
     * @param missingPostings fingerprints a record implies that are not stored
     * @param unreadable records that do not decode
     */
    public record IntegrityReport(
            int segments, int orphanBands, int orphanPostings, int missingBands, int missingPostings, int unreadable) {

        /** True when nothing disagrees. */
        public boolean isClean() {
            return orphanBands == 0
                    && orphanPostings == 0
                    && missingBands == 0
                    && missingPostings == 0
                    && unreadable == 0;
        }

        /** One line an operator can read. */
        public String summary() {
            if (isClean()) {
                return segments + " segments, no problems found";
            }
            return segments + " segments, " + orphanBands + " orphan bands, " + orphanPostings + " orphan postings, "
                    + missingBands + " missing bands, " + missingPostings + " missing postings, " + unreadable
                    + " unreadable records";
        }
    }

    /**
     * What a compaction changed.
     *
     * @param removedBands band rows dropped before the rebuild
     * @param removedPostings posting rows dropped before the rebuild
     * @param rebuiltSegments segments whose derived rows were rewritten
     * @param bytesBefore database size before
     * @param bytesAfter database size after the vacuum
     */
    public record CompactionReport(
            int removedBands, int removedPostings, int rebuiltSegments, long bytesBefore, long bytesAfter) {

        /** Bytes the vacuum gave back, never negative. */
        public long bytesReclaimed() {
            return Math.max(0, bytesBefore - bytesAfter);
        }
    }

    /** Compare the derived tables against the stored records. */
    public static IntegrityReport checkIntegrity(SqliteIndex store) {
        Connection conn = store.connection();
        int segments = (int) Sql.scalar(conn, "SELECT COUNT(*) AS n FROM segment");
        int orphanBands = (int) Sql.scalar(
                conn,
                "SELECT COUNT(*) AS n FROM band b LEFT JOIN segment s "
                        + "ON b.slug = s.slug AND b.segment_index = s.segment_index WHERE s.slug IS NULL");
        int orphanPostings = (int) Sql.scalar(
                conn,
                "SELECT COUNT(*) AS n FROM posting p LEFT JOIN segment s "
                        + "ON p.slug = s.slug AND p.segment_index = s.segment_index WHERE s.slug IS NULL");
        int missingBands = 0;
        int missingPostings = 0;
        int unreadable = 0;
        for (Map<String, Object> row : Sql.rows(conn, "SELECT slug, segment_index, blob FROM segment")) {
            SegmentFingerprint record;
            try {
                record = Codec.decodeRecord((byte[]) row.get("blob"));
            } catch (RuntimeException exc) {
                // Any decode failure is the same finding.
                unreadable++;
                continue;
            }
            Set<BandKey> expectedBands = new HashSet<>(
                    Lsh.bandKeys(record.signature(), store.config().lsh()));
            Set<BandKey> foundBands = new HashSet<>();
            for (Map<String, Object> item : Sql.rows(
                    conn,
                    "SELECT band_key FROM band WHERE slug = ? AND segment_index = ?",
                    row.get("slug"),
                    row.get("segment_index"))) {
                foundBands.add(BandKey.of((byte[]) item.get("band_key")));
            }
            expectedBands.removeAll(foundBands);
            missingBands += expectedBands.size();
            Set<Long> expectedValues = new HashSet<>();
            record.fingerprintValues().forEach(value -> expectedValues.add(Bitops.toSigned64(value)));
            Set<Long> foundValues = new HashSet<>();
            for (Map<String, Object> item : Sql.rows(
                    conn,
                    "SELECT value FROM posting WHERE slug = ? AND segment_index = ?",
                    row.get("slug"),
                    row.get("segment_index"))) {
                foundValues.add(((Number) item.get("value")).longValue());
            }
            expectedValues.removeAll(foundValues);
            missingPostings += expectedValues.size();
        }
        return new IntegrityReport(segments, orphanBands, orphanPostings, missingBands, missingPostings, unreadable);
    }

    /** Rebuild the derived tables from the records, then reclaim the space. */
    public static CompactionReport compact(SqliteIndex store) {
        Connection conn = store.connection();
        long before = sizeOf(conn);
        int removedBands = (int) Sql.scalar(conn, "SELECT COUNT(*) AS n FROM band");
        int removedPostings = (int) Sql.scalar(conn, "SELECT COUNT(*) AS n FROM posting");
        int rebuilt = 0;
        Sql.execute(conn, "BEGIN");
        Sql.execute(conn, "DELETE FROM band");
        Sql.execute(conn, "DELETE FROM posting");
        for (Map<String, Object> row : Sql.rows(conn, "SELECT slug, segment_index, blob FROM segment")) {
            SegmentFingerprint record = Codec.decodeRecord((byte[]) row.get("blob"));
            List<Object[]> bands = new ArrayList<>();
            for (BandKey key : Lsh.bandKeys(record.signature(), store.config().lsh())) {
                bands.add(new Object[] {key.bytes(), row.get("slug"), row.get("segment_index")});
            }
            Sql.executeMany(conn, "INSERT OR IGNORE INTO band (band_key, slug, segment_index) VALUES (?, ?, ?)", bands);
            List<Object[]> values = new ArrayList<>();
            for (long value : record.fingerprintValues()) {
                values.add(new Object[] {Bitops.toSigned64(value), row.get("slug"), row.get("segment_index")});
            }
            Sql.executeMany(
                    conn, "INSERT OR IGNORE INTO posting (value, slug, segment_index) VALUES (?, ?, ?)", values);
            rebuilt++;
        }
        Sql.execute(conn, "COMMIT");
        Sql.execute(conn, "VACUUM");
        long after = sizeOf(conn);
        LOG.atInfo()
                .addKeyValue("segments", rebuilt)
                .addKeyValue("before", before)
                .addKeyValue("after", after)
                .log("index compacted");
        return new CompactionReport(removedBands, removedPostings, rebuilt, before, after);
    }

    private static long sizeOf(Connection conn) {
        return Sql.scalar(conn, "PRAGMA page_count") * Sql.scalar(conn, "PRAGMA page_size");
    }
}
