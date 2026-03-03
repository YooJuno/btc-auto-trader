package com.btcautotrader.feature;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureFlagServiceTest {
    @Test
    void toMap_containsPublicFeatureKeys() {
        FeatureFlagService service = new FeatureFlagService(true, false, true, true);

        Map<String, Boolean> flags = service.toMap();

        assertThat(flags).containsEntry("feature.onboarding.enabled", true);
        assertThat(flags).containsEntry("feature.admin-approval.enabled", false);
        assertThat(flags).containsEntry("feature.strategy.v2.enabled", true);
        assertThat(flags).containsEntry("feature.strategy.v2.shadow-mode", true);
    }
}
