package com.btcautotrader.strategy;

public record StrategyPresetItem(
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
}
