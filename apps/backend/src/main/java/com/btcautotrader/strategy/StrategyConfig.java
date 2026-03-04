package com.btcautotrader.strategy;

public record StrategyConfig(
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
}
