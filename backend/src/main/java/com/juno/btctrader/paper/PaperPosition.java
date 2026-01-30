package com.juno.btctrader.paper;

public class PaperPosition {
	private final String market;
	private final double quantity;
	private final double entryPrice;
	private double lastPrice;

	public PaperPosition(String market, double quantity, double entryPrice, double lastPrice) {
		this.market = market;
		this.quantity = quantity;
		this.entryPrice = entryPrice;
		this.lastPrice = lastPrice;
	}

	public String getMarket() {
		return market;
	}

	public double getQuantity() {
		return quantity;
	}

	public double getEntryPrice() {
		return entryPrice;
	}

	public double getLastPrice() {
		return lastPrice;
	}

	public void setLastPrice(double lastPrice) {
		this.lastPrice = lastPrice;
	}

	public double getUnrealizedPnl() {
		return (lastPrice - entryPrice) * quantity;
	}

	public double getUnrealizedPnlPct() {
		return entryPrice == 0.0 ? 0.0 : ((lastPrice - entryPrice) / entryPrice) * 100.0;
	}
}
