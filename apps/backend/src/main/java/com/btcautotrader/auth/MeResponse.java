package com.btcautotrader.auth;

import java.time.OffsetDateTime;

public record MeResponse(
        Long id,
        String provider,
        String providerUserId,
        String email,
        String displayName,
        String tenantDatabase,
        String approvalStatus,
        String approvalNote,
        OffsetDateTime approvalUpdatedAt,
        boolean owner,
        OffsetDateTime createdAt,
        OffsetDateTime lastLoginAt
) {
    public static MeResponse from(UserEntity user, boolean owner) {
        return new MeResponse(
                user.getId(),
                user.getProvider(),
                user.getProviderUserId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getTenantDatabase(),
                TradingApprovalStatus.from(user.getTradingApprovalStatus()).name(),
                user.getTradingApprovalNote(),
                user.getTradingApprovalUpdatedAt(),
                owner,
                user.getCreatedAt(),
                user.getLastLoginAt()
        );
    }
}
