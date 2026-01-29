package com.hvs.btctrader.bot;

import java.math.BigDecimal;
import java.util.UUID;

import com.hvs.btctrader.common.BaseEntity;
import com.hvs.btctrader.enums.MarketType;
import com.hvs.btctrader.enums.RiskPreset;
import com.hvs.btctrader.enums.SelectionMode;
import com.hvs.btctrader.enums.StrategyMode;
import com.hvs.btctrader.tenants.Tenant;
import com.hvs.btctrader.users.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "bot_configs")
public class BotConfig extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tenant_id", nullable = false)
	private Tenant tenant;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false)
	private User owner;

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private MarketType baseMarket = MarketType.KRW;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SelectionMode selectionMode = SelectionMode.AUTO;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private StrategyMode strategyMode = StrategyMode.AUTO;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private RiskPreset riskPreset = RiskPreset.STANDARD;

	@Column(nullable = false)
	private int maxPositions = 3;

	@Column(nullable = false, precision = 5, scale = 2)
	private BigDecimal maxDailyDrawdownPct = BigDecimal.valueOf(3.0);

	@Column(nullable = false, precision = 5, scale = 2)
	private BigDecimal maxWeeklyDrawdownPct = BigDecimal.valueOf(8.0);

	@Column(nullable = false)
	private int autoPickTopN = 5;

	@Column(length = 2048)
	private String manualMarkets;

	@Column
	private Integer emaFast;

	@Column
	private Integer emaSlow;

	@Column
	private Integer rsiPeriod;

	@Column
	private Integer atrPeriod;

	@Column
	private Integer bbPeriod;

	@Column(precision = 6, scale = 3)
	private Double bbStdDev;

	@Column(precision = 6, scale = 4)
	private Double trendThreshold;

	@Column(precision = 6, scale = 4)
	private Double volatilityHigh;

	@Column
	private Integer trendRsiBuyMin;

	@Column
	private Integer trendRsiSellMax;

	@Column
	private Integer rangeRsiBuyMax;

	@Column
	private Integer rangeRsiSellMin;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public Tenant getTenant() {
		return tenant;
	}

	public void setTenant(Tenant tenant) {
		this.tenant = tenant;
	}

	public User getOwner() {
		return owner;
	}

	public void setOwner(User owner) {
		this.owner = owner;
	}

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
