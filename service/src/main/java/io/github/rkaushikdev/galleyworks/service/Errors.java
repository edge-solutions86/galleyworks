package io.github.rkaushikdev.galleyworks.service;

import io.github.rkaushikdev.galleyworks.LexdriftError;
import io.github.rkaushikdev.galleyworks.util.Log;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * How a failure reaches the caller.
 *
 * <p>An engine error answers with the status its class names and its stable code, message and
 * context. A body or parameter that does not validate answers 422 with one entry per problem. A
 * missing instrument or route answers 404 with a {@code detail} sentence, and anything unexpected
 * answers a plain 500.
 */
@RestControllerAdvice
public class Errors {

    private static final Logger LOG = Log.get("service");

    /** An engine error: its own status, code, message and context. */
    @ExceptionHandler(LexdriftError.class)
    public ResponseEntity<Map<String, Object>> engineError(LexdriftError exc, HttpServletRequest request) {
        LOG.atWarn()
                .addKeyValue("code", exc.code())
                .addKeyValue("path", request.getRequestURI())
                .log("request failed");
        return ResponseEntity.status(exc.httpStatus()).body(exc.toDict());
    }

    /** A request that does not validate. */
    @ExceptionHandler(RequestRejected.class)
    public ResponseEntity<Map<String, Object>> rejected(RequestRejected exc) {
        return ResponseEntity.unprocessableEntity().body(exc.body());
    }

    /** A refusal with a status and a sentence, such as an unknown instrument. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> refused(ResponseStatusException exc) {
        String reason = exc.getReason() == null
                ? HttpStatus.valueOf(exc.getStatusCode().value()).getReasonPhrase()
                : exc.getReason();
        return ResponseEntity.status(exc.getStatusCode()).body(Map.of("detail", reason));
    }

    /** A path no route serves. */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<Map<String, Object>> notFound(Exception exc) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "Not Found"));
    }

    /** A route asked for with a method it does not answer. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> wrongMethod(HttpRequestMethodNotSupportedException exc) {
        HttpHeaders headers = new HttpHeaders();
        if (exc.getSupportedHttpMethods() != null) {
            headers.setAllow(exc.getSupportedHttpMethods());
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(headers)
                .body(Map.of("detail", "Method Not Allowed"));
    }

    /** Anything else: logged in full, answered without detail. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> unexpected(Exception exc, HttpServletRequest request) {
        LOG.atError().setCause(exc).addKeyValue("path", request.getRequestURI()).log("request crashed");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.TEXT_PLAIN)
                .body("Internal Server Error");
    }
}
