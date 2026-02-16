package com.btcautotrader.auth;

import java.time.OffsetDateTime;

public record UserExchangeCredentialVerifyResponse(
        boolean ok,
        int accountCount,
        boolean usingDefaultCredentials,
        OffsetDateTime checkedAt
) {
}
