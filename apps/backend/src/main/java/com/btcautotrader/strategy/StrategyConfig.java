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
        double momentumExitPct
) {
}
