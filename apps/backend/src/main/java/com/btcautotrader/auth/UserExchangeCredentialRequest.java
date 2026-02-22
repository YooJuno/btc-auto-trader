package com.btcautotrader.auth;

public record UserExchangeCredentialRequest(
        String accessKey,
        String secretKey
) {
}
