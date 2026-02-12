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

    @Column(name = "trade_paused")
    private Boolean tradePaused;

    @Column(name = "take_profit_pct")
    private Double takeProfitPct;

    @Column(name = "stop_loss_pct")
    private Double stopLossPct;

    @Column(name = "trailing_stop_pct")
    private Double trailingStopPct;

    @Column(name = "partial_take_profit_pct")
    private Double partialTakeProfitPct;

    @Column(name = "stop_exit_pct")
    private Double stopExitPct;

    @Column(name = "trend_exit_pct")
    private Double trendExitPct;

    @Column(name = "momentum_exit_pct")
    private Double momentumExitPct;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public StrategyMarketOverrideEntity() {
    }

    public StrategyMarketOverrideEntity(
            String market,
            Double maxOrderKrw,
            String profile,
            Boolean tradePaused,
            Double takeProfitPct,
            Double stopLossPct,
            Double trailingStopPct,
            Double partialTakeProfitPct,
            Double stopExitPct,
            Double trendExitPct,
            Double momentumExitPct
    ) {
        this.market = market;
        this.maxOrderKrw = maxOrderKrw;
        this.profile = profile;
        this.tradePaused = tradePaused;
        this.takeProfitPct = takeProfitPct;
        this.stopLossPct = stopLossPct;
        this.trailingStopPct = trailingStopPct;
        this.partialTakeProfitPct = partialTakeProfitPct;
        this.stopExitPct = stopExitPct;
        this.trendExitPct = trendExitPct;
        this.momentumExitPct = momentumExitPct;
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

    public Boolean getTradePaused() {
        return tradePaused;
    }

    public void setTradePaused(Boolean tradePaused) {
        this.tradePaused = tradePaused;
    }

    public Double getTakeProfitPct() {
        return takeProfitPct;
    }

    public void setTakeProfitPct(Double takeProfitPct) {
        this.takeProfitPct = takeProfitPct;
    }

    public Double getStopLossPct() {
        return stopLossPct;
    }

    public void setStopLossPct(Double stopLossPct) {
        this.stopLossPct = stopLossPct;
    }

    public Double getTrailingStopPct() {
        return trailingStopPct;
    }

    public void setTrailingStopPct(Double trailingStopPct) {
        this.trailingStopPct = trailingStopPct;
    }

    public Double getPartialTakeProfitPct() {
        return partialTakeProfitPct;
    }

    public void setPartialTakeProfitPct(Double partialTakeProfitPct) {
        this.partialTakeProfitPct = partialTakeProfitPct;
    }

    public Double getStopExitPct() {
        return stopExitPct;
    }

    public void setStopExitPct(Double stopExitPct) {
        this.stopExitPct = stopExitPct;
    }

    public Double getTrendExitPct() {
        return trendExitPct;
    }

    public void setTrendExitPct(Double trendExitPct) {
        this.trendExitPct = trendExitPct;
    }

    public Double getMomentumExitPct() {
        return momentumExitPct;
    }

    public void setMomentumExitPct(Double momentumExitPct) {
        this.momentumExitPct = momentumExitPct;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
