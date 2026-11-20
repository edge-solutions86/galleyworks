package io.github.rkaushikdev.galleyworks;

import io.github.rkaushikdev.galleyworks.util.Regex;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Uniquely names one instrument: the jurisdiction that enacted it, the legislative session, the
 * chamber of origin and the printed number.
 *
 * <p>It round-trips through a compact slug ({@code wy/2025/lower/HB0142}) so it can sit in a
 * filename, a database row and a report without being escaped. Keys order field by field.
 *
 * @param jurisdiction the enacting jurisdiction's slug
 * @param session the legislative session
 * @param chamber the chamber of origin
 * @param number the printed number
 */
public record InstrumentKey(String jurisdiction, String session, Chamber chamber, String number)
        implements Comparable<InstrumentKey> {

    private static final Pattern KEY = Regex.compile("^(?<jurisdiction>[a-z0-9][a-z0-9-]{0,31})"
            + "/(?<session>[a-z0-9][a-z0-9-]{0,31})"
            + "/(?<chamber>[a-z]+)"
            + "/(?<number>[A-Za-z0-9._-]{1,32})$");

    private static final Comparator<InstrumentKey> ORDER = Comparator.comparing(InstrumentKey::jurisdiction)
            .thenComparing(InstrumentKey::session)
            .thenComparing(key -> key.chamber().value())
            .thenComparing(InstrumentKey::number);

    /** Validates each field. */
    public InstrumentKey {
        if (!Jurisdiction.SLUG.matcher(jurisdiction).lookingAt()) {
            throw new ConfigError("instrument jurisdiction is malformed", Context.of("value", jurisdiction));
        }
        if (!Jurisdiction.SLUG.matcher(session).lookingAt()) {
            throw new ConfigError("instrument session is malformed", Context.of("value", session));
        }
        if (number.isEmpty() || number.length() > 32) {
            throw new ConfigError("instrument number is malformed", Context.of("value", number));
        }
    }

    /** {@code jurisdiction/session/chamber/number}. */
    public String slug() {
        return jurisdiction + "/" + session + "/" + chamber.value() + "/" + number;
    }

    /** Read a key back from its slug. */
    public static InstrumentKey parse(String slug) {
        Matcher match = KEY.matcher(slug);
        if (!match.lookingAt()) {
            throw new ConfigError("instrument key does not parse", Context.of("slug", slug));
        }
        Chamber chamber;
        try {
            chamber = Chamber.fromValue(match.group("chamber"));
        } catch (IllegalArgumentException exc) {
            throw new ConfigError("unknown chamber in instrument key", Context.of("slug", slug), exc);
        }
        return new InstrumentKey(match.group("jurisdiction"), match.group("session"), chamber, match.group("number"));
    }

    @Override
    public int compareTo(InstrumentKey other) {
        return ORDER.compare(this, other);
    }

    @Override
    public String toString() {
        return slug();
    }
}
