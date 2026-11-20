package io.github.rkaushikdev.galleyworks.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses a request without the expected bearer token, before anything else sees it. The health
 * check and the API documentation stay open, so a load balancer and a reader of the docs need no
 * token.
 */
public final class AuthFilter extends OncePerRequestFilter {

    /** Paths that never need a token. */
    public static final Set<String> EXEMPT = Set.of("/health", "/openapi.json", "/docs", "/redoc");

    /** Prefixes the documentation page loads its own files from. */
    private static final Set<String> EXEMPT_PREFIXES = Set.of("/swagger-ui/", "/openapi.json/");

    private final TokenPolicy policy;
    private final ObjectMapper json;

    /** A filter enforcing {@code policy}, writing its refusals with {@code json}. */
    public AuthFilter(TokenPolicy policy, ObjectMapper json) {
        this.policy = policy;
        this.json = json;
    }

    /** The policy this filter enforces. */
    public TokenPolicy policy() {
        return policy;
    }

    /** True when {@code path} is served without a token. */
    public static boolean isExempt(String path) {
        return EXEMPT.contains(path) || EXEMPT_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!policy.isEnforced() || isExempt(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        Optional<String> presented =
                TokenPolicy.bearerToken(Optional.ofNullable(request.getHeader(HttpHeaders.AUTHORIZATION)));
        if (policy.accepts(presented)) {
            chain.doFilter(request, response);
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "lexdrift.unauthorized");
        body.put("message", "a bearer token is required");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        json.writeValue(response.getOutputStream(), body);
    }
}
