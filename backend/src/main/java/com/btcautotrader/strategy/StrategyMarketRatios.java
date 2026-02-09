package com.btcautotrader.strategy;

public record StrategyMarketRatios(
        Double takeProfitPct,
        Double stopLossPct,
        Double trailingStopPct,
        Double partialTakeProfitPct,
        Double stopExitPct,
        Double trendExitPct,
        Double momentumExitPct
) {
}
