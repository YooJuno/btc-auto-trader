package com.btcautotrader.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "user_exchange_credentials")
public class UserExchangeCredentialEntity {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "access_key_encrypted", nullable = false, columnDefinition = "TEXT")
    private String accessKeyEncrypted;

    @Column(name = "secret_key_encrypted", nullable = false, columnDefinition = "TEXT")
    private String secretKeyEncrypted;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getAccessKeyEncrypted() {
        return accessKeyEncrypted;
    }

    public void setAccessKeyEncrypted(String accessKeyEncrypted) {
        this.accessKeyEncrypted = accessKeyEncrypted;
    }

    public String getSecretKeyEncrypted() {
        return secretKeyEncrypted;
    }

    public void setSecretKeyEncrypted(String secretKeyEncrypted) {
        this.secretKeyEncrypted = secretKeyEncrypted;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
