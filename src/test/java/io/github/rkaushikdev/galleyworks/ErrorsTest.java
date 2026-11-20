package io.github.rkaushikdev.galleyworks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ErrorsTest {

    @Test
    void baseErrorCarriesMessageAndCode() {
        LexdriftError exc = new LexdriftError("something went sideways");
        assertThat(exc.message()).isEqualTo("something went sideways");
        assertThat(exc.code()).isEqualTo("lexdrift.error");
        assertThat(exc.toDict()).isEqualTo(Map.of("code", "lexdrift.error", "message", "something went sideways"));
    }

    @Test
    void contextIsRenderedInStrSorted() {
        ConfigError exc = new ConfigError("bad profile", Context.of("stage", "load", "profile", "wy-2024"));
        assertThat(exc.getMessage()).isEqualTo("bad profile (profile='wy-2024', stage='load')");
    }

    @Test
    void contextIsCopiedNotAliased() {
        Map<String, Object> ctx = new HashMap<>(Map.of("path", "a.txt"));
        IngestError exc = new IngestError("nope", ctx);
        ctx.put("path", "b.txt");
        assertThat(exc.context()).isEqualTo(Map.of("path", "a.txt"));
        assertThat(exc.toDict().get("context")).isEqualTo(Map.of("path", "a.txt"));
    }

    @Test
    void parseErrorIsAnIngestErrorAndLocatesItself() {
        ParseError exc = new ParseError("unbalanced subsection marker", 42, 7);
        assertThat(exc).isInstanceOf(IngestError.class);
        assertThat(exc.code()).isEqualTo("lexdrift.ingest.parse");
        assertThat(exc.located()).isEqualTo("line 42, column 7: unbalanced subsection marker");
        assertThat(exc.toDict().get("context")).isEqualTo(Map.of("line", 42, "column", 7));
    }

    @Test
    void parseErrorDefaultsColumnToZero() {
        ParseError exc = new ParseError("stray enacting clause", 3);
        assertThat(exc.column()).isZero();
        assertThat(exc.located()).startsWith("line 3, column 0:");
    }

    static Stream<Arguments> codes() {
        return Stream.of(
                Arguments.of((Function<String, LexdriftError>) ConfigError::new, "lexdrift.config"),
                Arguments.of((Function<String, LexdriftError>) IngestError::new, "lexdrift.ingest"),
                Arguments.of((Function<String, LexdriftError>) UnknownFormatError::new, "lexdrift.ingest.format"),
                Arguments.of((Function<String, LexdriftError>) CanonError::new, "lexdrift.canon"),
                Arguments.of((Function<String, LexdriftError>) StoreError::new, "lexdrift.store"),
                Arguments.of((Function<String, LexdriftError>) MatchError::new, "lexdrift.match"));
    }

    @ParameterizedTest
    @MethodSource("codes")
    void codesAreStable(Function<String, LexdriftError> make, String code) {
        LexdriftError exc = make.apply("x");
        assertThat(exc.code()).isEqualTo(code);
        assertThat(exc).isInstanceOf(LexdriftError.class);
    }

    @Test
    void errorCodeOfHandlesForeignExceptions() {
        assertThat(LexdriftError.errorCodeOf(new IllegalArgumentException("nope")))
                .isEqualTo("lexdrift.unexpected");
        assertThat(LexdriftError.errorCodeOf(new StoreError("nope"))).isEqualTo("lexdrift.store");
    }

    static Stream<Arguments> statuses() {
        return Stream.of(
                Arguments.of(new LexdriftError("x"), 500),
                Arguments.of(new ConfigError("x"), 400),
                Arguments.of(new IngestError("x"), 422),
                Arguments.of(new ParseError("x", 1), 422),
                Arguments.of(new UnknownFormatError("x"), 415),
                Arguments.of(new CanonError("x"), 500),
                Arguments.of(new StoreError("x"), 503),
                Arguments.of(new MatchError("x"), 500));
    }

    @ParameterizedTest
    @MethodSource("statuses")
    void eachErrorNamesItsHttpStatus(LexdriftError error, int status) {
        assertThat(error.httpStatus()).isEqualTo(status);
    }

    @Test
    void httpStatusOfReadsTheInstance() {
        assertThat(LexdriftError.httpStatusOf(new StoreError("x"))).isEqualTo(503);
        assertThat(LexdriftError.httpStatusOf(new ParseError("x", 1))).isEqualTo(422);
    }

    @Test
    void aForeignExceptionIsAServerError() {
        assertThat(LexdriftError.httpStatusOf(new IllegalArgumentException("x")))
                .isEqualTo(500);
    }

    @Test
    void aSubclassInheritsTheStatus() {
        final class Narrower extends StoreError {
            Narrower(String message) {
                super(message);
            }

            @Override
            public String code() {
                return "lexdrift.store.narrow";
            }
        }

        assertThat(new Narrower("x").httpStatus()).isEqualTo(503);
        assertThat(LexdriftError.httpStatusOf(new Narrower("x"))).isEqualTo(503);
    }
}
