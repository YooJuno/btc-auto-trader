package com.btcautotrader.strategy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "strategy_market_overrides")
public class StrategyMarketOverrideEntity {
    @Id
    @Column(nullable = false, length = 20)
    private String market;

    @Column(name = "max_order_krw")
    private Double maxOrderKrw;

    @Column(name = "profile", length = 20)
    private String profile;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public StrategyMarketOverrideEntity() {
    }

    public StrategyMarketOverrideEntity(String market, Double maxOrderKrw, String profile) {
        this.market = market;
        this.maxOrderKrw = maxOrderKrw;
        this.profile = profile;
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

    public Double getMaxOrderKrw() {
        return maxOrderKrw;
    }

    public void setMaxOrderKrw(Double maxOrderKrw) {
        this.maxOrderKrw = maxOrderKrw;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
