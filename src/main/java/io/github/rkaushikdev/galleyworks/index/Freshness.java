package io.github.rkaushikdev.galleyworks.index;

import io.github.rkaushikdev.galleyworks.Instrument;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Deciding what needs reindexing.
 *
 * <p>The store keeps a digest of each instrument's raw text, so a second run can index only what
 * moved. The digest is of the raw text, not the fingerprints: a settings change is already caught by
 * the engine digest on open, which refuses the index outright rather than silently reindexing half
 * of it under one configuration and half under another.
 */
public final class Freshness {

    private Freshness() {}

    /**
     * What a second run would have to do, each list sorted.
     *
     * @param unchanged slugs whose text matches the stored digest
     * @param changed slugs whose text moved
     * @param added slugs the index has never seen
     * @param removed slugs the index holds that the corpus no longer has
     */
    public record FreshnessReport(
            List<String> unchanged, List<String> changed, List<String> added, List<String> removed) {

        /** Copies the lists. */
        public FreshnessReport {
            unchanged = List.copyOf(unchanged);
            changed = List.copyOf(changed);
            added = List.copyOf(added);
            removed = List.copyOf(removed);
        }

        /** Every slug that has to be indexed again, sorted. */
        public List<String> toIndex() {
            TreeSet<String> pending = new TreeSet<>(changed);
            pending.addAll(added);
            return List.copyOf(pending);
        }

        /** True when nothing changed, arrived or left. */
        public boolean isCurrent() {
            return changed.isEmpty() && added.isEmpty() && removed.isEmpty();
        }

        /** {@code N unchanged, N changed, N new, N no longer present}. */
        public String summary() {
            return unchanged.size() + " unchanged, " + changed.size() + " changed, " + added.size() + " new, "
                    + removed.size() + " no longer present";
        }
    }

    /** The digest the store keeps for an instrument's raw text. */
    public static String contentDigest(Instrument instrument) {
        return SqliteIndex.contentDigest(instrument);
    }

    /** Store the content digest for {@code instrument}. */
    public static void recordDigest(SqliteIndex store, Instrument instrument) {
        Sql.execute(
                store.connection(),
                "UPDATE instrument SET content_digest = ? WHERE slug = ?",
                contentDigest(instrument),
                instrument.key().slug());
    }

    /** Every recorded digest, by instrument slug. */
    public static Map<String, String> storedDigests(SqliteIndex store) {
        Map<String, String> digests = new LinkedHashMap<>();
        for (Map<String, Object> row : Sql.rows(
                store.connection(), "SELECT slug, content_digest FROM instrument WHERE content_digest IS NOT NULL")) {
            digests.put(String.valueOf(row.get("slug")), String.valueOf(row.get("content_digest")));
        }
        return digests;
    }

    /** Compare a corpus against what the index already holds. */
    public static FreshnessReport planReindex(SqliteIndex store, Iterable<Instrument> instruments) {
        Map<String, String> known = storedDigests(store);
        List<String> unchanged = new ArrayList<>();
        List<String> changed = new ArrayList<>();
        List<String> added = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Instrument instrument : instruments) {
            String slug = instrument.key().slug();
            seen.add(slug);
            String recorded = known.get(slug);
            if (recorded == null) {
                added.add(slug);
            } else if (recorded.equals(contentDigest(instrument))) {
                unchanged.add(slug);
            } else {
                changed.add(slug);
            }
        }
        TreeSet<String> removed = new TreeSet<>(known.keySet());
        removed.removeAll(seen);
        return new FreshnessReport(sorted(unchanged), sorted(changed), sorted(added), List.copyOf(removed));
    }

    private static List<String> sorted(List<String> values) {
        List<String> copy = new ArrayList<>(values);
        copy.sort(null);
        return copy;
    }
}
