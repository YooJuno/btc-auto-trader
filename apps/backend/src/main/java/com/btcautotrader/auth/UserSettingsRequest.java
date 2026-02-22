package com.btcautotrader.auth;

import java.util.List;
import java.util.Map;

public record UserSettingsRequest(
        List<String> markets,
        String riskProfile,
        Map<String, Object> uiPrefs
) {
}
