package com.hvs.btctrader.market;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class UpbitTickerCache {
	private final Map<String, LiveTicker> cache = new ConcurrentHashMap<>();

	public void update(LiveTicker ticker) {
		cache.put(ticker.market(), ticker);
	}

	public Optional<LiveTicker> getFresh(String market, long maxAgeSeconds) {
		LiveTicker ticker = cache.get(market);
		if (ticker == null) {
			return Optional.empty();
		}
		if (maxAgeSeconds <= 0) {
			return Optional.of(ticker);
		}
		long age = Duration.between(ticker.timestamp(), Instant.now()).getSeconds();
		return age <= maxAgeSeconds ? Optional.of(ticker) : Optional.empty();
	}

	public Map<String, LiveTicker> snapshot() {
		return Map.copyOf(cache);
	}
}
