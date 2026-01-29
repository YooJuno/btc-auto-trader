package com.hvs.btctrader.strategy;

public record StrategyParameters(
		int emaFast,
		int emaSlow,
		int rsiPeriod,
		int atrPeriod,
		int bbPeriod,
		double bbStdDev,
		double trendThreshold,
		double volatilityHigh,
		int trendRsiBuyMin,
		int trendRsiSellMax,
		int rangeRsiBuyMax,
		int rangeRsiSellMin
) {
}
