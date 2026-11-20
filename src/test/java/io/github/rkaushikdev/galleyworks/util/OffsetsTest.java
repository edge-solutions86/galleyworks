package io.github.rkaushikdev.galleyworks.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class OffsetsTest {

    @Test
    void identityMapsEveryOffsetToItself() {
        OffsetMap identity = OffsetMap.identity(12);
        assertThat(IntStream.rangeClosed(0, 12).map(identity::toSource).toArray())
                .containsExactly(IntStream.rangeClosed(0, 12).toArray());
    }

    @Test
    void identityReportsItsLengths() {
        OffsetMap identity = OffsetMap.identity(9);
        assertThat(identity.sourceLength()).isEqualTo(9);
        assertThat(identity.targetLength()).isEqualTo(9);
    }

    @Test
    void offsetsOutsideTheTargetAreRejected() {
        OffsetMap identity = OffsetMap.identity(4);
        assertThatThrownBy(() -> identity.toSource(5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside target text");
        assertThatThrownBy(() -> identity.toSource(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside target text");
    }

    @Test
    void overlappingPiecesAreRejected() {
        List<Piece> pieces = List.of(
                new Piece(new TextSpan(0, 5), new TextSpan(0, 5)), new Piece(new TextSpan(5, 9), new TextSpan(3, 7)));
        assertThatThrownBy(() -> new OffsetMap(pieces, 9, 7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap");
    }

    @Test
    void builderTracksADeletion() {
        OffsetMapBuilder builder = new OffsetMapBuilder();
        builder.keep(4);
        builder.drop(6);
        builder.keep(3);
        OffsetMap mapping = builder.build();
        assertThat(mapping.sourceLength()).isEqualTo(13);
        assertThat(mapping.targetLength()).isEqualTo(7);
        assertThat(mapping.toSource(0)).isZero();
        assertThat(mapping.toSource(4)).isEqualTo(10);
        assertThat(mapping.toSource(7)).isEqualTo(13);
    }

    @Test
    void builderTracksAnInsertion() {
        OffsetMapBuilder builder = new OffsetMapBuilder();
        builder.keep(4);
        builder.insert(5);
        builder.keep(2);
        OffsetMap mapping = builder.build();
        assertThat(mapping.toSource(4)).isEqualTo(4);
        assertThat(mapping.toSource(6)).isEqualTo(4);
        assertThat(mapping.toSource(9)).isEqualTo(4);
        assertThat(mapping.toSource(10)).isEqualTo(5);
    }

    @Test
    void builderInterpolatesAcrossAReplacement() {
        OffsetMapBuilder builder = new OffsetMapBuilder();
        builder.keep(2);
        builder.replace(10, 5);
        builder.keep(1);
        OffsetMap mapping = builder.build();
        assertThat(mapping.toSource(2)).isEqualTo(2);
        assertThat(mapping.toSource(4)).isEqualTo(6);
        assertThat(mapping.toSource(7)).isEqualTo(12);
    }

    @Test
    void builderIgnoresEmptyMoves() {
        OffsetMapBuilder builder = new OffsetMapBuilder();
        builder.keep(0);
        builder.drop(0);
        assertThat(builder.build().pieces()).isEmpty();
    }

    @Test
    void builderRejectsNegativeRuns() {
        assertThatThrownBy(() -> new OffsetMapBuilder().keep(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be negative");
    }

    @Test
    void builderExposesItsCursors() {
        OffsetMapBuilder builder = new OffsetMapBuilder();
        builder.keep(3);
        builder.drop(2);
        assertThat(builder.sourceCursor()).isEqualTo(5);
        assertThat(builder.targetCursor()).isEqualTo(3);
    }

    @Test
    void sourceLengthCanBeDeclaredToCoverATrailingTail() {
        OffsetMapBuilder builder = new OffsetMapBuilder();
        builder.keep(3);
        assertThat(builder.build(40).sourceLength()).isEqualTo(40);
    }

    @Test
    void spanTranslationNeverInverts() {
        OffsetMapBuilder builder = new OffsetMapBuilder();
        builder.keep(3);
        builder.insert(4);
        OffsetMap mapping = builder.build();
        TextSpan span = mapping.toSourceSpan(new TextSpan(3, 7));
        assertThat(span.start()).isLessThanOrEqualTo(span.end());
        assertThat(span).isEqualTo(new TextSpan(3, 3));
    }

    @Test
    void piecesReportAlignment() {
        assertThat(new Piece(new TextSpan(0, 4), new TextSpan(0, 4)).isAligned())
                .isTrue();
        assertThat(new Piece(new TextSpan(0, 4), new TextSpan(0, 2)).isAligned())
                .isFalse();
        assertThat(new Piece(new TextSpan(0, 0), new TextSpan(0, 0)).isAligned())
                .isFalse();
    }

    @Test
    void composeChainsTwoRewrites() {
        OffsetMapBuilder first = new OffsetMapBuilder();
        first.keep(4);
        first.drop(3);
        first.keep(3);
        OffsetMap aToB = first.build();

        OffsetMapBuilder second = new OffsetMapBuilder();
        second.keep(2);
        second.drop(2);
        second.keep(3);
        OffsetMap bToC = second.build();

        OffsetMap aToC = aToB.compose(bToC);
        assertThat(aToC.sourceLength()).isEqualTo(10);
        assertThat(aToC.targetLength()).isEqualTo(5);
        assertThat(aToC.toSource(0)).isZero();
        assertThat(aToC.toSource(2)).isEqualTo(7);
        assertThat(aToC.toSource(5)).isEqualTo(10);
    }

    @Test
    void composeRejectsALengthMismatch() {
        assertThatThrownBy(() -> OffsetMap.identity(4).compose(OffsetMap.identity(9)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot compose");
    }

    @Test
    void anEmptyMapAnswersZero() {
        OffsetMap empty = new OffsetMap(List.of(), 0, 0);
        assertThat(empty.toSource(0)).isZero();
    }
}
