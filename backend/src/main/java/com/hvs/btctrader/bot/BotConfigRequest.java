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

	private String manualMarkets;

	@Min(2)
	@Max(200)
	private Integer emaFast;

	@Min(5)
	@Max(400)
	private Integer emaSlow;

	@Min(5)
	@Max(60)
	private Integer rsiPeriod;

	@Min(5)
	@Max(60)
	private Integer atrPeriod;

	@Min(10)
	@Max(60)
	private Integer bbPeriod;

	@DecimalMin("0.5")
	@DecimalMax("5.0")
	private Double bbStdDev;

	@DecimalMin("0.001")
	@DecimalMax("0.05")
	private Double trendThreshold;

	@DecimalMin("0.01")
	@DecimalMax("0.2")
	private Double volatilityHigh;

	@Min(10)
	@Max(90)
	private Integer trendRsiBuyMin;

	@Min(10)
	@Max(90)
	private Integer trendRsiSellMax;

	@Min(5)
	@Max(70)
	private Integer rangeRsiBuyMax;

	@Min(50)
	@Max(95)
	private Integer rangeRsiSellMin;

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

	public String getManualMarkets() {
		return manualMarkets;
	}

	public void setManualMarkets(String manualMarkets) {
		this.manualMarkets = manualMarkets;
	}

	public Integer getEmaFast() {
		return emaFast;
	}

	public void setEmaFast(Integer emaFast) {
		this.emaFast = emaFast;
	}

	public Integer getEmaSlow() {
		return emaSlow;
	}

	public void setEmaSlow(Integer emaSlow) {
		this.emaSlow = emaSlow;
	}

	public Integer getRsiPeriod() {
		return rsiPeriod;
	}

	public void setRsiPeriod(Integer rsiPeriod) {
		this.rsiPeriod = rsiPeriod;
	}

	public Integer getAtrPeriod() {
		return atrPeriod;
	}

	public void setAtrPeriod(Integer atrPeriod) {
		this.atrPeriod = atrPeriod;
	}

	public Integer getBbPeriod() {
		return bbPeriod;
	}

	public void setBbPeriod(Integer bbPeriod) {
		this.bbPeriod = bbPeriod;
	}

	public Double getBbStdDev() {
		return bbStdDev;
	}

	public void setBbStdDev(Double bbStdDev) {
		this.bbStdDev = bbStdDev;
	}

	public Double getTrendThreshold() {
		return trendThreshold;
	}

	public void setTrendThreshold(Double trendThreshold) {
		this.trendThreshold = trendThreshold;
	}

	public Double getVolatilityHigh() {
		return volatilityHigh;
	}

	public void setVolatilityHigh(Double volatilityHigh) {
		this.volatilityHigh = volatilityHigh;
	}

	public Integer getTrendRsiBuyMin() {
		return trendRsiBuyMin;
	}

	public void setTrendRsiBuyMin(Integer trendRsiBuyMin) {
		this.trendRsiBuyMin = trendRsiBuyMin;
	}

	public Integer getTrendRsiSellMax() {
		return trendRsiSellMax;
	}

	public void setTrendRsiSellMax(Integer trendRsiSellMax) {
		this.trendRsiSellMax = trendRsiSellMax;
	}

	public Integer getRangeRsiBuyMax() {
		return rangeRsiBuyMax;
	}

	public void setRangeRsiBuyMax(Integer rangeRsiBuyMax) {
		this.rangeRsiBuyMax = rangeRsiBuyMax;
	}

	public Integer getRangeRsiSellMin() {
		return rangeRsiSellMin;
	}

	public void setRangeRsiSellMin(Integer rangeRsiSellMin) {
		this.rangeRsiSellMin = rangeRsiSellMin;
	}
}
