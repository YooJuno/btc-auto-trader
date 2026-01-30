package com.juno.btctrader.market;

import java.time.Instant;

public record MarketCandle(
		Instant timestamp,
		double open,
		double high,
		double low,
		double close
) {
}
