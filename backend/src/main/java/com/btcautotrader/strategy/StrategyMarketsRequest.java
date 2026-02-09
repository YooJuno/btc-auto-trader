package com.btcautotrader.strategy;

import java.util.List;

public record StrategyMarketsRequest(
        List<String> markets
) {
}
