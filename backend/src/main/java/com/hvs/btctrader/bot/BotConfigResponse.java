package com.hvs.btctrader.bot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.hvs.btctrader.enums.MarketType;
import com.hvs.btctrader.enums.OperationMode;
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
		OperationMode operationMode,
		int maxPositions,
		BigDecimal maxDailyDrawdownPct,
		BigDecimal maxWeeklyDrawdownPct,
		int autoPickTopN,
		String manualMarkets,
		Integer emaFast,
		Integer emaSlow,
		Integer rsiPeriod,
		Integer atrPeriod,
		Integer bbPeriod,
		Double bbStdDev,
		Double trendThreshold,
		Double volatilityHigh,
		Integer trendRsiBuyMin,
		Integer trendRsiSellMax,
		Integer rangeRsiBuyMax,
		Integer rangeRsiSellMin,
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
				config.getOperationMode(),
				config.getMaxPositions(),
				config.getMaxDailyDrawdownPct(),
				config.getMaxWeeklyDrawdownPct(),
				config.getAutoPickTopN(),
				config.getManualMarkets(),
				config.getEmaFast(),
				config.getEmaSlow(),
				config.getRsiPeriod(),
				config.getAtrPeriod(),
				config.getBbPeriod(),
				config.getBbStdDev(),
				config.getTrendThreshold(),
				config.getVolatilityHigh(),
				config.getTrendRsiBuyMin(),
				config.getTrendRsiSellMax(),
				config.getRangeRsiBuyMax(),
				config.getRangeRsiSellMin(),
				config.getCreatedAt()
		);
	}
}
