package com.hvs.btctrader.market;

import java.time.Instant;

public record MarketCandle(
		Instant timestamp,
		double close
) {
}
