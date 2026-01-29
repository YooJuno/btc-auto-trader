package com.hvs.btctrader.paper;

import java.util.List;

public record PaperPerformanceResponse(
		double totalReturnPct,
		double maxDrawdownPct,
		List<PaperPerformancePoint> daily,
		List<PaperPerformancePoint> weekly
) {
}
