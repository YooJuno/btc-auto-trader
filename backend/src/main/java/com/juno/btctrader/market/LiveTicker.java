package com.juno.btctrader.market;

import java.time.Instant;

public record LiveTicker(
		String market,
		double tradePrice,
		double highPrice,
		double lowPrice,
		double accTradePrice24h,
		double accTradeVolume24h,
		Instant timestamp
) {
}
