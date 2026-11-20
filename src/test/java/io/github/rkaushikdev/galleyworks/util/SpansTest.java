package io.github.rkaushikdev.galleyworks.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpansTest {

    @Test
    void spanRejectsNegativeStart() {
        assertThatThrownBy(() -> new TextSpan(-1, 4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-negative");
    }

    @Test
    void spanRejectsInvertedBounds() {
        assertThatThrownBy(() -> new TextSpan(9, 8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("precedes start");
    }

    @Test
    void emptySpanIsAllowed() {
        TextSpan span = new TextSpan(5, 5);
        assertThat(span.isEmpty()).isTrue();
        assertThat(span.length()).isZero();
        assertThat(span.offsets().toArray()).isEmpty();
    }

    @Test
    void containsOffsetIsHalfOpen() {
        TextSpan span = new TextSpan(3, 7);
        assertThat(span.containsOffset(3)).isTrue();
        assertThat(span.containsOffset(6)).isTrue();
        assertThat(span.containsOffset(7)).isFalse();
        assertThat(span.containsOffset(2)).isFalse();
    }

    @Test
    void overlapsExcludesAbuttingSpans() {
        TextSpan left = new TextSpan(0, 5);
        TextSpan right = new TextSpan(5, 9);
        assertThat(left.overlaps(right)).isFalse();
        assertThat(left.touches(right)).isTrue();
    }

    @Test
    void intersectionReturnsNoneWhenDisjoint() {
        assertThat(new TextSpan(0, 3).intersection(new TextSpan(4, 8))).isEmpty();
        assertThat(new TextSpan(0, 6).intersection(new TextSpan(4, 8))).contains(new TextSpan(4, 6));
    }

    @Test
    void hullSpansTheGap() {
        assertThat(new TextSpan(0, 2).hull(new TextSpan(10, 12))).isEqualTo(new TextSpan(0, 12));
    }

    @Test
    void shiftAndClamp() {
        assertThat(new TextSpan(2, 5).shift(3)).isEqualTo(new TextSpan(5, 8));
        assertThat(new TextSpan(2, 40).clamp(0, 10)).isEqualTo(new TextSpan(2, 10));
        assertThat(new TextSpan(20, 40).clamp(0, 10)).isEqualTo(new TextSpan(10, 10));
    }

    @Test
    void clampRejectsInvertedBounds() {
        assertThatThrownBy(() -> new TextSpan(0, 1).clamp(5, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bounds inverted");
    }

    @Test
    void sliceOfReadsTheText() {
        String text = "the department shall publish";
        assertThat(new TextSpan(4, 14).sliceOf(text)).isEqualTo("department");
    }

    @Test
    void spansSortByStartThenEnd() {
        List<TextSpan> spans = new ArrayList<>(List.of(new TextSpan(4, 9), new TextSpan(0, 12), new TextSpan(4, 5)));
        spans.sort(null);
        assertThat(spans).containsExactly(new TextSpan(0, 12), new TextSpan(4, 5), new TextSpan(4, 9));
    }

    @Test
    void mergeSpansCollapsesOverlaps() {
        List<TextSpan> merged = Spans.merge(List.of(new TextSpan(0, 5), new TextSpan(3, 9), new TextSpan(20, 25)));
        assertThat(merged).containsExactly(new TextSpan(0, 9), new TextSpan(20, 25));
    }

    @Test
    void mergeSpansHonoursGap() {
        List<TextSpan> spans = List.of(new TextSpan(0, 5), new TextSpan(7, 9));
        assertThat(Spans.merge(spans)).isEqualTo(spans);
        assertThat(Spans.merge(spans, 2)).containsExactly(new TextSpan(0, 9));
    }

    @Test
    void mergeSpansRejectsNegativeGap() {
        assertThatThrownBy(() -> Spans.merge(List.of(), -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-negative");
    }

    @Test
    void coverageCountsEachCharacterOnce() {
        assertThat(Spans.coverage(List.of(new TextSpan(0, 10), new TextSpan(5, 15))))
                .isEqualTo(15);
        assertThat(Spans.coverage(List.of())).isZero();
    }

    @Test
    void subtractPunchesHoles() {
        List<TextSpan> pieces = Spans.subtract(new TextSpan(0, 20), List.of(new TextSpan(5, 8), new TextSpan(12, 14)));
        assertThat(pieces).containsExactly(new TextSpan(0, 5), new TextSpan(8, 12), new TextSpan(14, 20));
    }

    @Test
    void subtractIgnoresHolesOutsideTheSpan() {
        assertThat(Spans.subtract(new TextSpan(10, 20), List.of(new TextSpan(0, 4), new TextSpan(30, 40))))
                .containsExactly(new TextSpan(10, 20));
    }

    @Test
    void subtractCanEmptyASpan() {
        assertThat(Spans.subtract(new TextSpan(4, 9), List.of(new TextSpan(0, 100))))
                .isEmpty();
    }
}
