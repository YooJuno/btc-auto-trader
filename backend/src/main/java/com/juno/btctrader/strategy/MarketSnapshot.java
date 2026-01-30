package com.juno.btctrader.strategy;

public record MarketSnapshot(
		String symbol,
		double lastPrice,
		double volume24h,
		double spreadPct,
		double volatilityPct,
		double trendStrengthPct
) {
}
