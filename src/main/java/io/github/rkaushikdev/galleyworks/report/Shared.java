package io.github.rkaushikdev.galleyworks.report;

import io.github.rkaushikdev.galleyworks.util.Regex;
import io.github.rkaushikdev.galleyworks.util.Text;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What every report writer needs, in one place, so that a passage quoted in the text report and the
 * same passage quoted in the markdown one are the same string.
 */
public final class Shared {

    /** The character a truncated quotation ends with. */
    public static final String ELLIPSIS = "…";

    private static final Pattern WHITESPACE = Regex.compile(Text.SPACE_CLASS + "+");

    private Shared() {}

    /** Collapse every run of whitespace so a quotation fits on one line. */
    public static String flow(String text) {
        return Text.strip(WHITESPACE.matcher(text).replaceAll(" "));
    }

    /** A one-line quotation, or the empty string when there is nothing to quote. */
    public static String quote(String text) {
        return quote(text, Optional.empty(), "");
    }

    /** A one-line quotation truncated to {@code limit} characters, or {@code empty} when blank. */
    public static String quote(String text, Optional<Integer> limit, String empty) {
        String flowed = flow(text);
        if (flowed.isEmpty()) {
            return empty;
        }
        if (limit.isEmpty() || flowed.length() <= limit.get()) {
            return flowed;
        }
        return flowed.substring(0, Math.max(1, limit.get() - 1)) + ELLIPSIS;
    }

    /** The JSON shape of a span, spelled one way: start, end and length. */
    public static Map<String, Object> spanPayload(TextSpan span) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("start", span.start());
        payload.put("end", span.end());
        payload.put("length", span.length());
        return payload;
    }

    /**
     * A finding cut down to a number of passages, and how many were hidden.
     *
     * @param match the finding with at most the allowed passages
     * @param hidden how many passages were cut
     */
    public record Trimmed(ReportedMatch match, int hidden) {}

    /** {@code match} with at most {@code limit} passages, and how many were hidden. */
    public static Trimmed trimPassages(ReportedMatch match, Optional<Integer> limit) {
        if (limit.isEmpty() || match.passages().size() <= limit.get()) {
            return new Trimmed(match, 0);
        }
        int kept = limit.get();
        return new Trimmed(
                match.withPassages(match.passages().subList(0, kept)),
                match.passages().size() - kept);
    }
}
