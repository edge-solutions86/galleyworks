package io.github.rkaushikdev.galleyworks;

import io.github.rkaushikdev.galleyworks.canon.Profiles;
import io.github.rkaushikdev.galleyworks.canon.Tokens;
import io.github.rkaushikdev.galleyworks.util.SequenceMatcher;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Comparing two prints of the same instrument.
 *
 * <p>Reuse detection answers "who else has this text". This answers the narrower and more common
 * question: "what changed between the introduced bill and the enrolled one". Segments are paired by
 * their printed path, so {@code 1(a)(ii)} is compared with {@code 1(a)(ii)} and a renumbered
 * subsection shows up as one removal and one addition rather than as a rewrite. That is the honest
 * reading: a renumbering is a change to the instrument even when no word moved.
 */
public final class Diff {

    private Diff() {}

    /** What happened to one segment between two prints. */
    public enum ChangeKind {
        UNCHANGED("unchanged"),
        CHANGED("changed"),
        ADDED("added"),
        REMOVED("removed");

        private final String value;

        ChangeKind(String value) {
            this.value = value;
        }

        /** The lowercase name written in diff output. */
        public String value() {
            return value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    /**
     * What happened to one segment between two prints.
     *
     * @param path the printed path both prints share
     * @param kind what happened
     * @param similarity how alike the two word sequences are
     * @param wordsAdded words in the later print not matched in the earlier
     * @param wordsRemoved words in the earlier print not matched in the later
     */
    public record SegmentChange(String path, ChangeKind kind, double similarity, int wordsAdded, int wordsRemoved) {

        /** True for anything but an unchanged segment. */
        public boolean isMaterial() {
            return kind != ChangeKind.UNCHANGED;
        }

        /** Words added plus words removed. */
        public int churn() {
            return wordsAdded + wordsRemoved;
        }
    }

    /**
     * The whole comparison.
     *
     * @param left the earlier print's slug
     * @param right the later print's slug
     * @param changes one entry per path, sorted by path
     */
    public record InstrumentDiff(String left, String right, List<SegmentChange> changes) {

        /** Copies the changes. */
        public InstrumentDiff {
            changes = List.copyOf(changes);
        }

        /** True when no segment changed. */
        public boolean isIdentical() {
            return changes.stream().noneMatch(SegmentChange::isMaterial);
        }

        /** The segments that changed, in path order. */
        public List<SegmentChange> changed() {
            return changes.stream().filter(SegmentChange::isMaterial).toList();
        }

        /** How many segments fell into each kind, every kind present. */
        public Map<String, Integer> counts() {
            Map<String, Integer> found = new LinkedHashMap<>();
            for (ChangeKind kind : ChangeKind.values()) {
                found.put(kind.value(), 0);
            }
            changes.forEach(change -> found.merge(change.kind().value(), 1, Integer::sum));
            return found;
        }

        /** Churn across every segment. */
        public int totalChurn() {
            return changes.stream().mapToInt(SegmentChange::churn).sum();
        }
    }

    /**
     * The canonical words of {@code text}, keeping case. Folding case here would override the
     * profile: the verbatim profile exists so that a comparison can notice a capitalisation change.
     */
    private static List<String> canonicalWords(String text, CanonConfig config) {
        String canon = Profiles.buildPipeline().run(text, config).text();
        return Tokens.wordsOnly(Tokens.tokenize(canon)).stream()
                .map(Tokens.Token::text)
                .toList();
    }

    private static Map<String, List<String>> segments(Instrument instrument, CanonConfig config) {
        Map<String, List<String>> found = new LinkedHashMap<>();
        for (Segment segment : instrument.bodySegments()) {
            String path = segment.path().printed();
            if (!path.isEmpty()) {
                found.put(path, canonicalWords(segment.span().sliceOf(instrument.rawText()), config));
            }
        }
        return found;
    }

    /** Compare two prints under the verbatim profile. */
    public static InstrumentDiff compare(Instrument left, Instrument right) {
        return compare(left, right, Profiles.profile("verbatim"));
    }

    /** Compare two prints of one instrument, segment by segment. */
    public static InstrumentDiff compare(Instrument left, Instrument right, CanonConfig config) {
        Map<String, List<String>> before = segments(left, config);
        Map<String, List<String>> after = segments(right, config);
        TreeSet<String> paths = new TreeSet<>(before.keySet());
        paths.addAll(after.keySet());
        List<SegmentChange> changes = new ArrayList<>();
        for (String path : paths) {
            List<String> old = before.get(path);
            List<String> fresh = after.get(path);
            if (old == null) {
                changes.add(new SegmentChange(path, ChangeKind.ADDED, 0.0, fresh.size(), 0));
                continue;
            }
            if (fresh == null) {
                changes.add(new SegmentChange(path, ChangeKind.REMOVED, 0.0, 0, old.size()));
                continue;
            }
            SequenceMatcher<String> matcher = new SequenceMatcher<>(old, fresh);
            int matched = matcher.matched();
            changes.add(new SegmentChange(
                    path,
                    old.equals(fresh) ? ChangeKind.UNCHANGED : ChangeKind.CHANGED,
                    matcher.ratio(),
                    fresh.size() - matched,
                    old.size() - matched));
        }
        return new InstrumentDiff(left.key().slug(), right.key().slug(), changes);
    }
}
