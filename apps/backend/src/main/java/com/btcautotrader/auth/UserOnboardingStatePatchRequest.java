package com.btcautotrader.auth;

public record UserOnboardingStatePatchRequest(
        Boolean profileCompleted,
        Boolean credentialsCompleted,
        Boolean strategyCompleted
) {
}
