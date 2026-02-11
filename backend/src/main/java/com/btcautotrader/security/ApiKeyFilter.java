package com.btcautotrader.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiKeyFilter extends OncePerRequestFilter {
    private static final List<String> PROTECTED_PREFIXES = List.of(
            "/api/order",
            "/api/engine",
            "/api/strategy",
            "/api/portfolio"
    );

    private final boolean enabled;
    private final String headerName;
    private final String apiKey;

    public ApiKeyFilter(
            @Value("${api.auth.enabled:false}") boolean enabled,
            @Value("${api.auth.header:X-API-KEY}") String headerName,
            @Value("${api.auth.key:}") String apiKey
    ) {
        this.enabled = enabled;
        this.headerName = headerName == null || headerName.isBlank() ? "X-API-KEY" : headerName.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!enabled || !isProtected(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (apiKey.isBlank()) {
            writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "api auth enabled but key is empty");
            return;
        }

        String provided = request.getHeader(headerName);
        if (provided == null || !provided.equals(apiKey)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "invalid api key");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static boolean isProtected(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return false;
        }
        for (String prefix : PROTECTED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
