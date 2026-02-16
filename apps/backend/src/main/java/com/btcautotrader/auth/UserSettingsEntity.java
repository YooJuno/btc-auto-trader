package com.btcautotrader.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "user_settings")
public class UserSettingsEntity {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "preferred_markets", nullable = false, columnDefinition = "TEXT")
    private String preferredMarketsJson;

    @Column(name = "risk_profile", nullable = false, length = 20)
    private String riskProfile;

    @Column(name = "ui_prefs", nullable = false, columnDefinition = "TEXT")
    private String uiPrefsJson;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getPreferredMarketsJson() {
        return preferredMarketsJson;
    }

    public void setPreferredMarketsJson(String preferredMarketsJson) {
        this.preferredMarketsJson = preferredMarketsJson;
    }

    public String getRiskProfile() {
        return riskProfile;
    }

    public void setRiskProfile(String riskProfile) {
        this.riskProfile = riskProfile;
    }

    public String getUiPrefsJson() {
        return uiPrefsJson;
    }

    public void setUiPrefsJson(String uiPrefsJson) {
        this.uiPrefsJson = uiPrefsJson;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }
}
