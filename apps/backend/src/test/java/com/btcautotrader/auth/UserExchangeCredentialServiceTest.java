package com.btcautotrader.auth;

import com.btcautotrader.tenant.TenantContext;
import com.btcautotrader.upbit.UpbitAuthCredentials;
import com.btcautotrader.upbit.UpbitCredentials;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserExchangeCredentialServiceTest {
    @Mock
    private UserExchangeCredentialRepository credentialRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CredentialCryptoService credentialCryptoService;
    @Mock
    private UpbitCredentials upbitCredentials;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void resolveCredentialsForCurrentTenant_doesNotFallbackToOwnerWhenTenantMappingMissing() {
        UserExchangeCredentialService service = new UserExchangeCredentialService(
                credentialRepository,
                userRepository,
                credentialCryptoService,
                upbitCredentials,
                "owner@example.com"
        );
        when(userRepository.findFirstByTenantDatabase("btc_system")).thenReturn(Optional.empty());

        Optional<UpbitAuthCredentials> resolved = TenantContext.callWithTenantDatabase(
                "btc_system",
                service::resolveCredentialsForCurrentTenant
        );

        assertThat(resolved).isEmpty();
        verify(userRepository).findFirstByTenantDatabase("btc_system");
        verify(userRepository, never()).findFirstByEmailIgnoreCase(anyString());
    }

    @Test
    void resolveCredentialsForCurrentTenant_usesOwnerDefaultsWhenOwnerIsMappedToTenant() {
        UserExchangeCredentialService service = new UserExchangeCredentialService(
                credentialRepository,
                userRepository,
                credentialCryptoService,
                upbitCredentials,
                "owner@example.com"
        );
        UserEntity owner = mock(UserEntity.class);
        UpbitAuthCredentials defaultCredentials = new UpbitAuthCredentials("ak", "sk");

        when(owner.getId()).thenReturn(1L);
        when(owner.getEmail()).thenReturn("owner@example.com");
        when(userRepository.findFirstByTenantDatabase("btc_system")).thenReturn(Optional.of(owner));
        when(credentialRepository.findById(1L)).thenReturn(Optional.empty());
        when(upbitCredentials.isConfigured()).thenReturn(true);
        when(upbitCredentials.toAuthCredentials()).thenReturn(Optional.of(defaultCredentials));

        Optional<UpbitAuthCredentials> resolved = TenantContext.callWithTenantDatabase(
                "btc_system",
                service::resolveCredentialsForCurrentTenant
        );

        assertThat(resolved).contains(defaultCredentials);
        verify(userRepository).findFirstByTenantDatabase("btc_system");
        verify(userRepository, never()).findFirstByEmailIgnoreCase(anyString());
    }
}
