package com.btcautotrader.engine;

import java.math.BigDecimal;

final class UnifiedTrendSignalModel implements TradeSignalModel {
    static final String NAME = "trend_breakout";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public BuySignalDecision evaluateBuy(BuySignalContext context) {
        MarketIndicators indicators = context.indicators();
        SignalTuning tuning = context.tuning();
        if (indicators == null || tuning == null || indicators.currentPrice() == null
                || indicators.maShort() == null || indicators.maLong() == null) {
            return BuySignalDecision.skip("insufficient candles");
        }

        if (indicators.maShort().compareTo(indicators.maLong()) <= 0
                || indicators.currentPrice().compareTo(indicators.maLong()) <= 0) {
            return BuySignalDecision.skip("no trend");
        }
        if (tuning.minMaLongSlopePct() > 0 && indicators.maLongSlopePct() == null) {
            return BuySignalDecision.skip("no trend slope");
        }
        if (indicators.maLongSlopePct() != null
                && indicators.maLongSlopePct().doubleValue() < tuning.minMaLongSlopePct()) {
            return BuySignalDecision.skip("trend weakening");
        }
        if (tuning.maxExtensionPct() > 0) {
            BigDecimal maxEntryPrice = indicators.maLong()
                    .multiply(percentFactor(tuning.maxExtensionPct()));
            if (indicators.currentPrice().compareTo(maxEntryPrice) > 0) {
                return BuySignalDecision.skip("overextended");
            }
        }
        if (tuning.minAdx() > 0) {
            if (indicators.adx() == null) {
                return BuySignalDecision.skip("no adx");
            }
            if (indicators.adx().doubleValue() < tuning.minAdx()) {
                return BuySignalDecision.skip("weak_trend");
            }
        }
        if (tuning.minVolumeRatio() > 0) {
            if (indicators.volumeRatio() == null) {
                return BuySignalDecision.skip("no volume");
            }
            if (indicators.volumeRatio().doubleValue() < tuning.minVolumeRatio()) {
                return BuySignalDecision.skip("low_volume");
            }
        }
        if (indicators.breakoutLevel() == null) {
            return BuySignalDecision.skip("no breakout");
        }
        if (indicators.currentPrice().compareTo(indicators.breakoutLevel()) <= 0) {
            return BuySignalDecision.skip("no breakout");
        }

        return BuySignalDecision.proceed("trend_breakout");
    }

    private static BigDecimal percentFactor(double pct) {
        return BigDecimal.ONE.add(BigDecimal.valueOf(pct).movePointLeft(2));
    }
}
