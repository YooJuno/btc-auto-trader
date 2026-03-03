package com.btcautotrader.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradingAccessServiceTest {
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private UserExchangeCredentialService userExchangeCredentialService;
    @Mock
    private Authentication authentication;

    private UserEntity user;

    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setEmail("user@example.com");
        user.setProvider("google");
        user.setProviderUserId("123");
        user.setTradingApprovalUpdatedAt(OffsetDateTime.now());
        when(currentUserService.requireUser(authentication)).thenReturn(user);
    }

    @Test
    void pendingUser_isRejectedWhenAdminApprovalEnabled() {
        user.setTradingApprovalStatus(TradingApprovalStatus.PENDING.name());

        TradingAccessService service = new TradingAccessService(
                currentUserService,
                userExchangeCredentialService,
                false,
                true,
                "owner@example.com"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.requireOrderSubmissionAllowed(authentication)
        );

        assertThat(exception.getStatusCode().value()).isEqualTo(403);
        assertThat(exception.getReason()).contains("거래 승인 대기");
    }

    @Test
    void suspendedUser_isRejectedImmediately() {
        user.setTradingApprovalStatus(TradingApprovalStatus.SUSPENDED.name());

        TradingAccessService service = new TradingAccessService(
                currentUserService,
                userExchangeCredentialService,
                false,
                true,
                "owner@example.com"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.requireEngineExecutionAllowed(authentication)
        );

        assertThat(exception.getStatusCode().value()).isEqualTo(403);
        assertThat(exception.getReason()).contains("거래가 중지");
    }

    @Test
    void approvedUserWithoutCredentials_isRejected() {
        user.setTradingApprovalStatus(TradingApprovalStatus.APPROVED.name());
        when(userExchangeCredentialService.hasCredentialsForUser(user)).thenReturn(false);

        TradingAccessService service = new TradingAccessService(
                currentUserService,
                userExchangeCredentialService,
                false,
                true,
                "owner@example.com"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.requireOrderSubmissionAllowed(authentication)
        );

        assertThat(exception.getStatusCode().value()).isEqualTo(403);
        assertThat(exception.getReason()).contains("거래소 API 키");
    }

    @Test
    void approvedUserWithCredentials_isAllowed() {
        user.setTradingApprovalStatus(TradingApprovalStatus.APPROVED.name());
        when(userExchangeCredentialService.hasCredentialsForUser(user)).thenReturn(true);

        TradingAccessService service = new TradingAccessService(
                currentUserService,
                userExchangeCredentialService,
                false,
                true,
                "owner@example.com"
        );

        assertDoesNotThrow(() -> service.requireOrderSubmissionAllowed(authentication));
        assertDoesNotThrow(() -> service.requireEngineExecutionAllowed(authentication));
    }
}
