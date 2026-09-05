package com.btcautotrader.strategy;

import java.util.List;
import java.util.Map;

public record StrategyMarketOverridesRequest(
        List<String> markets,
        Map<String, Double> maxOrderKrwByMarket,
        Map<String, String> profileByMarket,
        Map<String, String> signalModelByMarket,
        Map<String, Boolean> tradePausedByMarket,
        Map<String, StrategyMarketRatios> ratiosByMarket
) {
}
