package io.github.rkaushikdev.galleyworks.util;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.LayoutBase;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.event.KeyValuePair;

/**
 * Renders a log event as one JSON object per line.
 *
 * <p>The object holds {@code ts}, {@code level}, {@code logger} and {@code message}, then the
 * ambient context bound with {@link Log#bound}, then the event's own key-value fields, which win
 * over a bound field of the same name. Keys are written sorted.
 */
public final class JsonLayout extends LayoutBase<ILoggingEvent> {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final Set<String> RESERVED = Set.of("ts", "level", "logger", "message", "exception");
    private static final Json.Style STYLE = Json.Style.compact().sorted().stringifying();

    @Override
    public String doLayout(ILoggingEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        LocalDateTime when =
                LocalDateTime.ofInstant(Instant.ofEpochMilli(event.getTimeStamp()), ZoneId.systemDefault());
        payload.put("ts", TIMESTAMP.format(when));
        payload.put("level", Log.levelName(event.getLevel()).toLowerCase(Locale.ROOT));
        payload.put("logger", event.getLoggerName());
        payload.put("message", event.getFormattedMessage());
        Map<String, String> context = event.getMDCPropertyMap();
        if (context != null) {
            context.forEach((key, value) -> {
                if (!RESERVED.contains(key)) {
                    payload.put(key, value);
                }
            });
        }
        if (event.getKeyValuePairs() != null) {
            for (KeyValuePair pair : event.getKeyValuePairs()) {
                if (!RESERVED.contains(pair.key)) {
                    payload.put(pair.key, pair.value);
                }
            }
        }
        IThrowableProxy thrown = event.getThrowableProxy();
        if (thrown != null) {
            payload.put("exception", ThrowableProxyUtil.asString(thrown).strip());
        }
        return Json.write(payload, STYLE) + "\n";
    }
}
