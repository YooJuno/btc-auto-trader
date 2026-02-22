package com.btcautotrader.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Optional;

@Service
public class TradingAccessService {
    private final CurrentUserService currentUserService;
    private final UserExchangeCredentialService userExchangeCredentialService;
    private final boolean ownerOnlyMode;
    private final String ownerEmail;

    public TradingAccessService(
            CurrentUserService currentUserService,
            UserExchangeCredentialService userExchangeCredentialService,
            @Value("${app.trading.owner-only-mode:${APP_TRADING_OWNER_ONLY_MODE:true}}") boolean ownerOnlyMode,
            @Value("${app.multi-tenant.owner-email:juno980220@gmail.com}") String ownerEmail
    ) {
        this.currentUserService = currentUserService;
        this.userExchangeCredentialService = userExchangeCredentialService;
        this.ownerOnlyMode = ownerOnlyMode;
        this.ownerEmail = ownerEmail == null ? "" : ownerEmail.trim().toLowerCase(Locale.ROOT);
    }

    public void requireOrderSubmissionAllowed(Authentication authentication) {
        UserEntity user = currentUserService.requireUser(authentication);
        requireTradingAllowed(user, "주문 실행 권한이 없습니다.");
    }

    public void requireEngineExecutionAllowed(Authentication authentication) {
        UserEntity user = currentUserService.requireUser(authentication);
        requireTradingAllowed(user, "엔진 실행 권한이 없습니다.");
    }

    public boolean canRunAutomatedTradingForCurrentTenant() {
        Optional<UserEntity> userOptional = userExchangeCredentialService.findUserForCurrentTenant();
        if (userOptional.isEmpty()) {
            return false;
        }
        UserEntity user = userOptional.get();
        if (ownerOnlyMode && !isOwner(user)) {
            return false;
        }
        return userExchangeCredentialService.hasCredentialsForUser(user);
    }

    private void requireTradingAllowed(UserEntity user, String forbiddenMessage) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        if (ownerOnlyMode && !isOwner(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, forbiddenMessage + " (owner 전용 모드)");
        }
        if (!userExchangeCredentialService.hasCredentialsForUser(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "거래소 API 키를 먼저 등록해주세요.");
        }
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
}
