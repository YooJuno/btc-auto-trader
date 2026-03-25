package com.btcautotrader.feature;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class FeatureFlagService {
    private final boolean adminApprovalEnabled;

    public FeatureFlagService(
            @Value("${feature.admin-approval.enabled:true}") boolean adminApprovalEnabled
    ) {
        this.adminApprovalEnabled = adminApprovalEnabled;
    }

    public boolean adminApprovalEnabled() {
        return adminApprovalEnabled;
    }

    public Map<String, Boolean> toMap() {
        return Map.ofEntries(
                Map.entry("feature.admin-approval.enabled", adminApprovalEnabled),
                Map.entry("adminApprovalEnabled", adminApprovalEnabled)
        );
    }
}
