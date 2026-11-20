package io.github.rkaushikdev.galleyworks.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** A small client over MockMvc for the route tests: send a request, read the answer. */
final class Http {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final MockMvc mvc;

    Http(MockMvc mvc) {
        this.mvc = mvc;
    }

    /**
     * One response.
     *
     * @param response the whole response
     */
    record Answer(MockHttpServletResponse response) {

        /** The status code. */
        int status() {
            return response.getStatus();
        }

        /** The body as text. */
        String body() {
            try {
                return response.getContentAsString(StandardCharsets.UTF_8);
            } catch (java.io.UnsupportedEncodingException exc) {
                throw new IllegalStateException(exc);
            }
        }

        /** The body, parsed as JSON. */
        JsonNode json() {
            try {
                return JSON.readTree(body());
            } catch (JsonProcessingException exc) {
                throw new IllegalStateException("not JSON: " + body(), exc);
            }
        }

        /** The value of header {@code name}, when present. */
        Optional<String> header(String name) {
            return Optional.ofNullable(response.getHeader(name));
        }
    }

    /** {@code GET path}, where {@code path} may carry a query string, with headers as name, value pairs. */
    Answer get(String path, String... headers) {
        return send(MockMvcRequestBuilders.get(path), headers);
    }

    /** {@code POST path} with {@code body} encoded as JSON. */
    Answer postJson(String path, Object body, String... headers) {
        try {
            return post(path, JSON.writeValueAsString(body), MediaType.APPLICATION_JSON_VALUE, headers);
        } catch (JsonProcessingException exc) {
            throw new UncheckedIOException(exc);
        }
    }

    /** {@code POST path} with {@code body} as it is, declared as {@code contentType}. */
    Answer post(String path, String body, String contentType, String... headers) {
        MockHttpServletRequestBuilder request =
                MockMvcRequestBuilders.post(path).content(body.getBytes(StandardCharsets.UTF_8));
        if (contentType != null) {
            request.contentType(contentType);
        }
        return send(request, headers);
    }

    private Answer send(MockHttpServletRequestBuilder request, String... headers) {
        for (int i = 0; i + 1 < headers.length; i += 2) {
            request.header(headers[i], headers[i + 1]);
        }
        try {
            return new Answer(mvc.perform(request).andReturn().getResponse());
        } catch (Exception exc) {
            throw new IllegalStateException("the request did not complete", exc);
        }
    }
}
