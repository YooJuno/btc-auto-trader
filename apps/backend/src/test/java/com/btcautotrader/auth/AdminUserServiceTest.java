package com.btcautotrader.auth;

import com.btcautotrader.tenant.TenantDatabaseProvisioningService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserExchangeCredentialService userExchangeCredentialService;
    @Mock
    private UserOnboardingService userOnboardingService;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private TenantDatabaseProvisioningService tenantDatabaseProvisioningService;

    private AdminUserService service() {
        return new AdminUserService(
                userRepository,
                userExchangeCredentialService,
                userOnboardingService,
                currentUserService,
                tenantDatabaseProvisioningService
        );
    }

    @Test
    void updateApproval_rebindsTenantImmediatelyAndReturnsProvisioningMetadata() {
        AdminUserService service = service();

        UserEntity user = new UserEntity();
        ReflectionTestUtils.setField(user, "id", 21L);
        user.setEmail("user21@example.com");
        user.setTradingApprovalStatus(TradingApprovalStatus.PENDING.name());
        user.setTenantDatabase("btc-auto-trader");

        UserEntity provisioned = new UserEntity();
        ReflectionTestUtils.setField(provisioned, "id", 21L);
        provisioned.setEmail("user21@example.com");
        provisioned.setTradingApprovalStatus(TradingApprovalStatus.APPROVED.name());
        provisioned.setTenantDatabase("btc_user_21");
        provisioned.setTradingApprovalNote("approved");
        provisioned.setTradingApprovalUpdatedAt(OffsetDateTime.parse("2026-03-03T00:00:00Z"));

        OffsetDateTime tenantProvisionedAt = OffsetDateTime.parse("2026-03-03T00:00:05Z");

        when(userRepository.findById(21L)).thenReturn(Optional.of(user));
        when(currentUserService.isOwner(user)).thenReturn(false);
        when(userRepository.save(user)).thenReturn(user);
        when(tenantDatabaseProvisioningService.ensureTenant(user)).thenReturn(provisioned);
        when(tenantDatabaseProvisioningService.resolveTenantProvisionedAt("btc_user_21"))
                .thenReturn(tenantProvisionedAt);

        AdminApprovalUpdateResponse response = service.updateApproval(
                21L,
                new AdminApprovalUpdateRequest(TradingApprovalStatus.APPROVED.name(), "approved")
        );

        assertThat(response.userId()).isEqualTo(21L);
        assertThat(response.approvalStatus()).isEqualTo(TradingApprovalStatus.APPROVED.name());
        assertThat(response.tenantDb()).isEqualTo("btc_user_21");
        assertThat(response.tenantProvisionedAt()).isEqualTo(tenantProvisionedAt);

        verify(tenantDatabaseProvisioningService).ensureTenant(user);
    }

    @Test
    void deleteUser_dropsDedicatedTenantDatabaseWhenUnshared() {
        AdminUserService service = service();

        UserEntity user = new UserEntity();
        ReflectionTestUtils.setField(user, "id", 31L);
        user.setEmail("user31@example.com");
        user.setTenantDatabase("btc_user_31");

        when(userRepository.findById(31L)).thenReturn(Optional.of(user));
        when(currentUserService.isOwner(user)).thenReturn(false);
        when(tenantDatabaseProvisioningService.isSystemTenantDatabase("btc_user_31")).thenReturn(false);
        when(userRepository.findAllByTenantDatabaseOrderByIdAsc("btc_user_31")).thenReturn(List.of(user));
        when(tenantDatabaseProvisioningService.dropDedicatedTenantDatabase("btc_user_31")).thenReturn(true);

        AdminUserDeleteResponse response = service.deleteUser(31L);

        assertThat(response.userId()).isEqualTo(31L);
        assertThat(response.tenantDatabase()).isEqualTo("btc_user_31");
        assertThat(response.tenantDatabaseDropped()).isTrue();
        verify(userRepository).delete(user);
        verify(tenantDatabaseProvisioningService).dropDedicatedTenantDatabase("btc_user_31");
    }

    @Test
    void deleteUser_rejectsOwnerAccountDeletion() {
        AdminUserService service = service();

        UserEntity owner = new UserEntity();
        ReflectionTestUtils.setField(owner, "id", 1L);
        owner.setEmail("owner@example.com");

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(currentUserService.isOwner(owner)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteUser(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("owner account cannot be deleted");

        verify(userRepository, never()).delete(owner);
    }

    @Test
    void deleteUser_rejectsWhenTenantDatabaseIsSharedByAnotherUser() {
        AdminUserService service = service();

        UserEntity target = new UserEntity();
        ReflectionTestUtils.setField(target, "id", 42L);
        target.setEmail("target@example.com");
        target.setTenantDatabase("btc_user_42");

        UserEntity other = new UserEntity();
        ReflectionTestUtils.setField(other, "id", 99L);
        other.setEmail("other@example.com");
        other.setTenantDatabase("btc_user_42");

        when(userRepository.findById(42L)).thenReturn(Optional.of(target));
        when(currentUserService.isOwner(target)).thenReturn(false);
        when(tenantDatabaseProvisioningService.isSystemTenantDatabase("btc_user_42")).thenReturn(false);
        when(userRepository.findAllByTenantDatabaseOrderByIdAsc("btc_user_42")).thenReturn(List.of(target, other));

        assertThatThrownBy(() -> service.deleteUser(42L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tenant database is shared by multiple users");

        verify(userRepository, never()).delete(target);
        verify(tenantDatabaseProvisioningService, never()).dropDedicatedTenantDatabase("btc_user_42");
    }
}
