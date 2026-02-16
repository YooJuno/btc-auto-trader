package com.btcautotrader.tenant;

import com.btcautotrader.auth.CurrentUserService;
import com.btcautotrader.auth.UserEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class TenantContextInterceptor implements HandlerInterceptor {
    private static final List<String> TENANT_PREFIXES = List.of(
            "/api/order",
            "/api/strategy",
            "/api/portfolio"
    );
    private static final List<String> TENANT_EXACT_PATHS = List.of(
            "/api/engine/decisions"
    );

    private final CurrentUserService currentUserService;
    private final TenantDatabaseProvisioningService tenantDatabaseProvisioningService;

    public TenantContextInterceptor(
            CurrentUserService currentUserService,
            TenantDatabaseProvisioningService tenantDatabaseProvisioningService
    ) {
        this.currentUserService = currentUserService;
        this.tenantDatabaseProvisioningService = tenantDatabaseProvisioningService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request == null ? null : request.getRequestURI();
        if (!isTenantScopedPath(path)) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return true;
        }

        try {
            UserEntity user = currentUserService.requireUser(authentication);
            user = tenantDatabaseProvisioningService.ensureTenant(user);
            if (user.getTenantDatabase() != null && !user.getTenantDatabase().isBlank()) {
                TenantContext.setTenantDatabase(user.getTenantDatabase().trim());
            }
            return true;
        } catch (RuntimeException ex) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"error\":\"tenant resolution failed\"}");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContext.clear();
    }

    private static boolean isTenantScopedPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        if (TENANT_EXACT_PATHS.contains(path)) {
            return true;
        }
        for (String prefix : TENANT_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
