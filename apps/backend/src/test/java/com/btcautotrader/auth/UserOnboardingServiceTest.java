package com.btcautotrader.auth;

import com.btcautotrader.feature.FeatureFlagService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserOnboardingServiceTest {
    @Mock
    private UserOnboardingStateRepository onboardingStateRepository;
    @Mock
    private UserSettingsRepository userSettingsRepository;
    @Mock
    private UserExchangeCredentialService userExchangeCredentialService;
    @Mock
    private FeatureFlagService featureFlagService;

    @Test
    void getState_doesNotSaveExistingEntityWhenDerivedStateDidNotChange() {
        UserEntity user = new UserEntity();
        user.setProvider("google");
        user.setProviderUserId("123");
        user.setTradingApprovalStatus(TradingApprovalStatus.PENDING.name());
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", 7L);

        UserOnboardingStateEntity entity = new UserOnboardingStateEntity();
        entity.setUserId(7L);
        entity.setCredentialsCompleted(false);

        when(onboardingStateRepository.findById(7L)).thenReturn(Optional.of(entity));
        when(userExchangeCredentialService.getStatus(user))
                .thenReturn(new UserExchangeCredentialStatusResponse(false, false, null));
        when(featureFlagService.onboardingEnabled()).thenReturn(true);

        UserOnboardingService service = new UserOnboardingService(
                onboardingStateRepository,
                userSettingsRepository,
                userExchangeCredentialService,
                featureFlagService
        );

        UserOnboardingStateResponse response = service.getState(user);

        assertThat(response.credentialsCompleted()).isFalse();
        verify(onboardingStateRepository, never()).save(entity);
    }
}
