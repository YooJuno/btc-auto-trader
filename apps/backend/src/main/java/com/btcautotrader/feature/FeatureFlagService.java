package com.btcautotrader.feature;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class FeatureFlagService {
    private final boolean onboardingEnabled;
    private final boolean adminApprovalEnabled;
    private final boolean strategyV2Enabled;
    private final boolean strategyV2ShadowMode;

    public FeatureFlagService(
            @Value("${feature.onboarding.enabled:true}") boolean onboardingEnabled,
            @Value("${feature.admin-approval.enabled:true}") boolean adminApprovalEnabled,
            @Value("${feature.strategy.v2.enabled:false}") boolean strategyV2Enabled,
            @Value("${feature.strategy.v2.shadow-mode:true}") boolean strategyV2ShadowMode
    ) {
        this.onboardingEnabled = onboardingEnabled;
        this.adminApprovalEnabled = adminApprovalEnabled;
        this.strategyV2Enabled = strategyV2Enabled;
        this.strategyV2ShadowMode = strategyV2ShadowMode;
    }

    public boolean onboardingEnabled() {
        return onboardingEnabled;
    }

    public boolean adminApprovalEnabled() {
        return adminApprovalEnabled;
    }

    public boolean strategyV2Enabled() {
        return strategyV2Enabled;
    }

    public boolean strategyV2ShadowMode() {
        return strategyV2ShadowMode;
    }

    public Map<String, Boolean> toMap() {
        return Map.ofEntries(
                Map.entry("feature.onboarding.enabled", onboardingEnabled),
                Map.entry("feature.admin-approval.enabled", adminApprovalEnabled),
                Map.entry("feature.strategy.v2.enabled", strategyV2Enabled),
                Map.entry("feature.strategy.v2.shadow-mode", strategyV2ShadowMode),
                Map.entry("onboardingEnabled", onboardingEnabled),
                Map.entry("adminApprovalEnabled", adminApprovalEnabled),
                Map.entry("strategyV2Enabled", strategyV2Enabled),
                Map.entry("strategyV2ShadowMode", strategyV2ShadowMode)
        );
    }
}
