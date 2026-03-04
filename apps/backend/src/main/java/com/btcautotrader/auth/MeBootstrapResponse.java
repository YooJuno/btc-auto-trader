package com.btcautotrader.auth;

import java.util.Map;

public record MeBootstrapResponse(
        MeBootstrapUserResponse user,
        UserSettingsResponse settings,
        UserExchangeCredentialStatusResponse exchangeCredentials,
        UserOnboardingStateResponse onboarding,
        Map<String, Boolean> features
) {
}
