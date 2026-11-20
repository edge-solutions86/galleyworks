package io.github.rkaushikdev.galleyworks.index;

import io.github.rkaushikdev.galleyworks.fingerprint.Codec;
import io.github.rkaushikdev.galleyworks.util.Log;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;

/**
 * Removing rows that cannot contribute to an answer: segments with no winnowed fingerprints, which
 * can never appear in a candidate list, and instrument rows with no segments left.
 *
 * <p>Pruning is separate from compaction because it removes information. Compaction rebuilds
 * derived tables and is always safe; this drops rows, so it is opt in and it reports what it took.
 */
public final class Prune {

    private static final Logger LOG = Log.get("index.prune");

    private Prune() {}

    /**
     * What pruning removed, or would remove, each list sorted.
     *
     * @param emptySegments {@code slug#index} of every segment with no fingerprints
     * @param emptyInstruments slugs of instruments with no segments
     */
    public record PruneReport(List<String> emptySegments, List<String> emptyInstruments) {

        /** Copies the lists. */
        public PruneReport {
            emptySegments = List.copyOf(emptySegments);
            emptyInstruments = List.copyOf(emptyInstruments);
        }

        /** Rows found. */
        public int total() {
            return emptySegments.size() + emptyInstruments.size();
        }

        /** True when there is nothing to prune. */
        public boolean isClean() {
            return total() == 0;
        }

        /** One line an operator can read. */
        public String summary() {
            if (isClean()) {
                return "nothing to prune";
            }
            return emptySegments.size() + " segments with no fingerprints, " + emptyInstruments.size()
                    + " instruments with no segments";
        }
    }

    /** Report what pruning would remove, without removing it. */
    public static PruneReport findPrunable(SqliteIndex store) {
        List<String> segments = new ArrayList<>();
        for (Map<String, Object> row : Sql.rows(store.connection(), "SELECT slug, segment_index, blob FROM segment")) {
            if (Codec.decodeRecord((byte[]) row.get("blob")).selected().isEmpty()) {
                segments.add(row.get("slug") + "#" + row.get("segment_index"));
            }
        }
        List<String> instruments = new ArrayList<>();
        for (Map<String, Object> row : Sql.rows(
                store.connection(),
                "SELECT slug FROM instrument WHERE slug NOT IN (SELECT DISTINCT slug FROM segment)")) {
            instruments.add(String.valueOf(row.get("slug")));
        }
        segments.sort(null);
        instruments.sort(null);
        return new PruneReport(segments, instruments);
    }

    /** Remove every row that cannot contribute to an answer. */
    public static PruneReport prune(SqliteIndex store) {
        PruneReport report = findPrunable(store);
        if (report.isClean()) {
            return report;
        }
        Connection connection = store.connection();
        Sql.execute(connection, "BEGIN");
        for (String slug : report.emptySegments()) {
            int hash = slug.lastIndexOf('#');
            String instrument = slug.substring(0, hash);
            int index = Integer.parseInt(slug.substring(hash + 1));
            for (String table : List.of("band", "posting", "segment")) {
                Sql.execute(
                        connection,
                        "DELETE FROM " + table + " WHERE slug = ? AND segment_index = ?",
                        instrument,
                        index);
            }
        }
        List<Object[]> doomed = new ArrayList<>();
        for (String slug : report.emptyInstruments()) {
            doomed.add(new Object[] {slug});
        }
        Sql.executeMany(connection, "DELETE FROM instrument WHERE slug = ?", doomed);
        Sql.execute(connection, "COMMIT");
        LOG.atInfo().addKeyValue("removed", report.total()).log("index pruned");
        return report;
    }
}
