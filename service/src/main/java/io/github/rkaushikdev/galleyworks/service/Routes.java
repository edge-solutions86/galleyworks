package io.github.rkaushikdev.galleyworks.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.github.rkaushikdev.galleyworks.ConfigError;
import io.github.rkaushikdev.galleyworks.Engine;
import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.InstrumentKey;
import io.github.rkaushikdev.galleyworks.Lexdrift;
import io.github.rkaushikdev.galleyworks.fingerprint.SegmentFingerprint;
import io.github.rkaushikdev.galleyworks.index.IndexStats;
import io.github.rkaushikdev.galleyworks.ingest.SourceDocument;
import io.github.rkaushikdev.galleyworks.report.InstrumentReport;
import io.github.rkaushikdev.galleyworks.util.Floats;
import io.github.rkaushikdev.galleyworks.util.Text;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The four routes: liveness, the corpus listing, one instrument's segments, and a query. Every route
 * is read only; a queried document is never indexed.
 */
@RestController
public class Routes {

    /** The largest page the listing serves. */
    public static final int MAX_PAGE = 500;

    private static final int DEFAULT_PAGE = 100;
    private static final int JURISDICTION_LIMIT = 32;
    private static final Pattern WHOLE = Pattern.compile("[+-]?[0-9]+");

    private final ServiceState state;
    private final ObjectReader bodies;

    /** Routes answering from {@code state}, reading bodies with {@code json}. */
    public Routes(ServiceState state, ObjectMapper json) {
        this.state = state;
        this.bodies = json.reader()
                .forType(Object.class)
                .with(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    /** Liveness, plus enough detail to tell two deployments apart. */
    @GetMapping(path = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    @Tag(name = "meta")
    public Schemas.HealthResponse health() {
        return state.read(engine -> {
            IndexStats stats = engine.index().stats();
            return new Schemas.HealthResponse(
                    "ok", Lexdrift.VERSION, state.digest(), stats.instruments(), stats.segments());
        });
    }

    /** One page of the indexed corpus. */
    @GetMapping(path = "/v1/instruments", produces = MediaType.APPLICATION_JSON_VALUE)
    @Tag(name = "corpus")
    @Parameter(
            name = "limit",
            in = ParameterIn.QUERY,
            schema = @Schema(type = "integer", defaultValue = "100", minimum = "1", maximum = "500"))
    @Parameter(
            name = "offset",
            in = ParameterIn.QUERY,
            schema = @Schema(type = "integer", defaultValue = "0", minimum = "0"))
    @Parameter(name = "jurisdiction", in = ParameterIn.QUERY, schema = @Schema(type = "string", maxLength = 32))
    public Schemas.InstrumentsResponse instruments(@Parameter(hidden = true) HttpServletRequest request) {
        RequestRejected.Problems problems = new RequestRejected.Problems();
        long limit = whole(request, "limit", DEFAULT_PAGE, 1, MAX_PAGE, problems);
        long offset = whole(request, "offset", 0, 0, Long.MAX_VALUE, problems);
        Optional<String> jurisdiction = last(request, "jurisdiction");
        jurisdiction.ifPresent(value -> {
            if (value.codePointCount(0, value.length()) > JURISDICTION_LIMIT) {
                problems.add(
                        "string_too_long",
                        List.of("query", "jurisdiction"),
                        "String should have at most 32 characters",
                        value,
                        Map.of("max_length", JURISDICTION_LIMIT));
            }
        });
        problems.raiseIfAny();
        return state.read(engine -> {
            List<InstrumentKey> keys = engine.index().instruments().stream()
                    .filter(key -> jurisdiction.isEmpty() || key.jurisdiction().equals(jurisdiction.get()))
                    .toList();
            int from = (int) Math.min(offset, keys.size());
            int to = (int) Math.min(from + limit, keys.size());
            List<Schemas.InstrumentOut> page = keys.subList(from, to).stream()
                    .map(Schemas.InstrumentOut::of)
                    .toList();
            return new Schemas.InstrumentsResponse(keys.size(), offset, (int) limit, page);
        });
    }

    /** Every indexed segment of one instrument. */
    @GetMapping(
            path = "/v1/instruments/{jurisdiction}/{session}/{chamber}/{number}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Tag(name = "corpus")
    public Schemas.InstrumentDetail instrumentDetail(
            @PathVariable String jurisdiction,
            @PathVariable String session,
            @PathVariable String chamber,
            @PathVariable String number) {
        InstrumentKey key;
        try {
            key = InstrumentKey.parse(jurisdiction + "/" + session + "/" + chamber + "/" + number);
        } catch (ConfigError exc) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no such instrument", exc);
        }
        List<SegmentFingerprint> segments = state.read(engine -> {
            List<SegmentFingerprint> found = new ArrayList<>();
            for (SegmentFingerprint record : engine.index()) {
                if (record.key().equals(key)) {
                    found.add(record);
                }
            }
            return found;
        });
        if (segments.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no such instrument");
        }
        segments.sort(Comparator.comparingInt(SegmentFingerprint::segmentIndex));
        return new Schemas.InstrumentDetail(
                Schemas.InstrumentOut.of(key),
                segments.stream()
                        .map(record -> new Schemas.SegmentOut(
                                record.segmentIndex(),
                                record.path(),
                                record.tokenCount(),
                                record.selected().size(),
                                Floats.round(record.contentRatio(), 4)))
                        .toList());
    }

    /** Report what one document reuses, without indexing it. */
    @PostMapping(path = "/v1/query", produces = MediaType.APPLICATION_JSON_VALUE)
    @Tag(name = "query")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Schemas.QueryRequest.class)))
    public Schemas.ReportOut query(
            @RequestBody(required = false) byte[] body,
            @Parameter(hidden = true) @RequestHeader(name = "Content-Type", required = false) String contentType) {
        Schemas.QueryRequest request = Schemas.QueryRequest.of(decode(body, contentType));
        Map<String, Object> hints = new LinkedHashMap<>();
        hints.put("jurisdiction", request.jurisdiction());
        if (request.session() != null) {
            hints.put("session", request.session());
        }
        if (request.number() != null) {
            hints.put("number", request.number());
        }
        SourceDocument document = new SourceDocument(request.text(), "<request>", hints);
        return state.read(engine -> answer(engine, document, request));
    }

