package com.btcautotrader.auth;

import com.btcautotrader.tenant.TenantDataSourceProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradingAccessServiceTest {
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private UserExchangeCredentialService userExchangeCredentialService;
    @Mock
    private TenantDataSourceProvider tenantDataSourceProvider;
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
        lenient().when(currentUserService.requireUser(authentication)).thenReturn(user);
    }

    @Test
    void pendingUser_isRejectedWhenAdminApprovalEnabled() {
        user.setTradingApprovalStatus(TradingApprovalStatus.PENDING.name());

        TradingAccessService service = new TradingAccessService(
                currentUserService,
                userExchangeCredentialService,
                tenantDataSourceProvider,
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
                tenantDataSourceProvider,
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
                tenantDataSourceProvider,
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
                tenantDataSourceProvider,
                false,
                true,
                "owner@example.com"
        );

        assertDoesNotThrow(() -> service.requireOrderSubmissionAllowed(authentication));
        assertDoesNotThrow(() -> service.requireEngineExecutionAllowed(authentication));
    }

    @Test
    void systemTenant_readyCandidate_isAllowedForAutomatedTrading() {
        user.setTradingApprovalStatus(TradingApprovalStatus.APPROVED.name());
        TradingAccessService service = new TradingAccessService(
                currentUserService,
                userExchangeCredentialService,
                tenantDataSourceProvider,
                false,
                true,
                "owner@example.com"
        );
        UserExchangeCredentialService.TenantTradingPrincipalResolution resolution =
                UserExchangeCredentialService.TenantTradingPrincipalResolution.ready(
                        "btc-auto-trader",
                        user,
                        null,
                        List.of()
                );
        when(userExchangeCredentialService.resolveTradingPrincipalForCurrentTenant()).thenReturn(resolution);

        TradingAccessService.AutomatedTradingAccess access = service.evaluateAutomatedTradingAccessForCurrentTenant();

        assertThat(access.allowed()).isTrue();
        assertThat(access.reason()).isEqualTo("allowed");
    }
}
