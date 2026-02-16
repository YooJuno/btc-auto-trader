package com.btcautotrader.auth;

import java.time.OffsetDateTime;

public record MeResponse(
        Long id,
        String provider,
        String providerUserId,
        String email,
        String displayName,
        OffsetDateTime createdAt,
        OffsetDateTime lastLoginAt
) {
    public static MeResponse from(UserEntity user) {
        return new MeResponse(
                user.getId(),
                user.getProvider(),
                user.getProviderUserId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getCreatedAt(),
                user.getLastLoginAt()
        );
    }
}
