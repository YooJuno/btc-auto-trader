package com.btcautotrader.strategy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "strategy_markets")
public class StrategyMarketEntity {
    @Id
    @Column(nullable = false, length = 20)
    private String market;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public StrategyMarketEntity() {
    }

    public StrategyMarketEntity(String market) {
        this.market = market;
    }

    @PrePersist
    @PreUpdate
    private void touch() {
        this.updatedAt = OffsetDateTime.now();
    }

    public String getMarket() {
        return market;
    }

    public void setMarket(String market) {
        this.market = market;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
