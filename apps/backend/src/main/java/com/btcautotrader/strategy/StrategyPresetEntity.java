package com.btcautotrader.strategy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "strategy_presets")
public class StrategyPresetEntity {
    @Id
    @Column(nullable = false, length = 40)
    private String code;

    @Column(name = "display_name", nullable = false, length = 60)
    private String displayName;

    @Column(name = "take_profit_pct", nullable = false)
    private double takeProfitPct;

    @Column(name = "stop_loss_pct", nullable = false)
    private double stopLossPct;

    @Column(name = "trailing_stop_pct", nullable = false)
    private double trailingStopPct;

    @Column(name = "partial_take_profit_pct", nullable = false)
    private double partialTakeProfitPct;

    @Column(name = "stop_exit_pct", nullable = false)
    private double stopExitPct;

    @Column(name = "trend_exit_pct", nullable = false)
    private double trendExitPct;

    @Column(name = "momentum_exit_pct", nullable = false)
    private double momentumExitPct;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public StrategyPresetEntity() {
    }

    public StrategyPresetEntity(
            String code,
            String displayName,
            double takeProfitPct,
            double stopLossPct,
            double trailingStopPct,
            double partialTakeProfitPct,
            double stopExitPct,
            double trendExitPct,
            double momentumExitPct
    ) {
        this.code = code;
        this.displayName = displayName;
        this.takeProfitPct = takeProfitPct;
        this.stopLossPct = stopLossPct;
        this.trailingStopPct = trailingStopPct;
        this.partialTakeProfitPct = partialTakeProfitPct;
        this.stopExitPct = stopExitPct;
        this.trendExitPct = trendExitPct;
        this.momentumExitPct = momentumExitPct;
    }

    public static StrategyPresetEntity from(StrategyPresetItem item) {
        return new StrategyPresetEntity(
                item.code(),
                item.displayName(),
                item.takeProfitPct(),
                item.stopLossPct(),
                item.trailingStopPct(),
                item.partialTakeProfitPct(),
                item.stopExitPct(),
                item.trendExitPct(),
                item.momentumExitPct()
        );
    }

    public StrategyPresetItem toItem() {
        return new StrategyPresetItem(
                code,
                displayName,
                takeProfitPct,
                stopLossPct,
                trailingStopPct,
                partialTakeProfitPct,
                stopExitPct,
                trendExitPct,
                momentumExitPct
        );
    }

    @PrePersist
    @PreUpdate
    private void touch() {
        this.updatedAt = OffsetDateTime.now();
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public double getTakeProfitPct() {
        return takeProfitPct;
    }

    public void setTakeProfitPct(double takeProfitPct) {
        this.takeProfitPct = takeProfitPct;
    }

    public double getStopLossPct() {
        return stopLossPct;
    }

    public void setStopLossPct(double stopLossPct) {
        this.stopLossPct = stopLossPct;
    }

    public double getTrailingStopPct() {
        return trailingStopPct;
    }

    public void setTrailingStopPct(double trailingStopPct) {
        this.trailingStopPct = trailingStopPct;
    }

    public double getPartialTakeProfitPct() {
        return partialTakeProfitPct;
    }

    public void setPartialTakeProfitPct(double partialTakeProfitPct) {
        this.partialTakeProfitPct = partialTakeProfitPct;
    }

    public double getStopExitPct() {
        return stopExitPct;
    }

    public void setStopExitPct(double stopExitPct) {
        this.stopExitPct = stopExitPct;
    }

    public double getTrendExitPct() {
        return trendExitPct;
    }

    public void setTrendExitPct(double trendExitPct) {
        this.trendExitPct = trendExitPct;
    }

    public double getMomentumExitPct() {
        return momentumExitPct;
    }

    public void setMomentumExitPct(double momentumExitPct) {
        this.momentumExitPct = momentumExitPct;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
