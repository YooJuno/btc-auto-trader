package com.btcautotrader.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

@Component
public class AuthRedirectUrlResolver {
    private final boolean dynamicEnabled;
    private final int frontendPort;

    public AuthRedirectUrlResolver(
            @Value("${app.auth.redirect.dynamic-enabled:${APP_AUTH_DYNAMIC_REDIRECT_ENABLED:true}}") boolean dynamicEnabled,
            @Value("${app.auth.redirect.frontend-port:${APP_AUTH_FRONTEND_PORT:5173}}") int frontendPort
    ) {
        this.dynamicEnabled = dynamicEnabled;
        this.frontendPort = frontendPort;
    }

    public String resolve(HttpServletRequest request, String configuredUrl) {
        if (!dynamicEnabled || request == null) {
            return fallback(configuredUrl);
        }

        String pathAndQuery = extractPathAndQuery(configuredUrl);
        ResolvedOrigin origin = resolveOrigin(request);
        if (origin == null || origin.host() == null || origin.host().isBlank()) {
            return fallback(configuredUrl);
        }

        int port = origin.port();
        if (port <= 0 && frontendPort > 0) {
            // Fallback only when request origin has no usable port information.
            port = frontendPort;
        }
        StringBuilder redirect = new StringBuilder();
        redirect.append(origin.scheme()).append("://").append(formatHost(origin.host()));
        if (shouldIncludePort(origin.scheme(), port)) {
            redirect.append(":").append(port);
        }
        redirect.append(pathAndQuery);
        return redirect.toString();
    }

    private static String fallback(String configuredUrl) {
        if (configuredUrl == null || configuredUrl.isBlank()) {
            return "/";
        }
        return configuredUrl.trim();
    }

    private static String extractPathAndQuery(String configuredUrl) {
        if (configuredUrl == null || configuredUrl.isBlank()) {
            return "/";
        }
        String trimmed = configuredUrl.trim();
        try {
            URI uri = URI.create(trimmed);
            String path = uri.getRawPath();
            if (path == null || path.isBlank()) {
                path = "/";
            } else if (!path.startsWith("/")) {
                path = "/" + path;
            }
            String query = uri.getRawQuery();
            if (query == null || query.isBlank()) {
                return path;
            }
            return path + "?" + query;
        } catch (Exception ignored) {
            if (trimmed.startsWith("/")) {
                return trimmed;
            }
            return "/" + trimmed;
        }
    }

    private static ResolvedOrigin resolveOrigin(HttpServletRequest request) {
        String scheme = firstForwardedValue(request.getHeader("X-Forwarded-Proto"));
        if (scheme == null || scheme.isBlank()) {
            scheme = request.getScheme();
        }
        if (scheme == null || scheme.isBlank()) {
            scheme = "http";
        }
        scheme = scheme.trim().toLowerCase(Locale.ROOT);

        String forwardedHost = firstForwardedValue(request.getHeader("X-Forwarded-Host"));
        HostPort hostPort = parseHostPort(forwardedHost);
        String host = hostPort.host();
        int port = hostPort.port();

        if (host == null || host.isBlank()) {
            host = request.getServerName();
        }
        if (port <= 0) {
            String forwardedPort = firstForwardedValue(request.getHeader("X-Forwarded-Port"));
            port = parsePort(forwardedPort);
        }
        if (port <= 0) {
            port = request.getServerPort();
        }

        if (host == null || host.isBlank()) {
            return null;
        }
        return new ResolvedOrigin(scheme, host.trim(), port);
    }

    private static String firstForwardedValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] parts = value.split(",");
        if (parts.length == 0) {
            return null;
        }
        String first = parts[0];
        return first == null ? null : first.trim();
    }

    private static HostPort parseHostPort(String rawHost) {
        if (rawHost == null || rawHost.isBlank()) {
            return new HostPort(null, -1);
        }
        String value = rawHost.trim();
        if (value.startsWith("[")) {
            int close = value.indexOf(']');
            if (close > 0) {
                String host = value.substring(1, close);
                if (close + 1 < value.length() && value.charAt(close + 1) == ':') {
                    int port = parsePort(value.substring(close + 2));
                    return new HostPort(host, port);
                }
                return new HostPort(host, -1);
            }
        }

        int firstColon = value.indexOf(':');
        int lastColon = value.lastIndexOf(':');
        if (firstColon > 0 && firstColon == lastColon) {
            String host = value.substring(0, firstColon);
            int port = parsePort(value.substring(firstColon + 1));
            return new HostPort(host, port);
        }

        return new HostPort(value, -1);
    }

    private static int parsePort(String value) {
        if (value == null || value.isBlank()) {
            return -1;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed <= 0 || parsed > 65535) {
                return -1;
            }
            return parsed;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static String formatHost(String host) {
        if (host == null || host.isBlank()) {
            return host;
        }
        String normalized = host.trim();
        if (normalized.contains(":") && !(normalized.startsWith("[") && normalized.endsWith("]"))) {
            return "[" + normalized + "]";
        }
        return normalized;
    }

    private static boolean shouldIncludePort(String scheme, int port) {
        if (port <= 0) {
            return false;
        }
        return !(("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443));
    }

    private record ResolvedOrigin(String scheme, String host, int port) {
    }

    private record HostPort(String host, int port) {
    }
}
