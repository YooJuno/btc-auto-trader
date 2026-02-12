package com.btcautotrader.engine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "engine_state")
public class EngineStateEntity {
    @Id
    private Long id;

    @Column(nullable = false)
    private boolean running;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public EngineStateEntity() {
    }

    public EngineStateEntity(Long id, boolean running) {
        this.id = id;
        this.running = running;
    }

    @PrePersist
    @PreUpdate
    private void touch() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
