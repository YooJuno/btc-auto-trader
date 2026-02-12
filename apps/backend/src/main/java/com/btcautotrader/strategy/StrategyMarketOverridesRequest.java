package com.btcautotrader.strategy;

import java.util.Map;

public record StrategyMarketOverridesRequest(
        Map<String, Double> maxOrderKrwByMarket,
        Map<String, String> profileByMarket,
        Map<String, Boolean> tradePausedByMarket,
        Map<String, StrategyMarketRatios> ratiosByMarket
) {
}
