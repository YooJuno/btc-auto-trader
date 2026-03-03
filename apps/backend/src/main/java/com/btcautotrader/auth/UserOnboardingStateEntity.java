package com.btcautotrader.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "user_onboarding_state")
public class UserOnboardingStateEntity {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "profile_completed", nullable = false)
    private boolean profileCompleted;

    @Column(name = "credentials_completed", nullable = false)
    private boolean credentialsCompleted;

    @Column(name = "strategy_completed", nullable = false)
    private boolean strategyCompleted;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    private void touch() {
        this.updatedAt = OffsetDateTime.now();
        if (profileCompleted && credentialsCompleted && strategyCompleted) {
            if (completedAt == null) {
                completedAt = OffsetDateTime.now();
            }
        } else {
            completedAt = null;
        }
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public boolean isProfileCompleted() {
        return profileCompleted;
    }

    public void setProfileCompleted(boolean profileCompleted) {
        this.profileCompleted = profileCompleted;
    }

    public boolean isCredentialsCompleted() {
        return credentialsCompleted;
    }

    public void setCredentialsCompleted(boolean credentialsCompleted) {
        this.credentialsCompleted = credentialsCompleted;
    }

    public boolean isStrategyCompleted() {
        return strategyCompleted;
    }

    public void setStrategyCompleted(boolean strategyCompleted) {
        this.strategyCompleted = strategyCompleted;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
