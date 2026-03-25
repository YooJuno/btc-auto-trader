package com.btcautotrader.engine;

interface TradeSignalModel {
    BuySignalDecision evaluateBuy(BuySignalContext context);
}

record BuySignalContext(
        MarketIndicators indicators,
        SignalTuning tuning,
        double bollingerMinBandwidthPct,
        double bollingerMaxPercentB
) {
}

record BuySignalDecision(
        boolean proceed,
        String reason,
        boolean includeOrderFundsInSkip
) {
    static BuySignalDecision proceed(String entryReason) {
        return new BuySignalDecision(true, entryReason, false);
    }

    static BuySignalDecision skip(String reason) {
        return new BuySignalDecision(false, reason, false);
    }

    static BuySignalDecision skipWithOrderFunds(String reason) {
        return new BuySignalDecision(false, reason, true);
    }
}
