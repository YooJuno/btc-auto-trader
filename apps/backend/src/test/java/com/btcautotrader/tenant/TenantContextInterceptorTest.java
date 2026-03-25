package com.btcautotrader.tenant;

import com.btcautotrader.auth.CurrentUserService;
import com.btcautotrader.auth.TradingApprovalStatus;
import com.btcautotrader.auth.UserEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantContextInterceptorTest {
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private TenantDatabaseProvisioningService tenantDatabaseProvisioningService;
    @Mock
    private Authentication authentication;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void preHandle_rejectsTenantScopedRequestWhenTenantIsMissing() throws Exception {
        UserEntity user = new UserEntity();
        user.setTradingApprovalStatus(TradingApprovalStatus.PENDING.name());

        when(authentication.isAuthenticated()).thenReturn(true);
        when(currentUserService.requireUser(authentication)).thenReturn(user);
        when(tenantDatabaseProvisioningService.ensureTenant(user)).thenReturn(user);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        TenantContextInterceptor interceptor = new TenantContextInterceptor(currentUserService, tenantDatabaseProvisioningService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/order/history");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(TenantContext.getTenantDatabase()).isNull();
    }

    @Test
    void preHandle_failsClosedWhenTenantResolutionThrows() throws Exception {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(currentUserService.requireUser(authentication)).thenThrow(new IllegalStateException("boom"));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        TenantContextInterceptor interceptor = new TenantContextInterceptor(currentUserService, tenantDatabaseProvisioningService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/portfolio/summary");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(TenantContext.getTenantDatabase()).isNull();
    }
}
