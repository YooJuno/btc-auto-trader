package com.btcautotrader.auth;

import java.time.OffsetDateTime;

public record AdminUserItemResponse(
        Long userId,
        String email,
        String displayName,
        OffsetDateTime lastLoginAt,
        String approvalStatus,
        String approvalNote,
        boolean credentialConfigured,
        boolean onboardingCompleted
) {
}
