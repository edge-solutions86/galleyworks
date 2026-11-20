package io.github.rkaushikdev.galleyworks.ingest;

import io.github.rkaushikdev.galleyworks.Chamber;
import io.github.rkaushikdev.galleyworks.util.Regex;
import io.github.rkaushikdev.galleyworks.util.Text;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulling identity and dates out of a bill's front matter.
 *
 * <p>Hints from the caller always win; this is the fallback for a corpus that arrives as a
 * directory of text files with nothing but the text, which is the normal case when a legislature
 * publishes bills as printed.
 */
public final class Metadata {

    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("january", 1),
            Map.entry("february", 2),
            Map.entry("march", 3),
            Map.entry("april", 4),
            Map.entry("may", 5),
            Map.entry("june", 6),
            Map.entry("july", 7),
            Map.entry("august", 8),
            Map.entry("september", 9),
            Map.entry("october", 10),
            Map.entry("november", 11),
            Map.entry("december", 12));

    private record ChamberWord(String words, Chamber chamber) {}

    private static final List<ChamberWord> CHAMBER_WORDS = List.of(
            new ChamberWord("house bill", Chamber.LOWER),
            new ChamberWord("house joint resolution", Chamber.LOWER),
            new ChamberWord("senate file", Chamber.UPPER),
            new ChamberWord("senate bill", Chamber.UPPER),
            new ChamberWord("senate joint resolution", Chamber.UPPER),
            new ChamberWord("council bill", Chamber.COUNCIL),
            new ChamberWord("ordinance", Chamber.COUNCIL),
            new ChamberWord("executive order", Chamber.EXECUTIVE));

    private static final Pattern NUMBER =
            Regex.ignoringCase("(?:no\\.?|number)\\s*(?<number>[A-Z]{0,3}[- ]?\\d{1,5}(?:-\\d{1,4})?[A-Z]?)");
    private static final Pattern SESSION = Regex.compile("\\b(?<year>(?:19|20)\\d{2})\\b");
    private static final Pattern SPONSOR = Regex.ignoringCase("^sponsored by:\\s*(?<sponsor>.+)$");
    private static final Pattern EFFECTIVE =
            Regex.ignoringCase("effective\\s+(?<month>[A-Za-z]+)\\s+(?<day>\\d{1,2}),\\s*(?<year>\\d{4})");

    private Metadata() {}

    /** Scan the opening lines of a bill for its printed identity. */
    public static BillHeader readHeader(List<String> lines) {
        String number = null;
        Chamber chamber = null;
        String session = null;
        String sponsor = null;
        for (String raw : lines) {
            String line = Text.strip(raw);
            if (line.isEmpty()) {
                continue;
            }
            String lowered = Text.lower(line);
            if (chamber == null) {
                for (ChamberWord word : CHAMBER_WORDS) {
                    if (lowered.contains(word.words())) {
                        chamber = word.chamber();
                        break;
                    }
                }
            }
            if (number == null && chamber != null) {
                Matcher match = NUMBER.matcher(line);
                if (match.find()) {
                    number = match.group("number").replace(" ", "").replace("-", "");
                }
            }
            if (sponsor == null) {
                Matcher sponsored = SPONSOR.matcher(line);
                if (sponsored.lookingAt()) {
                    sponsor = Text.strip(sponsored.group("sponsor"));
                }
            }
            if (session == null) {
                Matcher year = SESSION.matcher(line);
                if (year.find()) {
                    session = year.group("year");
                }
            }
        }
        return new BillHeader(
                Optional.ofNullable(number),
                Optional.ofNullable(chamber),
                Optional.ofNullable(session),
                Optional.ofNullable(sponsor));
    }

    /** Find an {@code effective July 1, 2025} clause anywhere in the text. */
    public static Optional<LocalDate> parseEffectiveDate(String text) {
        Matcher match = EFFECTIVE.matcher(text);
        if (!match.find()) {
            return Optional.empty();
        }
        Integer month = MONTHS.get(Text.lower(match.group("month")));
        if (month == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(
                    LocalDate.of(Integer.parseInt(match.group("year")), month, Integer.parseInt(match.group("day"))));
        } catch (DateTimeException exc) {
            return Optional.empty();
        }
    }
}
