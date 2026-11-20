package io.github.rkaushikdev.galleyworks.util;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * ISO 8601 timestamps written the way the reports have always written them: an explicit {@code
 * +00:00} offset rather than {@code Z}, and microseconds only when there are any.
 */
public final class Timestamps {

    private static final DateTimeFormatter SECONDS = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter OFFSET = DateTimeFormatter.ofPattern("xxx");

    private Timestamps() {}

    /** The current time in UTC, to the microsecond. */
    public static OffsetDateTime nowUtc() {
        return OffsetDateTime.now(Clock.systemUTC()).truncatedTo(ChronoUnit.MICROS);
    }

    /** {@code 2025-07-01T12:30:00+00:00}, with {@code .ffffff} when the time has microseconds. */
    public static String isoformat(OffsetDateTime when) {
        int micros = when.getNano() / 1000;
        String fraction = micros == 0 ? "" : String.format(Locale.ROOT, ".%06d", micros);
        return SECONDS.format(when) + fraction + OFFSET.format(when);
    }

    /** {@code 2025-07-01T12:30:00+00:00}, always to the second. */
    public static String isoformatSeconds(OffsetDateTime when) {
        return SECONDS.format(when) + OFFSET.format(when);
    }

    /** Midnight UTC on the given day, for fixtures and tests. */
    public static OffsetDateTime utc(int year, int month, int day, int hour, int minute) {
        return OffsetDateTime.of(year, month, day, hour, minute, 0, 0, ZoneOffset.UTC);
    }
}
