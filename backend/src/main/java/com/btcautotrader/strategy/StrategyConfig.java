package com.btcautotrader.strategy;

public record StrategyConfig(
        boolean enabled,
        double maxOrderKrw,
        double takeProfitPct,
        double stopLossPct
) {
}
