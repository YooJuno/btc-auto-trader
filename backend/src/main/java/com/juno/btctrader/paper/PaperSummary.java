package com.juno.btctrader.paper;

import java.util.List;

public record PaperSummary(
		double cashBalance,
		double equity,
		double realizedPnl,
		double unrealizedPnl,
		List<PaperPositionView> positions
) {
}
