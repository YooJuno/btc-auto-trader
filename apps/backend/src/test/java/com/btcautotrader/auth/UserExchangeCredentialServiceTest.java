package com.btcautotrader.auth;

import com.btcautotrader.tenant.TenantContext;
import com.btcautotrader.tenant.TenantDataSourceProvider;
import com.btcautotrader.upbit.UpbitAuthCredentials;
import com.btcautotrader.upbit.UpbitCredentials;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
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
    @Mock
    private TenantDataSourceProvider tenantDataSourceProvider;

    @BeforeEach
    void setUp() {
        when(tenantDataSourceProvider.getSystemDatabaseName()).thenReturn("btc_system");
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void resolveTradingPrincipalForCurrentTenant_returnsNoUserWhenTenantHasNoUsers() {
        UserExchangeCredentialService service = newService();
        when(userRepository.findAllByTenantDatabaseOrderByIdAsc("btc_system")).thenReturn(List.of());

        UserExchangeCredentialService.TenantTradingPrincipalResolution resolution = TenantContext.callWithTenantDatabase(
                "btc_system",
                service::resolveTradingPrincipalForCurrentTenant
        );

        assertThat(resolution.status()).isEqualTo(UserExchangeCredentialService.TenantTradingPrincipalStatus.NO_USER);
        assertThat(resolution.tenantDatabase()).isEqualTo("btc_system");
        assertThat(service.resolveCredentialsForCurrentTenant()).isEmpty();
    }

    @Test
    void resolveCredentialsForCurrentTenant_usesOwnerDefaultsForSingleApprovedOwnerCandidate() {
        UserExchangeCredentialService service = newService();
        UserEntity owner = user(1L, TradingApprovalStatus.APPROVED);
        UpbitAuthCredentials defaultCredentials = new UpbitAuthCredentials("ak", "sk");

        when(owner.getEmail()).thenReturn("owner@example.com");
        when(userRepository.findAllByTenantDatabaseOrderByIdAsc("btc_system")).thenReturn(List.of(owner));
        when(credentialRepository.findById(1L)).thenReturn(Optional.empty());
        when(upbitCredentials.isConfigured()).thenReturn(true);
        when(upbitCredentials.toAuthCredentials()).thenReturn(Optional.of(defaultCredentials));

        UserExchangeCredentialService.TenantTradingPrincipalResolution resolution = TenantContext.callWithTenantDatabase(
                "btc_system",
                service::resolveTradingPrincipalForCurrentTenant
        );

        assertThat(resolution.status()).isEqualTo(UserExchangeCredentialService.TenantTradingPrincipalStatus.READY);
        assertThat(resolution.user()).contains(owner);
        assertThat(resolution.credentials()).contains(defaultCredentials);
        assertThat(resolution.candidateUserIds()).containsExactly(1L);
    }

    @Test
    void resolveTradingPrincipalForCurrentTenant_blocksWhenMultipleApprovedCredentialCandidatesExist() {
        UserExchangeCredentialService service = newService();
        UserEntity userA = user(11L, TradingApprovalStatus.APPROVED);
        UserEntity userB = user(12L, TradingApprovalStatus.APPROVED);

        UserExchangeCredentialEntity credsA = credential(11L, "enc-ak-a", "enc-sk-a");
        UserExchangeCredentialEntity credsB = credential(12L, "enc-ak-b", "enc-sk-b");

        when(userRepository.findAllByTenantDatabaseOrderByIdAsc("btc_user_99")).thenReturn(List.of(userA, userB));
        when(credentialRepository.findById(11L)).thenReturn(Optional.of(credsA));
        when(credentialRepository.findById(12L)).thenReturn(Optional.of(credsB));
        when(credentialCryptoService.decrypt("enc-ak-a")).thenReturn("ak-a");
        when(credentialCryptoService.decrypt("enc-sk-a")).thenReturn("sk-a");
        when(credentialCryptoService.decrypt("enc-ak-b")).thenReturn("ak-b");
        when(credentialCryptoService.decrypt("enc-sk-b")).thenReturn("sk-b");

        UserExchangeCredentialService.TenantTradingPrincipalResolution resolution = TenantContext.callWithTenantDatabase(
                "btc_user_99",
                service::resolveTradingPrincipalForCurrentTenant
        );

        assertThat(resolution.status()).isEqualTo(UserExchangeCredentialService.TenantTradingPrincipalStatus.MULTIPLE_CANDIDATES);
        assertThat(resolution.candidateUserIds()).containsExactly(11L, 12L);
        assertThat(service.resolveCredentialsForCurrentTenant()).isEmpty();
    }

    @Test
    void resolveTradingPrincipalForCurrentTenant_systemTenantUsesOwnerOnly() {
        UserExchangeCredentialService service = newService();
        UserEntity owner = user(1L, TradingApprovalStatus.APPROVED);
        UserEntity nonOwner = user(2L, TradingApprovalStatus.APPROVED);
        UserExchangeCredentialEntity ownerCreds = credential(1L, "enc-ak-owner", "enc-sk-owner");

        when(owner.getEmail()).thenReturn("owner@example.com");
        when(userRepository.findAllByTenantDatabaseOrderByIdAsc("btc_system")).thenReturn(List.of(owner, nonOwner));
        when(credentialRepository.findById(1L)).thenReturn(Optional.of(ownerCreds));
        when(credentialCryptoService.decrypt("enc-ak-owner")).thenReturn("ak-owner");
        when(credentialCryptoService.decrypt("enc-sk-owner")).thenReturn("sk-owner");

        UserExchangeCredentialService.TenantTradingPrincipalResolution resolution = TenantContext.callWithTenantDatabase(
                "btc_system",
                service::resolveTradingPrincipalForCurrentTenant
        );

        assertThat(resolution.status()).isEqualTo(UserExchangeCredentialService.TenantTradingPrincipalStatus.READY);
        assertThat(resolution.user()).contains(owner);
        assertThat(resolution.candidateUserIds()).containsExactly(1L);
        assertThat(TenantContext.callWithTenantDatabase("btc_system", service::resolveCredentialsForCurrentTenant))
                .contains(new UpbitAuthCredentials("ak-owner", "sk-owner"));
    }

    private UserExchangeCredentialService newService() {
        return new UserExchangeCredentialService(
                credentialRepository,
                userRepository,
                credentialCryptoService,
                upbitCredentials,
                tenantDataSourceProvider,
                "owner@example.com"
        );
    }

    private static UserEntity user(Long id, TradingApprovalStatus approvalStatus) {
        UserEntity user = mock(UserEntity.class);
        lenient().when(user.getId()).thenReturn(id);
        lenient().when(user.getTradingApprovalStatus()).thenReturn(approvalStatus.name());
        return user;
    }

    private static UserExchangeCredentialEntity credential(Long userId, String accessKeyEncrypted, String secretKeyEncrypted) {
        UserExchangeCredentialEntity entity = new UserExchangeCredentialEntity();
        entity.setUserId(userId);
        entity.setAccessKeyEncrypted(accessKeyEncrypted);
        entity.setSecretKeyEncrypted(secretKeyEncrypted);
        return entity;
    }
}
