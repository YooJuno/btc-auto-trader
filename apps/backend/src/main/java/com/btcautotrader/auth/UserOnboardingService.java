package com.btcautotrader.auth;

import com.btcautotrader.feature.FeatureFlagService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class UserOnboardingService {
    private final UserOnboardingStateRepository onboardingStateRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final UserExchangeCredentialService userExchangeCredentialService;
    private final FeatureFlagService featureFlagService;

    public UserOnboardingService(
            UserOnboardingStateRepository onboardingStateRepository,
            UserSettingsRepository userSettingsRepository,
            UserExchangeCredentialService userExchangeCredentialService,
            FeatureFlagService featureFlagService
    ) {
        this.onboardingStateRepository = onboardingStateRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.userExchangeCredentialService = userExchangeCredentialService;
        this.featureFlagService = featureFlagService;
    }

    @Transactional
    public UserOnboardingStateResponse getState(UserEntity user) {
        if (user == null || user.getId() == null) {
            return new UserOnboardingStateResponse(false, false, false, false, null, null);
        }
        UserOnboardingStateEntity entity = ensureStateEntity(user);
        if (featureFlagService.onboardingEnabled()) {
            return UserOnboardingStateResponse.from(entity);
        }
        return new UserOnboardingStateResponse(true, true, true, true, OffsetDateTime.now(), entity.getUpdatedAt());
    }

    @Transactional
    public UserOnboardingStateResponse patchState(UserEntity user, UserOnboardingStatePatchRequest request) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("user is required");
        }
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
        UserOnboardingStateEntity entity = ensureStateEntity(user);
        if (request.profileCompleted() != null) {
            entity.setProfileCompleted(request.profileCompleted());
        }
        if (request.credentialsCompleted() != null) {
            entity.setCredentialsCompleted(request.credentialsCompleted());
        }
        if (request.strategyCompleted() != null) {
            entity.setStrategyCompleted(request.strategyCompleted());
        }
        UserOnboardingStateEntity saved = onboardingStateRepository.save(entity);
        return UserOnboardingStateResponse.from(saved);
    }

    private UserOnboardingStateEntity ensureStateEntity(UserEntity user) {
        UserOnboardingStateEntity entity = onboardingStateRepository.findById(user.getId()).orElse(null);
        if (entity != null) {
            UserExchangeCredentialStatusResponse credentialStatus = userExchangeCredentialService.getStatus(user);
            if (credentialStatus.configured() || credentialStatus.usingDefaultCredentials()) {
                entity.setCredentialsCompleted(true);
            }
            return onboardingStateRepository.save(entity);
        }

        UserOnboardingStateEntity created = new UserOnboardingStateEntity();
        created.setUserId(user.getId());

        boolean profileCompleted = userSettingsRepository.findById(user.getId()).isPresent();
        UserExchangeCredentialStatusResponse credentialStatus = userExchangeCredentialService.getStatus(user);
        boolean credentialsCompleted = credentialStatus.configured() || credentialStatus.usingDefaultCredentials();

        created.setProfileCompleted(profileCompleted);
        created.setCredentialsCompleted(credentialsCompleted);
        created.setStrategyCompleted(profileCompleted);

        return onboardingStateRepository.save(created);
    }
}
