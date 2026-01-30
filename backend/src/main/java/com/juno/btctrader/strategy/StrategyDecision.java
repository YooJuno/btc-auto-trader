package com.juno.btctrader.strategy;

import java.util.List;

public record StrategyDecision(
		SignalAction action,
		String strategy,
		MarketRegime regime,
		double confidence,
		double riskPerTradePct,
		int maxPositions,
		double volatilityPct,
		double trendStrengthPct,
		List<String> reasons
) {
}
