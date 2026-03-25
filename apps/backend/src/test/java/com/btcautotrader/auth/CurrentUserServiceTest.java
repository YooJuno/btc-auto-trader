package com.btcautotrader.auth;

import com.btcautotrader.tenant.TenantDatabaseProvisioningService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentUserServiceTest {

    @Test
    void upsertFromAuthentication_keepsExistingDisplayName() {
        UserRepository userRepository = mock(UserRepository.class);
        TenantDatabaseProvisioningService tenantService = mock(TenantDatabaseProvisioningService.class);
        CurrentUserService service = new CurrentUserService(userRepository, tenantService, "");
        UserEntity existing = new UserEntity();
        existing.setProvider("google");
        existing.setProviderUserId("user-1");
        existing.setDisplayName("내 닉네임");

        when(userRepository.findByProviderAndProviderUserId("google", "user-1")).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);
        when(tenantService.ensureTenant(existing)).thenReturn(existing);

        UserEntity saved = service.upsertFromAuthentication(authentication("google", "user-1", "oauth@example.com", "OAuth Name"));

        assertThat(saved.getDisplayName()).isEqualTo("내 닉네임");
        assertThat(saved.getEmail()).isEqualTo("oauth@example.com");
    }

    @Test
    void updateProfile_trimsAndAllowsClearingDisplayName() {
        UserRepository userRepository = mock(UserRepository.class);
        TenantDatabaseProvisioningService tenantService = mock(TenantDatabaseProvisioningService.class);
        CurrentUserService service = new CurrentUserService(userRepository, tenantService, "");
        UserEntity user = new UserEntity();

        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserEntity renamed = service.updateProfile(user, new UserProfileRequest("  새 닉네임  "));
        assertThat(renamed.getDisplayName()).isEqualTo("새 닉네임");

        UserEntity cleared = service.updateProfile(user, new UserProfileRequest("   "));
        assertThat(cleared.getDisplayName()).isNull();
    }

    @Test
    void updateProfile_rejectsTooLongDisplayName() {
        UserRepository userRepository = mock(UserRepository.class);
        TenantDatabaseProvisioningService tenantService = mock(TenantDatabaseProvisioningService.class);
        CurrentUserService service = new CurrentUserService(userRepository, tenantService, "");
        UserEntity user = new UserEntity();
        String tooLong = "a".repeat(161);

        assertThatThrownBy(() -> service.updateProfile(user, new UserProfileRequest(tooLong)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("닉네임은 160자 이하로 입력해주세요.");
    }

    private static OAuth2AuthenticationToken authentication(
            String provider,
            String subject,
            String email,
            String displayName
    ) {
        Map<String, Object> attributes = Map.of(
                "sub", subject,
                "email", email,
                "name", displayName
        );
        DefaultOAuth2User principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                "sub"
        );
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), provider);
    }
}
