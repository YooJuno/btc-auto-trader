package com.btcautotrader.strategy;

import java.util.List;
import java.util.Map;

public record StrategyMarketOverridesResponse(
        List<String> markets,
        Map<String, Double> maxOrderKrwByMarket,
        Map<String, String> profileByMarket
) {
}
