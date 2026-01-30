package com.juno.btctrader.bot;

import java.math.BigDecimal;
import java.util.List;

import com.juno.btctrader.enums.MarketType;
import com.juno.btctrader.enums.OperationMode;
import com.juno.btctrader.enums.RiskPreset;
import com.juno.btctrader.enums.SelectionMode;
import com.juno.btctrader.enums.StrategyMode;

public record BotDefaultsResponse(
		MarketType defaultMarket,
		SelectionMode defaultSelectionMode,
		StrategyMode defaultStrategyMode,
		RiskPreset defaultRiskPreset,
		OperationMode defaultOperationMode,
		int defaultMaxPositions,
		BigDecimal defaultDailyDrawdownPct,
		BigDecimal defaultWeeklyDrawdownPct,
		int defaultAutoPickTopN,
		int defaultEmaFast,
		int defaultEmaSlow,
		int defaultRsiPeriod,
		int defaultAtrPeriod,
		int defaultBbPeriod,
		double defaultBbStdDev,
		double defaultTrendThreshold,
		double defaultVolatilityHigh,
		int defaultTrendRsiBuyMin,
		int defaultTrendRsiSellMax,
		int defaultRangeRsiBuyMax,
		int defaultRangeRsiSellMin,
		boolean engineEnabled,
		long engineIntervalMs,
		List<MarketType> availableMarkets,
		List<SelectionMode> availableSelectionModes,
		List<StrategyMode> availableStrategyModes,
		List<RiskPreset> availableRiskPresets,
		List<OperationMode> availableOperationModes
) {
}
