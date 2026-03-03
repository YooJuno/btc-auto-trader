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

    @Column(name = "profile")
    private String profile;

    @Column(name = "stop_exit_pct")
    private double stopExitPct;

    @Column(name = "trend_exit_pct")
    private double trendExitPct;

    @Column(name = "momentum_exit_pct")
    private double momentumExitPct;

    @Column(name = "signal_model")
    private String signalModel;

    @Column(name = "entry_score_threshold")
    private double entryScoreThreshold;

    @Column(name = "exit_score_threshold")
    private double exitScoreThreshold;

    @Column(name = "risk_per_trade_pct")
    private double riskPerTradePct;

    @Column(name = "time_stop_candles")
    private int timeStopCandles;

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
            double partialTakeProfitPct,
            String profile,
            double stopExitPct,
            double trendExitPct,
            double momentumExitPct,
            String signalModel,
            double entryScoreThreshold,
            double exitScoreThreshold,
            double riskPerTradePct,
            int timeStopCandles
    ) {
        this.id = id;
        this.enabled = enabled;
        this.maxOrderKrw = maxOrderKrw;
        this.takeProfitPct = takeProfitPct;
        this.stopLossPct = stopLossPct;
        this.trailingStopPct = trailingStopPct;
        this.partialTakeProfitPct = partialTakeProfitPct;
        this.profile = profile;
        this.stopExitPct = stopExitPct;
        this.trendExitPct = trendExitPct;
        this.momentumExitPct = momentumExitPct;
        this.signalModel = signalModel;
        this.entryScoreThreshold = entryScoreThreshold;
        this.exitScoreThreshold = exitScoreThreshold;
        this.riskPerTradePct = riskPerTradePct;
        this.timeStopCandles = timeStopCandles;
    }

    public static StrategyConfigEntity from(Long id, StrategyConfig config) {
        return new StrategyConfigEntity(
                id,
                config.enabled(),
                config.maxOrderKrw(),
                config.takeProfitPct(),
                config.stopLossPct(),
                config.trailingStopPct(),
                config.partialTakeProfitPct(),
                config.profile(),
                config.stopExitPct(),
                config.trendExitPct(),
                config.momentumExitPct(),
                config.signalModel(),
                config.entryScoreThreshold(),
                config.exitScoreThreshold(),
                config.riskPerTradePct(),
                config.timeStopCandles()
        );
    }

    public StrategyConfig toRecord() {
        return new StrategyConfig(
                enabled,
                maxOrderKrw,
                takeProfitPct,
                stopLossPct,
                trailingStopPct,
                partialTakeProfitPct,
                profile,
                stopExitPct,
                trendExitPct,
                momentumExitPct,
                signalModel,
                entryScoreThreshold,
                exitScoreThreshold,
                riskPerTradePct,
                timeStopCandles
        );
    }

    public void apply(StrategyConfig config) {
        this.enabled = config.enabled();
        this.maxOrderKrw = config.maxOrderKrw();
        this.takeProfitPct = config.takeProfitPct();
        this.stopLossPct = config.stopLossPct();
        this.trailingStopPct = config.trailingStopPct();
        this.partialTakeProfitPct = config.partialTakeProfitPct();
        if (config.profile() != null && !config.profile().isBlank()) {
            this.profile = config.profile();
        }
        this.stopExitPct = config.stopExitPct();
        this.trendExitPct = config.trendExitPct();
        this.momentumExitPct = config.momentumExitPct();
        if (config.signalModel() != null && !config.signalModel().isBlank()) {
            this.signalModel = config.signalModel();
        }
        this.entryScoreThreshold = config.entryScoreThreshold();
        this.exitScoreThreshold = config.exitScoreThreshold();
        this.riskPerTradePct = config.riskPerTradePct();
        this.timeStopCandles = config.timeStopCandles();
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

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
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

    public String getSignalModel() {
        return signalModel;
    }

    public void setSignalModel(String signalModel) {
        this.signalModel = signalModel;
    }

    public double getEntryScoreThreshold() {
        return entryScoreThreshold;
    }

    public void setEntryScoreThreshold(double entryScoreThreshold) {
        this.entryScoreThreshold = entryScoreThreshold;
    }

    public double getExitScoreThreshold() {
        return exitScoreThreshold;
    }

    public void setExitScoreThreshold(double exitScoreThreshold) {
        this.exitScoreThreshold = exitScoreThreshold;
    }

    public double getRiskPerTradePct() {
        return riskPerTradePct;
    }

    public void setRiskPerTradePct(double riskPerTradePct) {
        this.riskPerTradePct = riskPerTradePct;
    }

    public int getTimeStopCandles() {
        return timeStopCandles;
    }

    public void setTimeStopCandles(int timeStopCandles) {
        this.timeStopCandles = timeStopCandles;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
