package io.github.rkaushikdev.galleyworks.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

/** Talks to a running service over real HTTP, one call per line of a test. */
final class TestClient implements AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final LexdriftService service;
    private final LexdriftService.Running running;
    private final HttpClient http = HttpClient.newHttpClient();

    private TestClient(LexdriftService service) {
        this.service = service;
        this.running = service.start("127.0.0.1", 0);
    }

    /** Start {@code service} on a free port. */
    static TestClient of(LexdriftService service) {
        return new TestClient(service);
    }

    /**
     * One response.
     *
     * @param status the status code
     * @param response the whole response
     */
    record Answer(int status, HttpResponse<String> response) {

        /** The body, parsed as JSON. */
        JsonNode json() {
            try {
                return JSON.readTree(response.body());
            } catch (JsonProcessingException exc) {
                throw new IllegalStateException("not JSON: " + response.body(), exc);
            }
        }

        /** The value of header {@code name}, when present. */
        Optional<String> header(String name) {
            return response.headers().firstValue(name);
        }

        /** The body as text. */
        String body() {
            return response.body();
        }
    }

    LexdriftService service() {
        return service;
    }

    LexdriftService.Running running() {
        return running;
    }

    /** {@code GET path} with {@code headers} given as name, value, name, value. */
    Answer get(String path, String... headers) {
        return send(request(path, headers).GET());
    }

    /** {@code POST path} with {@code body} encoded as JSON. */
    Answer postJson(String path, Object body, String... headers) {
        try {
            return post(path, JSON.writeValueAsString(body), "application/json", headers);
        } catch (JsonProcessingException exc) {
            throw new IllegalArgumentException(exc);
        }
    }

    /** {@code POST path} with {@code body} as it is, declared as {@code contentType}. */
    Answer post(String path, String body, String contentType, String... headers) {
        HttpRequest.Builder builder = request(path, headers).POST(HttpRequest.BodyPublishers.ofString(body));
        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }
        return send(builder);
    }

    private HttpRequest.Builder request(String path, String... headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + running.port() + path));
        for (int i = 0; i + 1 < headers.length; i += 2) {
            builder.header(headers[i], headers[i + 1]);
        }
        return builder;
    }

    private Answer send(HttpRequest.Builder builder) {
        try {
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Answer(response.statusCode(), response);
        } catch (IOException exc) {
            throw new UncheckedIOException(exc);
        } catch (InterruptedException exc) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exc);
        }
    }

    @Override
    public void close() {
        http.close();
        running.close();
    }
}
