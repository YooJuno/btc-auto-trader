package com.btcautotrader.feature;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureFlagServiceTest {
    @Test
    void toMap_containsPublicFeatureKeys() {
        FeatureFlagService service = new FeatureFlagService(false);

        Map<String, Boolean> flags = service.toMap();

        assertThat(flags).containsEntry("feature.admin-approval.enabled", false);
        assertThat(flags).containsEntry("adminApprovalEnabled", false);
    }
}
