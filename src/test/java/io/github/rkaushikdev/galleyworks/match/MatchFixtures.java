package io.github.rkaushikdev.galleyworks.match;

import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.fingerprints;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;

import io.github.rkaushikdev.galleyworks.Chamber;
import io.github.rkaushikdev.galleyworks.InstrumentKey;
import io.github.rkaushikdev.galleyworks.fingerprint.SegmentFingerprint;
import io.github.rkaushikdev.galleyworks.fingerprint.Shingle;
import io.github.rkaushikdev.galleyworks.fingerprint.Signature;
import io.github.rkaushikdev.galleyworks.index.MemoryIndex;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.ArrayList;
import java.util.List;

/** Hand-built records, anchors and passages the match tests share. */
final class MatchFixtures {

    static final InstrumentKey WYOMING = new InstrumentKey("wy", "2025", Chamber.LOWER, "HB0142");

    private MatchFixtures() {}

    /** A one-anchor passage whose anchor value is its query start. */
    static Passage passage(int queryStart, int queryEnd, int otherStart, int otherEnd) {
        Anchor anchor = new Anchor(
                queryStart,
                otherStart,
                queryStart,
                new TextSpan(queryStart, queryEnd),
                new TextSpan(otherStart, otherEnd));
        return new Passage(new TextSpan(queryStart, queryEnd), new TextSpan(otherStart, otherEnd), List.of(anchor));
    }

    /** A record whose selected shingles carry {@code values} at consecutive positions. */
    static SegmentFingerprint record(int index, int spacing, int width, long... values) {
        List<Shingle> selected = new ArrayList<>();
        for (int position = 0; position < values.length; position++) {
            selected.add(new Shingle(
                    values[position], position, new TextSpan(position * spacing, position * spacing + width)));
        }
        return new SegmentFingerprint(WYOMING, index, String.valueOf(index), 50, Signature.of(), 0, selected, "");
    }

    /** The subsection (a) record of a bill carrying {@code body}. */
    static SegmentFingerprint clauseRecord(String body, String jurisdiction, String number) {
        return fingerprints(makeBill(jurisdiction, number, body)).stream()
                .filter(record -> record.path().equals("1(a)"))
                .findFirst()
                .orElseThrow();
    }

    /** An index holding the fingerprints of one bill per {@code (jurisdiction, number, body)}. */
    static MemoryIndex indexed(io.github.rkaushikdev.galleyworks.EngineConfig config, String[]... bodies) {
        MemoryIndex index = new MemoryIndex(config);
        for (String[] bill : bodies) {
            index.addAll(fingerprints(makeBill(bill[0], bill[1], bill[2])));
        }
        return index;
    }
}
