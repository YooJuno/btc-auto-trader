package com.hvs.btctrader.upbit;

import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.hvs.btctrader.config.AppProperties;

@Service
public class UpbitClient {
	private final RestClient restClient;

	public UpbitClient(AppProperties properties) {
		this.restClient = RestClient.builder()
				.baseUrl(properties.getUpbit().getBaseUrl())
				.build();
	}

	public List<UpbitMarket> getMarkets(boolean withDetails) {
		List<UpbitMarket> markets = restClient.get()
				.uri(uriBuilder -> uriBuilder.path("/v1/market/all")
						.queryParam("isDetails", withDetails)
						.build())
				.retrieve()
				.body(new ParameterizedTypeReference<>() {});
		return markets == null ? List.of() : markets;
	}

	public List<UpbitTicker> getTickers(List<String> markets) {
		List<UpbitTicker> tickers = restClient.get()
				.uri(uriBuilder -> uriBuilder.path("/v1/ticker")
						.queryParam("markets", String.join(",", markets))
						.build())
				.retrieve()
				.body(new ParameterizedTypeReference<>() {});
		return tickers == null ? List.of() : tickers;
	}

	public List<UpbitCandle> getMinuteCandles(String market, int unit, int count) {
		List<UpbitCandle> candles = restClient.get()
				.uri(uriBuilder -> uriBuilder.path("/v1/candles/minutes/{unit}")
						.queryParam("market", market)
						.queryParam("count", count)
						.build(unit))
				.retrieve()
				.body(new ParameterizedTypeReference<>() {});
		return candles == null ? List.of() : candles;
	}
}
