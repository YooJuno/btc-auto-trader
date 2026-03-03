package com.btcautotrader.auth;

import java.time.OffsetDateTime;

public record UserOnboardingStateResponse(
        boolean profileCompleted,
        boolean credentialsCompleted,
        boolean strategyCompleted,
        boolean completed,
        OffsetDateTime completedAt,
        OffsetDateTime updatedAt
) {
    public static UserOnboardingStateResponse from(UserOnboardingStateEntity entity) {
        if (entity == null) {
            return new UserOnboardingStateResponse(false, false, false, false, null, null);
        }
        boolean completed = entity.isProfileCompleted()
                && entity.isCredentialsCompleted()
                && entity.isStrategyCompleted();
        return new UserOnboardingStateResponse(
                entity.isProfileCompleted(),
                entity.isCredentialsCompleted(),
                entity.isStrategyCompleted(),
                completed,
                entity.getCompletedAt(),
                entity.getUpdatedAt()
        );
    }
}
