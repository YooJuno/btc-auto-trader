package com.btcautotrader.strategy;

import java.util.Map;

public record StrategyMarketOverrides(
        Map<String, Double> maxOrderKrwByMarket,
        Map<String, String> profileByMarket,
        Map<String, StrategyMarketRatios> ratiosByMarket
) {
}
