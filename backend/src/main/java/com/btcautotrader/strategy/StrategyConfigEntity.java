package com.btcautotrader.strategy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "strategy_config")
public class StrategyConfigEntity {
    @Id
    private Long id;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "max_order_krw", nullable = false)
    private double maxOrderKrw;

    @Column(name = "take_profit_pct", nullable = false)
    private double takeProfitPct;

    @Column(name = "stop_loss_pct", nullable = false)
    private double stopLossPct;

    @Column(name = "trailing_stop_pct")
    private double trailingStopPct;

    @Column(name = "partial_take_profit_pct")
    private double partialTakeProfitPct;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public StrategyConfigEntity() {
    }

    public StrategyConfigEntity(
            Long id,
            boolean enabled,
            double maxOrderKrw,
            double takeProfitPct,
            double stopLossPct,
            double trailingStopPct,
            double partialTakeProfitPct
    ) {
        this.id = id;
        this.enabled = enabled;
        this.maxOrderKrw = maxOrderKrw;
        this.takeProfitPct = takeProfitPct;
        this.stopLossPct = stopLossPct;
        this.trailingStopPct = trailingStopPct;
        this.partialTakeProfitPct = partialTakeProfitPct;
    }

    public static StrategyConfigEntity from(Long id, StrategyConfig config) {
        return new StrategyConfigEntity(
                id,
                config.enabled(),
                config.maxOrderKrw(),
                config.takeProfitPct(),
                config.stopLossPct(),
                config.trailingStopPct(),
                config.partialTakeProfitPct()
        );
    }

    public StrategyConfig toRecord() {
        return new StrategyConfig(
                enabled,
                maxOrderKrw,
                takeProfitPct,
                stopLossPct,
                trailingStopPct,
                partialTakeProfitPct
        );
    }

    public void apply(StrategyConfig config) {
        this.enabled = config.enabled();
        this.maxOrderKrw = config.maxOrderKrw();
        this.takeProfitPct = config.takeProfitPct();
        this.stopLossPct = config.stopLossPct();
        this.trailingStopPct = config.trailingStopPct();
        this.partialTakeProfitPct = config.partialTakeProfitPct();
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getMaxOrderKrw() {
        return maxOrderKrw;
    }

    public void setMaxOrderKrw(double maxOrderKrw) {
        this.maxOrderKrw = maxOrderKrw;
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

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
