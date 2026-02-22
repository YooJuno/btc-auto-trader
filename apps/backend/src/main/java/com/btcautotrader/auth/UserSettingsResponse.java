package com.btcautotrader.auth;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record UserSettingsResponse(
        List<String> markets,
        String riskProfile,
        Map<String, Object> uiPrefs,
        OffsetDateTime updatedAt
) {
}
