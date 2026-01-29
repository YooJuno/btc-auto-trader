package com.hvs.btctrader.bot;

import java.math.BigDecimal;
import java.util.List;

import com.hvs.btctrader.enums.MarketType;
import com.hvs.btctrader.enums.RiskPreset;
import com.hvs.btctrader.enums.SelectionMode;
import com.hvs.btctrader.enums.StrategyMode;

public record BotDefaultsResponse(
		MarketType defaultMarket,
		SelectionMode defaultSelectionMode,
		StrategyMode defaultStrategyMode,
		RiskPreset defaultRiskPreset,
		int defaultMaxPositions,
		BigDecimal defaultDailyDrawdownPct,
		BigDecimal defaultWeeklyDrawdownPct,
		int defaultAutoPickTopN,
		List<MarketType> availableMarkets,
		List<SelectionMode> availableSelectionModes,
		List<StrategyMode> availableStrategyModes,
		List<RiskPreset> availableRiskPresets
) {
}
