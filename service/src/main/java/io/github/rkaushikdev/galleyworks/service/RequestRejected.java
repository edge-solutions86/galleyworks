package io.github.rkaushikdev.galleyworks.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A request whose body or parameters do not validate. It answers 422 with one entry per problem
 * under {@code detail}, each naming where the problem is ({@code loc}), its kind ({@code type}), a
 * sentence ({@code msg}), the offending value ({@code input}) and, for a limit, the limit
 * ({@code ctx}).
 */
public final class RequestRejected extends RuntimeException {

    private final transient List<Map<String, Object>> problems;

    /** A rejection listing {@code problems}, which must not be empty. */
    public RequestRejected(List<Map<String, Object>> problems) {
        super(problems.size() + " validation error" + (problems.size() == 1 ? "" : "s"));
        if (problems.isEmpty()) {
            throw new IllegalArgumentException("a rejection needs at least one problem");
        }
        this.problems = List.copyOf(problems);
    }

    /** A rejection naming one problem. */
    public static RequestRejected one(String type, List<Object> loc, String msg, Object input) {
        return one(type, loc, msg, input, Map.of());
    }

    /** A rejection naming one problem and the limit it broke. */
    public static RequestRejected one(
            String type, List<Object> loc, String msg, Object input, Map<String, Object> ctx) {
        Problems problems = new Problems();
        problems.add(type, loc, msg, input, ctx);
        return new RequestRejected(problems.found);
    }

    /** The problems, in the order they were found. */
    public List<Map<String, Object>> problems() {
        return problems;
    }

    /** The response body: {@code {"detail": [...]}}. */
    public Map<String, Object> body() {
        return Map.of("detail", problems);
    }

    /** Collects problems while a request is read. */
    public static final class Problems {

        private final List<Map<String, Object>> found = new ArrayList<>();

        /** Record one problem at {@code loc}. */
        public void add(String type, List<Object> loc, String msg, Object input) {
            add(type, loc, msg, input, Map.of());
        }

        /** Record one problem at {@code loc} with the limit it broke. */
        public void add(String type, List<Object> loc, String msg, Object input, Map<String, Object> ctx) {
            Map<String, Object> problem = new LinkedHashMap<>();
            problem.put("type", type);
            problem.put("loc", List.copyOf(loc));
            problem.put("msg", msg);
            problem.put("input", input);
            if (!ctx.isEmpty()) {
                problem.put("ctx", Collections.unmodifiableMap(new LinkedHashMap<>(ctx)));
            }
            found.add(Collections.unmodifiableMap(problem));
        }

        /** Throw when anything was recorded. */
        public void raiseIfAny() {
            if (!found.isEmpty()) {
                throw new RequestRejected(found);
            }
        }
    }
}
