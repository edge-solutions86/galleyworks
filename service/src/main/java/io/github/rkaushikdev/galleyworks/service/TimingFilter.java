package io.github.rkaushikdev.galleyworks.service;

import io.github.rkaushikdev.galleyworks.util.Floats;
import io.github.rkaushikdev.galleyworks.util.Log;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Request timing and the access log.
 *
 * <p>A query walks the whole engine, so response times vary by two orders of magnitude depending on
 * the document. Every response carries a request id and a duration header, and every request
 * produces one structured log line; the id is generated here when the caller did not supply one, so
 * a slow request can be found in the log from the response alone.
 */
public final class TimingFilter extends OncePerRequestFilter {

    /** The header carrying the request id, both ways. */
    public static final String REQUEST_ID_HEADER = "x-request-id";

    /** The header carrying how long the request took, in milliseconds. */
    public static final String DURATION_HEADER = "x-response-time-ms";

    /** Requests at least this slow are logged as warnings by default. */
    public static final double DEFAULT_SLOW_MS = 1000.0;

    private static final int REQUEST_ID_LENGTH = 16;
    private static final Logger LOG = Log.get("service.access");

    private final double slowMs;

    /** A filter warning about requests that take {@code slowMs} or longer. */
    public TimingFilter(double slowMs) {
        this.slowMs = slowMs;
    }

    /** The slow-request threshold, in milliseconds. */
    public double slowMs() {
        return slowMs;
    }

    /** A short opaque id for one request. */
    public static String newRequestId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, REQUEST_ID_LENGTH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader(REQUEST_ID_HEADER);
        String requestId = supplied == null || supplied.isEmpty() ? newRequestId() : supplied;
        ContentCachingResponseWrapper buffered = new ContentCachingResponseWrapper(response);
        long started = System.nanoTime();
        String outer = MDC.get("request_id");
        MDC.put("request_id", requestId);
        try {
            chain.doFilter(request, buffered);
        } finally {
            if (outer == null) {
                MDC.remove("request_id");
            } else {
                MDC.put("request_id", outer);
            }
        }
        double elapsedMs = (System.nanoTime() - started) / 1_000_000.0;
        buffered.setHeader(REQUEST_ID_HEADER, requestId);
        buffered.setHeader(DURATION_HEADER, Floats.fixed(elapsedMs, 1));
        buffered.copyBodyToResponse();
        LoggingEventBuilder event = elapsedMs >= slowMs ? LOG.atWarn() : LOG.atInfo();
        event.addKeyValue("request_id", requestId)
                .addKeyValue("method", request.getMethod())
                .addKeyValue("path", request.getRequestURI())
                .addKeyValue("status", buffered.getStatus())
                .addKeyValue("duration_ms", Floats.round(elapsedMs, 1))
                .log(elapsedMs >= slowMs ? "slow request" : "request");
    }
}
