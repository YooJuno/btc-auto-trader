package com.juno.btctrader.upbit;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpbitCandle(
		@JsonProperty("candle_date_time_utc") String candleDateTimeUtc,
		@JsonProperty("opening_price") double openingPrice,
		@JsonProperty("high_price") double highPrice,
		@JsonProperty("low_price") double lowPrice,
		@JsonProperty("trade_price") double tradePrice,
		@JsonProperty("candle_acc_trade_volume") double candleAccTradeVolume
) {
}
