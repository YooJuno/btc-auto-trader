package com.btcautotrader.auth;

import java.util.Map;

public record MeBootstrapResponse(
        MeBootstrapUserResponse user,
        UserSettingsResponse settings,
        UserExchangeCredentialStatusResponse exchangeCredentials,
        Map<String, Boolean> features
) {
}
