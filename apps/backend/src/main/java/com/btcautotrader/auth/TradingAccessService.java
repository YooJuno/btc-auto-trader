package com.btcautotrader.auth;

import com.btcautotrader.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
public class TradingAccessService {
    private final CurrentUserService currentUserService;
    private final UserExchangeCredentialService userExchangeCredentialService;
    private final boolean ownerOnlyMode;
    private final boolean adminApprovalEnabled;
    private final String ownerEmail;

    public TradingAccessService(
            CurrentUserService currentUserService,
            UserExchangeCredentialService userExchangeCredentialService,
            @Value("${app.trading.owner-only-mode:${APP_TRADING_OWNER_ONLY_MODE:true}}") boolean ownerOnlyMode,
            @Value("${feature.admin-approval.enabled:true}") boolean adminApprovalEnabled,
            @Value("${app.multi-tenant.owner-email:juno980220@gmail.com}") String ownerEmail
    ) {
        this.currentUserService = currentUserService;
        this.userExchangeCredentialService = userExchangeCredentialService;
        this.ownerOnlyMode = ownerOnlyMode;
        this.adminApprovalEnabled = adminApprovalEnabled;
        this.ownerEmail = ownerEmail == null ? "" : ownerEmail.trim().toLowerCase(Locale.ROOT);
    }

    public UserEntity requireOrderSubmissionAllowed(Authentication authentication) {
        UserEntity user = resolveIdentityUser(authentication);
        requireTradingAllowed(user, "주문 실행 권한이 없습니다.");
        return user;
    }

    public UserEntity requireEngineExecutionAllowed(Authentication authentication) {
        UserEntity user = resolveIdentityUser(authentication);
        requireTradingAllowed(user, "엔진 실행 권한이 없습니다.");
        return user;
    }

    public UserEntity requireTenantReadAllowed(Authentication authentication) {
        UserEntity user = resolveIdentityUser(authentication);
        requireTenantReadAllowed(user);
        return user;
    }

    /**
     * Identity lives in the system database only. Controllers under the tenant-scoped paths run with
     * TenantContext already bound by TenantContextInterceptor, so resolving the user without clearing it
     * would query (and insert into) the tenant database's empty app_users table.
     */
    private UserEntity resolveIdentityUser(Authentication authentication) {
        return TenantContext.callWithTenantDatabase(null, () -> currentUserService.requireUser(authentication));
    }

    public String requireTenantDatabase(UserEntity user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        String tenantDatabase = trimToNull(user.getTenantDatabase());
        if (tenantDatabase == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "전용 거래 공간이 준비되지 않았습니다.");
        }
        return tenantDatabase;
    }

    public boolean canRunAutomatedTradingForCurrentTenant() {
        return evaluateAutomatedTradingAccessForCurrentTenant().allowed();
    }

    public AutomatedTradingAccess evaluateAutomatedTradingAccessForCurrentTenant() {
        UserExchangeCredentialService.TenantTradingPrincipalResolution resolution =
                userExchangeCredentialService.resolveTradingPrincipalForCurrentTenant();
        String tenantDatabase = resolution.tenantDatabase();
        List<Long> candidateUserIds = resolution.candidateUserIds();

        switch (resolution.status()) {
            case NO_TENANT -> {
                return AutomatedTradingAccess.denied("no_tenant", tenantDatabase, null, candidateUserIds);
            }
            case NO_USER -> {
                return AutomatedTradingAccess.denied("no_user", tenantDatabase, null, candidateUserIds);
            }
            case NOT_APPROVED -> {
                return AutomatedTradingAccess.denied("not_approved", tenantDatabase, null, candidateUserIds);
            }
            case MISSING_CREDENTIALS -> {
                return AutomatedTradingAccess.denied("missing_credentials", tenantDatabase, null, candidateUserIds);
            }
            case MULTIPLE_CANDIDATES -> {
                return AutomatedTradingAccess.denied("multiple_candidates", tenantDatabase, null, candidateUserIds);
            }
            case READY -> {
                UserEntity user = resolution.user().orElse(null);
                if (user == null) {
                    return AutomatedTradingAccess.denied("no_user", tenantDatabase, null, candidateUserIds);
                }
                if (ownerOnlyMode && !isOwner(user)) {
                    return AutomatedTradingAccess.denied("owner_only_blocked", tenantDatabase, user.getId(), candidateUserIds);
                }
                if (!ownerOnlyMode && adminApprovalEnabled) {
                    TradingApprovalStatus status = TradingApprovalStatus.from(user.getTradingApprovalStatus());
                    if (status != TradingApprovalStatus.APPROVED) {
                        return AutomatedTradingAccess.denied("not_approved", tenantDatabase, user.getId(), candidateUserIds);
                    }
                }
                return AutomatedTradingAccess.allowed(tenantDatabase, user.getId(), candidateUserIds);
            }
            default -> {
                return AutomatedTradingAccess.denied("unknown", tenantDatabase, null, candidateUserIds);
            }
        }
    }

    private void requireTradingAllowed(UserEntity user, String forbiddenMessage) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        if (ownerOnlyMode && !isOwner(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, forbiddenMessage + " (owner 전용 모드)");
        }
        if (!ownerOnlyMode && adminApprovalEnabled) {
            TradingApprovalStatus status = TradingApprovalStatus.from(user.getTradingApprovalStatus());
            if (status == TradingApprovalStatus.SUSPENDED) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자에 의해 거래가 중지되었습니다.");
            }
            if (status != TradingApprovalStatus.APPROVED) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "거래 승인 대기 상태입니다.");
            }
        }
        if (!userExchangeCredentialService.hasCredentialsForUser(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "거래소 API 키를 먼저 등록해주세요.");
        }
        requireTenantDatabase(user);
    }

    private void requireTenantReadAllowed(UserEntity user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        if (adminApprovalEnabled && !isOwner(user)) {
            TradingApprovalStatus status = TradingApprovalStatus.from(user.getTradingApprovalStatus());
            if (status == TradingApprovalStatus.SUSPENDED) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자에 의해 거래가 중지되었습니다.");
            }
            if (status != TradingApprovalStatus.APPROVED) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "거래 승인 대기 상태입니다.");
            }
        }
        requireTenantDatabase(user);
    }

    private boolean isOwner(UserEntity user) {
        if (user == null || ownerEmail.isBlank()) {
            return false;
        }
        String email = user.getEmail();
        if (email == null || email.isBlank()) {
            return false;
        }
        return ownerEmail.equals(email.trim().toLowerCase(Locale.ROOT));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record AutomatedTradingAccess(
            boolean allowed,
            String reason,
            String tenantDatabase,
            Long userId,
            List<Long> candidateUserIds
    ) {
        static AutomatedTradingAccess allowed(String tenantDatabase, Long userId, List<Long> candidateUserIds) {
            return new AutomatedTradingAccess(
                    true,
                    "allowed",
                    tenantDatabase,
                    userId,
                    candidateUserIds == null ? List.of() : List.copyOf(candidateUserIds)
            );
        }

        static AutomatedTradingAccess denied(
                String reason,
                String tenantDatabase,
                Long userId,
                List<Long> candidateUserIds
        ) {
            return new AutomatedTradingAccess(
                    false,
                    reason == null || reason.isBlank() ? "denied" : reason,
                    tenantDatabase,
                    userId,
                    candidateUserIds == null ? List.of() : List.copyOf(candidateUserIds)
            );
        }
    }
}
