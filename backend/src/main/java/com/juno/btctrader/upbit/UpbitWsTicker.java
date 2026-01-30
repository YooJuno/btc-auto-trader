package com.juno.btctrader.upbit;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpbitWsTicker(
		String market,
		@JsonProperty("trade_price") double tradePrice,
		@JsonProperty("high_price") double highPrice,
		@JsonProperty("low_price") double lowPrice,
		@JsonProperty("acc_trade_price_24h") double accTradePrice24h,
		@JsonProperty("acc_trade_volume_24h") double accTradeVolume24h,
		@JsonProperty("timestamp") long timestamp
) {
}
