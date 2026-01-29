package com.hvs.btctrader.bot;

import java.math.BigDecimal;

import com.hvs.btctrader.enums.MarketType;
import com.hvs.btctrader.enums.RiskPreset;
import com.hvs.btctrader.enums.SelectionMode;
import com.hvs.btctrader.enums.StrategyMode;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class BotConfigRequest {
	@NotBlank
	private String name;

	@NotNull
	private MarketType baseMarket = MarketType.KRW;

	@NotNull
	private SelectionMode selectionMode = SelectionMode.AUTO;

	@NotNull
	private StrategyMode strategyMode = StrategyMode.AUTO;

	@NotNull
	private RiskPreset riskPreset = RiskPreset.STANDARD;

	@Min(1)
	@Max(10)
	private int maxPositions = 3;

	@DecimalMin("0.1")
	@DecimalMax("20.0")
	private BigDecimal maxDailyDrawdownPct = BigDecimal.valueOf(3.0);

	@DecimalMin("0.1")
	@DecimalMax("50.0")
	private BigDecimal maxWeeklyDrawdownPct = BigDecimal.valueOf(8.0);

	@Min(1)
	@Max(20)
	private int autoPickTopN = 5;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public MarketType getBaseMarket() {
		return baseMarket;
	}

	public void setBaseMarket(MarketType baseMarket) {
		this.baseMarket = baseMarket;
	}

	public SelectionMode getSelectionMode() {
		return selectionMode;
	}

	public void setSelectionMode(SelectionMode selectionMode) {
		this.selectionMode = selectionMode;
	}

	public StrategyMode getStrategyMode() {
		return strategyMode;
	}

	public void setStrategyMode(StrategyMode strategyMode) {
		this.strategyMode = strategyMode;
	}

	public RiskPreset getRiskPreset() {
		return riskPreset;
	}

	public void setRiskPreset(RiskPreset riskPreset) {
		this.riskPreset = riskPreset;
	}

	public int getMaxPositions() {
		return maxPositions;
	}

	public void setMaxPositions(int maxPositions) {
		this.maxPositions = maxPositions;
	}

	public BigDecimal getMaxDailyDrawdownPct() {
		return maxDailyDrawdownPct;
	}

	public void setMaxDailyDrawdownPct(BigDecimal maxDailyDrawdownPct) {
		this.maxDailyDrawdownPct = maxDailyDrawdownPct;
	}

	public BigDecimal getMaxWeeklyDrawdownPct() {
		return maxWeeklyDrawdownPct;
	}

	public void setMaxWeeklyDrawdownPct(BigDecimal maxWeeklyDrawdownPct) {
		this.maxWeeklyDrawdownPct = maxWeeklyDrawdownPct;
	}

	public int getAutoPickTopN() {
		return autoPickTopN;
	}

	public void setAutoPickTopN(int autoPickTopN) {
		this.autoPickTopN = autoPickTopN;
	}
}
