package io.github.rkaushikdev.galleyworks.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.galleyworks.ParseError;
import io.github.rkaushikdev.galleyworks.ingest.Markup.Change;
import io.github.rkaushikdev.galleyworks.ingest.Markup.ChangeKind;
import io.github.rkaushikdev.galleyworks.ingest.Markup.MarkedText;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MarkupTest {

    private static final String SIMPLE = "not later than {-sixty (60)-}{+ninety (90)+} days after the close.";

    @Test
    void plainTextPassesThroughUnchanged() {
        MarkedText parsed = Markup.parseMarkup("The board shall publish.");
        assertThat(parsed.current()).isEqualTo("The board shall publish.");
        assertThat(parsed.proposed()).isEqualTo("The board shall publish.");
        assertThat(parsed.changes()).isEmpty();
        assertThat(parsed.isAmendment()).isFalse();
    }

    @Test
    void bothReadingsAreProduced() {
        MarkedText parsed = Markup.parseMarkup(SIMPLE);
        assertThat(parsed.current()).isEqualTo("not later than sixty (60) days after the close.");
        assertThat(parsed.proposed()).isEqualTo("not later than ninety (90) days after the close.");
        assertThat(parsed.isAmendment()).isTrue();
    }

    @Test
    void changesAreClassifiedAndLocated() {
        MarkedText parsed = Markup.parseMarkup(SIMPLE);
        assertThat(parsed.changes().stream().map(Change::kind)).containsExactly(ChangeKind.STRIKE, ChangeKind.INSERT);
        assertThat(parsed.changes().stream().map(Change::text)).containsExactly("sixty (60)", "ninety (90)");
        Change strike = parsed.changes().getFirst();
        assertThat(strike.span().sliceOf(SIMPLE)).isEqualTo("{-sixty (60)-}");
        assertThat(strike.isStrike()).isTrue();
    }

    @Test
    void struckAndInsertedViews() {
        MarkedText parsed = Markup.parseMarkup(SIMPLE);
        assertThat(parsed.struck().stream().map(Change::text)).containsExactly("sixty (60)");
        assertThat(parsed.inserted().stream().map(Change::text)).containsExactly("ninety (90)");
    }

    @Test
    void currentOffsetsPointBackAtTheMarkup() {
        MarkedText parsed = Markup.parseMarkup(SIMPLE);
        int start = parsed.current().indexOf("sixty");
        assertThat(SIMPLE.substring(parsed.currentMap().toSource(start))).startsWith("sixty");
    }

    @Test
    void proposedOffsetsPointBackAtTheMarkup() {
        MarkedText parsed = Markup.parseMarkup(SIMPLE);
        int start = parsed.proposed().indexOf("ninety");
        assertThat(SIMPLE.substring(parsed.proposedMap().toSource(start))).startsWith("ninety");
    }

    @Test
    void offsetsOfTheSharedTailAgree() {
        MarkedText parsed = Markup.parseMarkup(SIMPLE);
        assertThat(parsed.currentMap().toSource(parsed.current().indexOf("days")))
                .isEqualTo(SIMPLE.indexOf("days"));
        assertThat(parsed.proposedMap().toSource(parsed.proposed().indexOf("days")))
                .isEqualTo(SIMPLE.indexOf("days"));
    }

    @Test
    void mapsDeclareTheMarkupLength() {
        MarkedText parsed = Markup.parseMarkup(SIMPLE);
        assertThat(parsed.currentMap().sourceLength()).isEqualTo(SIMPLE.length());
        assertThat(parsed.proposedMap().sourceLength()).isEqualTo(SIMPLE.length());
        assertThat(parsed.currentMap().targetLength())
                .isEqualTo(parsed.current().length());
    }

    @Test
    void anInsertOnlyAmendment() {
        MarkedText parsed = Markup.parseMarkup("the board{+ and the commission+} shall act");
        assertThat(parsed.current()).isEqualTo("the board shall act");
        assertThat(parsed.proposed()).isEqualTo("the board and the commission shall act");
    }

    @Test
    void aStrikeOnlyAmendment() {
        MarkedText parsed = Markup.parseMarkup("the board{- and the commission-} shall act");
        assertThat(parsed.current()).isEqualTo("the board and the commission shall act");
        assertThat(parsed.proposed()).isEqualTo("the board shall act");
    }

    @Test
    void anEmptyInsertIsAllowed() {
        MarkedText parsed = Markup.parseMarkup("a{++}b");
        assertThat(parsed.current()).isEqualTo("ab");
        assertThat(parsed.proposed()).isEqualTo("ab");
        assertThat(parsed.changes().getFirst().text()).isEmpty();
    }

    @Test
    void markupSpanningLines() {
        MarkedText parsed = Markup.parseMarkup("keep {-drop\nthese-} keep");
        assertThat(parsed.proposed()).isEqualTo("keep  keep");
        assertThat(parsed.current()).isEqualTo("keep drop\nthese keep");
    }

    @Test
    void hasMarkupDetectsWellFormedTags() {
        assertThat(Markup.hasMarkup(SIMPLE)).isTrue();
        assertThat(Markup.hasMarkup("no tags at all")).isFalse();
        assertThat(Markup.hasMarkup("a stray {- opener")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a stray {- opener", "a stray {+ opener", "{-closed-} then {+"})
    void unclosedTagsAreRejected(String text) {
        assertThatThrownBy(() -> Markup.parseMarkup(text))
                .isInstanceOf(ParseError.class)
                .hasMessageContaining("unclosed tag");
    }

    @Test
    void anUnclosedTagReportsItsLineAndColumn() {
        assertThatThrownBy(() -> Markup.parseMarkup("first line\nsecond {- line"))
                .isInstanceOfSatisfying(ParseError.class, caught -> {
                    assertThat(caught.line()).isEqualTo(2);
                    assertThat(caught.column()).isEqualTo(7);
                });
    }

    @Test
    void spanOfAChangeIsTheWholeTag() {
        assertThat(Markup.parseMarkup("x{+abc+}y").changes().getFirst().span()).isEqualTo(new TextSpan(1, 8));
    }
}
