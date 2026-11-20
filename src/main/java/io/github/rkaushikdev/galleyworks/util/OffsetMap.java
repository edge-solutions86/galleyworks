package io.github.rkaushikdev.galleyworks.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Translates offsets in a rewritten text back to the source text.
 *
 * <p>Canonicalization and amendment markup both rewrite text, and a match found in the rewritten
 * form has to be quoted from the source text. The map records the piecewise correspondence so that
 * translation is exact where the texts line up and honest where they do not: an inserted region
 * has no source, so it maps to the seam it was inserted at. Build one with {@link
 * OffsetMapBuilder}.
 */
public final class OffsetMap {

    private final List<Piece> pieces;
    private final int[] starts;
    private final int sourceLength;
    private final int targetLength;

    /**
     * A map over {@code pieces}, which must be ordered and must not overlap in the target text.
     */
    public OffsetMap(List<Piece> pieces, int sourceLength, int targetLength) {
        this.pieces = List.copyOf(pieces);
        this.starts =
                this.pieces.stream().mapToInt(piece -> piece.target().start()).toArray();
        this.sourceLength = sourceLength;
        this.targetLength = targetLength;
        for (int i = 1; i < this.pieces.size(); i++) {
            if (this.pieces.get(i).target().start()
                    < this.pieces.get(i - 1).target().end()) {
                throw new IllegalArgumentException("offset map pieces overlap in the target text");
            }
        }
    }

    /** The map of a text onto an unchanged copy of itself. */
    public static OffsetMap identity(int length) {
        TextSpan span = new TextSpan(0, length);
        return new OffsetMap(List.of(new Piece(span, span)), length, length);
    }

    /** The aligned runs, in target order. */
    public List<Piece> pieces() {
        return pieces;
    }

    /** Length of the source text. */
    public int sourceLength() {
        return sourceLength;
    }

    /** Length of the rewritten text. */
    public int targetLength() {
        return targetLength;
    }

    /** Map a target offset to the closest meaningful source offset. */
    public int toSource(int offset) {
        if (offset < 0 || offset > targetLength) {
            throw new IllegalArgumentException("offset " + offset + " outside target text of " + targetLength);
        }
        if (pieces.isEmpty()) {
            return 0;
        }
        int position = bisectRight(offset) - 1;
        if (position < 0) {
            return pieces.getFirst().source().start();
        }
        Piece piece = pieces.get(position);
        if (offset >= piece.target().end()) {
            return piece.source().end();
        }
        if (piece.target().length() == 0) {
            return piece.source().start();
        }
        if (piece.isAligned()) {
            return piece.source().start() + (offset - piece.target().start());
        }
        double ratio =
                (double) (offset - piece.target().start()) / piece.target().length();
        return piece.source().start() + (int) (ratio * piece.source().length());
    }

    /** Map a target span to the source; the result never inverts. */
    public TextSpan toSourceSpan(TextSpan span) {
        int start = toSource(span.start());
        int end = toSource(span.end());
        return new TextSpan(start, Math.max(start, end));
    }

    /**
     * Combine this map (a to b) with {@code later} (b to c) into a to c.
     *
     * <p>An aligned run in {@code later} is split at this map's own piece boundaries. Without that
     * the composition keeps only the coarser of the two alignments, and every offset inside a long
     * run gets interpolated across edits that the finer map knew exactly.
     */
    public OffsetMap compose(OffsetMap later) {
        if (later.sourceLength != targetLength) {
            throw new IllegalArgumentException(
                    "cannot compose offset maps: " + targetLength + " target characters against " + later.sourceLength);
        }
        List<Piece> composed = new ArrayList<>();
        for (Piece piece : later.pieces) {
            if (piece.isAligned()) {
                composed.addAll(refine(piece));
            } else {
                composed.add(new Piece(toSourceSpan(piece.source()), piece.target()));
            }
        }
        return new OffsetMap(composed, sourceLength, later.targetLength);
    }

    private List<Piece> refine(Piece piece) {
        int shift = piece.target().start() - piece.source().start();
        List<Piece> refined = new ArrayList<>();
        for (Piece mine : pieces) {
            Optional<TextSpan> found = mine.target().intersection(piece.source());
            if (found.isEmpty()) {
                continue;
            }
            TextSpan overlap = found.get();
            TextSpan target = new TextSpan(overlap.start() + shift, overlap.end() + shift);
            TextSpan source;
            if (mine.isAligned()) {
                int offset = mine.source().start() - mine.target().start();
                source = new TextSpan(overlap.start() + offset, overlap.end() + offset);
            } else {
                source = toSourceSpan(overlap);
            }
            refined.add(new Piece(source, target));
        }
        if (refined.isEmpty()) {
            return List.of(new Piece(toSourceSpan(piece.source()), piece.target()));
        }
        return refined;
    }

    private int bisectRight(int offset) {
        int low = 0;
        int high = starts.length;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (offset < starts[middle]) {
                high = middle;
            } else {
                low = middle + 1;
            }
        }
        return low;
    }
}
