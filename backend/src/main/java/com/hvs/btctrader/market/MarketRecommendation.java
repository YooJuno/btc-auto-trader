package com.hvs.btctrader.market;

import com.hvs.btctrader.strategy.MarketSnapshot;

public record MarketRecommendation(
		String market,
		double score,
		double lastPrice,
		double volume24h,
		double volatilityPct,
		double trendStrengthPct
) {
	public static MarketRecommendation from(String market, double score, MarketSnapshot snapshot) {
		return new MarketRecommendation(
				market,
				score,
				snapshot.lastPrice(),
				snapshot.volume24h(),
				snapshot.volatilityPct(),
				snapshot.trendStrengthPct()
		);
	}
}
