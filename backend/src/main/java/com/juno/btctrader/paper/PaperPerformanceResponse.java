package com.juno.btctrader.paper;

import java.util.List;

public record PaperPerformanceResponse(
		double totalReturnPct,
		double maxDrawdownPct,
		List<PaperPerformancePoint> daily,
		List<PaperPerformancePoint> weekly
) {
}
