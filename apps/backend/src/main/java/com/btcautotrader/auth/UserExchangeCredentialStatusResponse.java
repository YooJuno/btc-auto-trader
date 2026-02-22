package com.btcautotrader.auth;

import java.time.OffsetDateTime;

public record UserExchangeCredentialStatusResponse(
        boolean configured,
        boolean usingDefaultCredentials,
        OffsetDateTime updatedAt
) {
}
