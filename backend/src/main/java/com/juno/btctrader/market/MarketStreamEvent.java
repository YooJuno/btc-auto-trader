package com.juno.btctrader.market;

import java.time.Instant;
import java.util.List;

public record MarketStreamEvent(
		Instant timestamp,
		List<MarketRecommendation> recommendations
) {
}
