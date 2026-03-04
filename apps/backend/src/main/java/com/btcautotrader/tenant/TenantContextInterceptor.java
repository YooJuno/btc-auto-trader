package com.btcautotrader.tenant;

import com.btcautotrader.auth.CurrentUserService;
import com.btcautotrader.auth.UserEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

public class TenantContextInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(TenantContextInterceptor.class);
    private static final List<String> TENANT_PREFIXES = List.of(
            "/api/order",
            "/api/strategy",
            "/api/portfolio",
            "/api/engine"
    );
    private static final List<String> TENANT_EXACT_PATHS = List.of();

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
            // Fall back to system DB so tenant provisioning glitches don't block strategy/order APIs.
            TenantContext.clear();
            log.warn(
                    "Tenant resolution failed for path {}. Falling back to system tenant: {}",
                    path,
                    ex.getMessage()
            );
            return true;
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