    private Schemas.ReportOut answer(Engine engine, SourceDocument document, Schemas.QueryRequest request) {
        Instrument instrument = request.formatName() == null
                ? engine.registry().read(document)
                : engine.registry().read(document, request.formatName());
        engine.texts().add(instrument);
        InstrumentReport report = engine.analyse(instrument).report();
        return Schemas.ReportOut.of(report, state.digest(), request.includeText());
    }

    /**
     * The body as decoded JSON. An empty body is absent, a body that is not declared as JSON is
     * handed on as text so validation refuses it, and text that is not JSON is refused here.
     */
    private Object decode(byte[] body, String contentType) {
        if (body == null || body.length == 0) {
            throw RequestRejected.one("missing", List.of("body"), "Field required", null);
        }
        String text;
        try {
            text = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(body))
                    .toString();
        } catch (CharacterCodingException exc) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "There was an error parsing the body", exc);
        }
        if (contentType != null && !isJson(contentType)) {
            return text;
        }
        try {
            return bodies.readValue(text);
        } catch (JsonProcessingException exc) {
            long position = exc.getLocation() == null
                    ? 0
                    : Math.max(0, exc.getLocation().getCharOffset());
            throw RequestRejected.one(
                    "json_invalid",
                    List.of("body", position),
                    "JSON decode error",
                    Map.of(),
                    Map.of("error", exc.getOriginalMessage()));
        }
    }

    private static boolean isJson(String contentType) {
        String media = Text.lower(Text.strip(contentType.split(";", -1)[0]));
        if (!media.startsWith("application/")) {
            return false;
        }
        String subtype = media.substring("application/".length());
        return subtype.equals("json") || subtype.endsWith("+json");
    }

    /** The last value given for query parameter {@code name}. */
    private static Optional<String> last(HttpServletRequest request, String name) {
        String[] values = request.getParameterValues(name);
        return values == null || values.length == 0 ? Optional.empty() : Optional.of(values[values.length - 1]);
    }

    /** Query parameter {@code name} as a whole number in {@code [minimum, maximum]}. */
    private static long whole(
            HttpServletRequest request,
            String name,
            long fallback,
            long minimum,
            long maximum,
            RequestRejected.Problems problems) {
        Optional<String> given = last(request, name);
        if (given.isEmpty()) {
            return fallback;
        }
        String raw = given.get();
        String cleaned = Text.strip(raw);
        long value;
        try {
            if (!WHOLE.matcher(cleaned).matches()) {
                throw new NumberFormatException(cleaned);
            }
            value = Long.parseLong(cleaned);
        } catch (NumberFormatException exc) {
            problems.add(
                    "int_parsing",
                    List.of("query", name),
                    "Input should be a valid integer, unable to parse string as an integer",
                    raw);
            return fallback;
        }
        if (value < minimum) {
            problems.add(
                    "greater_than_equal",
                    List.of("query", name),
                    "Input should be greater than or equal to " + minimum,
                    raw,
                    Map.of("ge", minimum));
        } else if (value > maximum) {
            problems.add(
                    "less_than_equal",
                    List.of("query", name),
                    "Input should be less than or equal to " + maximum,
                    raw,
                    Map.of("le", maximum));
        }
        return value;
    }
}
