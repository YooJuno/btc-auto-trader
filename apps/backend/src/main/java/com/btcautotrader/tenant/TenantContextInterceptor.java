package com.btcautotrader.tenant;

import com.btcautotrader.auth.TradingApprovalStatus;
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
            String tenantDatabase = trimToNull(user.getTenantDatabase());
            if (tenantDatabase == null) {
                TradingApprovalStatus approvalStatus = TradingApprovalStatus.from(user.getTradingApprovalStatus());
                int status = approvalStatus == TradingApprovalStatus.APPROVED
                        ? HttpServletResponse.SC_SERVICE_UNAVAILABLE
                        : HttpServletResponse.SC_FORBIDDEN;
                response.sendError(status, tenantUnavailableMessage(approvalStatus));
                return false;
            }
            TenantContext.setTenantDatabase(tenantDatabase);
            return true;
        } catch (RuntimeException ex) {
            TenantContext.clear();
            log.warn("Tenant resolution failed for path {}: {}", path, ex.getMessage());
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "전용 거래 공간 조회에 실패했습니다. 잠시 후 다시 시도해주세요.");
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

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String tenantUnavailableMessage(TradingApprovalStatus approvalStatus) {
        if (approvalStatus == TradingApprovalStatus.SUSPENDED) {
            return "관리자에 의해 거래가 중지되었습니다.";
        }
        if (approvalStatus == TradingApprovalStatus.APPROVED) {
            return "전용 거래 공간이 준비되지 않았습니다.";
        }
        return "거래 승인 대기 상태입니다.";
    }
}
