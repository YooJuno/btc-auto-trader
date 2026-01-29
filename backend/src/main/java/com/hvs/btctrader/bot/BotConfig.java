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
}
