package com.btcautotrader.tenant;

import com.btcautotrader.auth.TradingApprovalStatus;
import com.btcautotrader.auth.UserEntity;
import com.btcautotrader.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantDatabaseProvisioningServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private TenantDataSourceProvider tenantDataSourceProvider;

    @Test
    void pendingNonOwner_isUnboundFromSystemTenant() {
        UserEntity user = new UserEntity();
        user.setEmail("user@example.com");
        user.setTradingApprovalStatus(TradingApprovalStatus.PENDING.name());
        user.setTenantDatabase("btc_system");

        when(tenantDataSourceProvider.getSystemDatabaseName()).thenReturn("btc_system");
        when(userRepository.save(user)).thenReturn(user);

        TenantDatabaseProvisioningService service = new TenantDatabaseProvisioningService(
                userRepository,
                tenantDataSourceProvider,
                "owner@example.com",
                true
        );

        UserEntity saved = service.ensureTenant(user);

        assertThat(saved.getTenantDatabase()).isNull();
    }
}
