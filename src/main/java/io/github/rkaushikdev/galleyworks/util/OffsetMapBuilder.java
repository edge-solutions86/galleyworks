package io.github.rkaushikdev.galleyworks.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulates the pieces of an {@link OffsetMap} while a rewriter walks the source text.
 *
 * <p>It is driven by the same four moves every rewriter makes: keep, drop, insert, replace.
 */
public final class OffsetMapBuilder {

    private final List<Piece> pieces = new ArrayList<>();
    private int source;
    private int target;

    /** Where the next move starts in the source text. */
    public int sourceCursor() {
        return source;
    }

    /** Where the next move starts in the rewritten text. */
    public int targetCursor() {
        return target;
    }

    /** Copy {@code length} characters through unchanged. */
    public void keep(int length) {
        advance(length, length);
    }

    /** Consume {@code length} source characters that produce no output. */
    public void drop(int length) {
        advance(length, 0);
    }

    /** Emit {@code length} target characters with no source behind them. */
    public void insert(int length) {
        advance(0, length);
    }

    /** Consume and emit different amounts of text. */
    public void replace(int sourceLength, int targetLength) {
        advance(sourceLength, targetLength);
    }

    private void advance(int sourceLength, int targetLength) {
        if (sourceLength < 0 || targetLength < 0) {
            throw new IllegalArgumentException("offset map runs cannot be negative");
        }
        if (sourceLength == 0 && targetLength == 0) {
            return;
        }
        Piece piece =
                new Piece(new TextSpan(source, source + sourceLength), new TextSpan(target, target + targetLength));
        source = piece.source().end();
        target = piece.target().end();
        pieces.add(piece);
    }

    /** Append pieces built elsewhere, moving the cursors past them. */
    public void extend(Iterable<Piece> more) {
        for (Piece piece : more) {
            pieces.add(piece);
            source = Math.max(source, piece.source().end());
            target = Math.max(target, piece.target().end());
        }
    }

    /** The map, with a source length equal to the source cursor. */
    public OffsetMap build() {
        return new OffsetMap(pieces, source, target);
    }

    /** The map, declaring a source length that may cover a trailing tail. */
    public OffsetMap build(int sourceLength) {
        return new OffsetMap(pieces, sourceLength, target);
    }
}
