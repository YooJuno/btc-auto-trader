package com.btcautotrader.auth;

public record AdminUserDeleteResponse(
        Long userId,
        String tenantDatabase,
        boolean tenantDatabaseDropped
) {
}
