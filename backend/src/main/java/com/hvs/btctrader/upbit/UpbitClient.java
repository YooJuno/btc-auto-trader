package com.hvs.btctrader.upbit;

import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.hvs.btctrader.config.AppProperties;

@Service
public class UpbitClient {
	private static final Logger log = LoggerFactory.getLogger(UpbitClient.class);
	private final RestClient restClient;
	private final AppProperties properties;
	private final Object rateLock = new Object();
	private long nextAllowedAtMs = 0L;

	public UpbitClient(AppProperties properties) {
		this.properties = properties;
		this.restClient = RestClient.builder()
				.baseUrl(properties.getUpbit().getBaseUrl())
				.build();
	}

	public List<UpbitMarket> getMarkets(boolean withDetails) {
		List<UpbitMarket> markets = execute("markets", () -> restClient.get()
				.uri(uriBuilder -> uriBuilder.path("/v1/market/all")
						.queryParam("isDetails", withDetails)
						.build())
				.retrieve()
				.body(new ParameterizedTypeReference<>() {}));
		return markets == null ? List.of() : markets;
	}

	public List<UpbitTicker> getTickers(List<String> markets) {
		List<UpbitTicker> tickers = execute("tickers", () -> restClient.get()
				.uri(uriBuilder -> uriBuilder.path("/v1/ticker")
						.queryParam("markets", String.join(",", markets))
						.build())
				.retrieve()
				.body(new ParameterizedTypeReference<>() {}));
		return tickers == null ? List.of() : tickers;
	}

	public List<UpbitCandle> getMinuteCandles(String market, int unit, int count) {
		List<UpbitCandle> candles = execute("candles", () -> restClient.get()
				.uri(uriBuilder -> uriBuilder.path("/v1/candles/minutes/{unit}")
						.queryParam("market", market)
						.queryParam("count", count)
						.build(unit))
				.retrieve()
				.body(new ParameterizedTypeReference<>() {}));
		return candles == null ? List.of() : candles;
	}

	private <T> T execute(String label, Supplier<T> action) {
		int maxRetry = Math.max(0, properties.getUpbit().getRestRetryMax());
		long backoffMs = Math.max(0, properties.getUpbit().getRestRetryBackoffMs());
		for (int attempt = 0; attempt <= maxRetry; attempt++) {
			throttle();
			try {
				return action.get();
			} catch (HttpClientErrorException.TooManyRequests ex) {
				if (attempt >= maxRetry) {
					log.warn("Upbit REST rate limited for {} after {} attempts", label, attempt + 1);
					return null;
				}
				sleep(backoffMs * (attempt + 1L));
			}
		}
		return null;
	}

	private void throttle() {
		long minInterval = Math.max(0, properties.getUpbit().getRestMinIntervalMs());
		if (minInterval == 0) {
			return;
		}
		synchronized (rateLock) {
			long now = System.currentTimeMillis();
			long wait = nextAllowedAtMs - now;
			if (wait > 0) {
				sleep(wait);
			}
			nextAllowedAtMs = System.currentTimeMillis() + minInterval;
		}
	}

	private void sleep(long millis) {
		if (millis <= 0) {
			return;
		}
		try {
			Thread.sleep(millis);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}
}
