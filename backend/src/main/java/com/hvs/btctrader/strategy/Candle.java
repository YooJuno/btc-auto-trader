package com.hvs.btctrader.strategy;

import java.time.Instant;

public record Candle(
		Instant timestamp,
		double open,
		double high,
		double low,
		double close,
		double volume
) {
}
