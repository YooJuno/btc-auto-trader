package com.btcautotrader.auth;

public record AuthProviderResponse(
        String id,
        String name,
        String authorizationUrl
) {
}
