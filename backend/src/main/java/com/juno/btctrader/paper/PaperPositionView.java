package com.juno.btctrader.paper;

public record PaperPositionView(
		String market,
		double quantity,
		double entryPrice,
		double lastPrice,
		double unrealizedPnl,
		double unrealizedPnlPct
) {
	public static PaperPositionView from(PaperPosition position) {
		return new PaperPositionView(
				position.getMarket(),
				position.getQuantity(),
				position.getEntryPrice(),
				position.getLastPrice(),
				position.getUnrealizedPnl(),
				position.getUnrealizedPnlPct()
		);
	}
}
