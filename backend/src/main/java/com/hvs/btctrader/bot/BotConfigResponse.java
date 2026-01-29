package com.hvs.btctrader.bot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.hvs.btctrader.enums.MarketType;
import com.hvs.btctrader.enums.RiskPreset;
import com.hvs.btctrader.enums.SelectionMode;
import com.hvs.btctrader.enums.StrategyMode;

public record BotConfigResponse(
		UUID id,
		String name,
		MarketType baseMarket,
		SelectionMode selectionMode,
		StrategyMode strategyMode,
		RiskPreset riskPreset,
		int maxPositions,
		BigDecimal maxDailyDrawdownPct,
		BigDecimal maxWeeklyDrawdownPct,
		int autoPickTopN,
		String manualMarkets,
		Instant createdAt
) {
	public static BotConfigResponse from(BotConfig config) {
		return new BotConfigResponse(
				config.getId(),
				config.getName(),
				config.getBaseMarket(),
				config.getSelectionMode(),
				config.getStrategyMode(),
				config.getRiskPreset(),
				config.getMaxPositions(),
				config.getMaxDailyDrawdownPct(),
				config.getMaxWeeklyDrawdownPct(),
				config.getAutoPickTopN(),
				config.getManualMarkets(),
				config.getCreatedAt()
		);
	}
}
