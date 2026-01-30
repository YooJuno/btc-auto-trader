package com.juno.btctrader.market;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import com.juno.btctrader.config.AppProperties;
import com.juno.btctrader.strategy.AutoSelector;
import com.juno.btctrader.strategy.Candle;
import com.juno.btctrader.strategy.CoinScore;
import com.juno.btctrader.strategy.IndicatorService;
import com.juno.btctrader.strategy.MarketSnapshot;
import com.juno.btctrader.upbit.UpbitCandle;
import com.juno.btctrader.upbit.UpbitClient;
import com.juno.btctrader.upbit.UpbitMarket;
import com.juno.btctrader.upbit.UpbitTicker;

@Service
public class UpbitMarketDataService {
	private final UpbitClient upbitClient;
	private final IndicatorService indicatorService;
	private final AutoSelector autoSelector;
	private final AppProperties properties;
	private final UpbitTickerCache tickerCache;
	private volatile List<MarketRecommendation> cachedRecommendations = List.of();
	private volatile long cachedAtMillis = 0L;
	private volatile List<UpbitMarket> cachedMarkets = List.of();
	private volatile long marketsCachedAtMillis = 0L;
	private final Map<String, CandleCache> candleCache = new ConcurrentHashMap<>();

	public UpbitMarketDataService(UpbitClient upbitClient, IndicatorService indicatorService,
			AutoSelector autoSelector, AppProperties properties, UpbitTickerCache tickerCache) {
		this.upbitClient = upbitClient;
		this.indicatorService = indicatorService;
		this.autoSelector = autoSelector;
		this.properties = properties;
		this.tickerCache = tickerCache;
	}

	public List<MarketRecommendation> recommendTop(int topN) {
		List<MarketSnapshot> snapshots = buildSnapshots(topN);
		List<CoinScore> scores = autoSelector.topN(snapshots, topN);
		Map<String, MarketSnapshot> snapshotMap = snapshots.stream()
				.collect(Collectors.toMap(MarketSnapshot::symbol, snapshot -> snapshot));
		List<MarketRecommendation> recommendations = new ArrayList<>();
		for (CoinScore score : scores) {
			MarketSnapshot snapshot = snapshotMap.get(score.symbol());
			if (snapshot != null) {
				recommendations.add(MarketRecommendation.from(score.symbol(), score.score(), snapshot));
			}
		}
		return recommendations;
	}

	public List<MarketRecommendation> recommendTopCached(int topN, long maxAgeMs) {
		long now = System.currentTimeMillis();
		List<MarketRecommendation> cached = cachedRecommendations;
		if (maxAgeMs > 0 && cached.size() >= topN && now - cachedAtMillis <= maxAgeMs) {
			return cached.subList(0, topN);
		}
		synchronized (this) {
			if (maxAgeMs > 0 && cachedRecommendations.size() >= topN
					&& now - cachedAtMillis <= maxAgeMs) {
				return cachedRecommendations.subList(0, topN);
			}
			try {
				List<MarketRecommendation> fresh = recommendTop(topN);
				if (!fresh.isEmpty()) {
					cachedRecommendations = fresh;
					cachedAtMillis = System.currentTimeMillis();
					return fresh;
				}
				return fallbackRecommendations(topN);
			} catch (HttpClientErrorException.TooManyRequests ex) {
				return fallbackRecommendations(topN);
			}
		}
	}

	public List<MarketSnapshot> buildSnapshots(int topN) {
		List<UpbitMarket> markets = getMarketsCached(true).stream()
				.filter(market -> market.market().startsWith(properties.getUpbit().getMarketPrefix()))
				.filter(market -> !"CAUTION".equalsIgnoreCase(market.marketWarning()))
				.toList();

		if (markets.isEmpty()) {
			return List.of();
		}

		List<String> marketIds = markets.stream().map(UpbitMarket::market).toList();
		List<UpbitTicker> tickers = fetchTickers(marketIds);
		if (tickers.isEmpty()) {
			return List.of();
		}
		Map<String, UpbitTicker> tickerByMarket = tickers.stream()
				.collect(Collectors.toMap(UpbitTicker::market, ticker -> ticker, (a, b) -> a));
		int factor = properties.getUpbit().getRecommendationCandidateFactor();
		if (factor <= 0) {
			factor = 1;
		} else if (factor > 5) {
			factor = 5;
		}
		int candidateCount = Math.max(topN * factor, topN);
		List<UpbitTicker> topByVolume = tickers.stream()
				.sorted(Comparator.comparingDouble(UpbitTicker::accTradePrice24h).reversed())
				.limit(candidateCount)
				.toList();

		List<MarketSnapshot> snapshots = new ArrayList<>();
		boolean fastRecommend = properties.getUpbit().isFastRecommend();
		int minCandles = Math.max(10, properties.getUpbit().getRecommendationMinCandles());
		for (UpbitTicker ticker : topByVolume) {
			UpbitTicker baseTicker = tickerByMarket.getOrDefault(ticker.market(), ticker);
			UpbitTicker finalTicker = tickerCache.getFresh(baseTicker.market(), properties.getUpbit().getWsMaxAgeSec())
					.map(live -> new UpbitTicker(
							live.market(),
							live.tradePrice(),
							live.highPrice(),
							live.lowPrice(),
							live.accTradePrice24h(),
							live.accTradeVolume24h()
					))
					.orElse(baseTicker);
			double lastPrice = finalTicker.tradePrice();
			double volatilityPct = 0.0;
			double trendStrengthPct = 0.0;
			if (!fastRecommend) {
				List<Candle> candles = fetchCandles(ticker.market());
				if (candles.size() < minCandles) {
					continue;
				}
				List<Double> closes = candles.stream().map(Candle::close).toList();
				double atr = indicatorService.atr(candles, 14);
				double emaFast = indicatorService.ema(closes, 12);
				double emaSlow = indicatorService.ema(closes, 26);
				volatilityPct = Double.isNaN(atr) ? 0.0 : (atr / lastPrice) * 100.0;
				trendStrengthPct = Double.isNaN(emaFast) || Double.isNaN(emaSlow)
						? 0.0
						: ((emaFast - emaSlow) / lastPrice) * 100.0;
			}

			MarketSnapshot snapshot = new MarketSnapshot(
					finalTicker.market(),
					lastPrice,
					finalTicker.accTradePrice24h(),
					0.0,
					volatilityPct,
					trendStrengthPct
			);
			snapshots.add(snapshot);
		}

		return snapshots;
	}

	public List<String> topMarketsByVolume(int topN) {
		List<UpbitMarket> markets = getMarketsCached(true).stream()
				.filter(market -> market.market().startsWith(properties.getUpbit().getMarketPrefix()))
				.filter(market -> !"CAUTION".equalsIgnoreCase(market.marketWarning()))
				.toList();
		if (markets.isEmpty()) {
			return List.of();
		}
		List<String> marketIds = markets.stream().map(UpbitMarket::market).toList();
		List<UpbitTicker> tickers = fetchTickers(marketIds);
		return tickers.stream()
				.sorted(Comparator.comparingDouble(UpbitTicker::accTradePrice24h).reversed())
				.limit(topN)
				.map(UpbitTicker::market)
				.toList();
	}

	public List<Candle> candlesForMarket(String market) {
		return fetchCandles(market);
	}

	private List<UpbitTicker> fetchTickers(List<String> marketIds) {
		int chunkSize = Math.max(1, properties.getUpbit().getTickerChunkSize());
		List<UpbitTicker> all = new ArrayList<>();
		for (int i = 0; i < marketIds.size(); i += chunkSize) {
			List<String> chunk = marketIds.subList(i, Math.min(i + chunkSize, marketIds.size()));
			all.addAll(upbitClient.getTickers(chunk));
		}
		return all;
	}

	private List<Candle> fetchCandles(String market) {
		long cacheMs = Math.max(0, properties.getUpbit().getCandleCacheMs());
		if (cacheMs > 0) {
			CandleCache cached = candleCache.get(market);
			long now = System.currentTimeMillis();
			if (cached != null && now - cached.cachedAtMillis <= cacheMs) {
				return cached.candles;
			}
		}
		List<UpbitCandle> response = upbitClient.getMinuteCandles(
				market,
				properties.getUpbit().getCandleUnit(),
				properties.getUpbit().getCandleCount()
		);
		List<Candle> candles = new ArrayList<>();
		for (int i = response.size() - 1; i >= 0; i--) {
			UpbitCandle candle = response.get(i);
			candles.add(new Candle(parseUtc(candle.candleDateTimeUtc()),
					candle.openingPrice(),
					candle.highPrice(),
					candle.lowPrice(),
					candle.tradePrice(),
					candle.candleAccTradeVolume()));
		}
		if (!candles.isEmpty()) {
			candleCache.put(market, new CandleCache(candles, System.currentTimeMillis()));
		}
		return candles;
	}

	private Instant parseUtc(String value) {
		try {
			return OffsetDateTime.parse(value).toInstant();
		} catch (Exception ex) {
			LocalDateTime localDateTime = LocalDateTime.parse(value);
			return localDateTime.toInstant(ZoneOffset.UTC);
		}
	}

	private List<UpbitMarket> getMarketsCached(boolean withDetails) {
		long cacheMs = Math.max(0, properties.getUpbit().getMarketCacheMs());
		if (cacheMs <= 0) {
			return upbitClient.getMarkets(withDetails);
		}
		long now = System.currentTimeMillis();
		if (!cachedMarkets.isEmpty() && now - marketsCachedAtMillis <= cacheMs) {
			return cachedMarkets;
		}
		synchronized (this) {
			if (!cachedMarkets.isEmpty() && now - marketsCachedAtMillis <= cacheMs) {
				return cachedMarkets;
			}
			List<UpbitMarket> markets = upbitClient.getMarkets(withDetails);
			if (!markets.isEmpty()) {
				cachedMarkets = markets;
				marketsCachedAtMillis = System.currentTimeMillis();
				return markets;
			}
			return cachedMarkets;
		}
	}

	private List<MarketRecommendation> fallbackRecommendations(int topN) {
		List<MarketRecommendation> cached = cachedRecommendations;
		if (cached.isEmpty()) {
			return List.of();
		}
		int safeTopN = Math.min(topN, cached.size());
		return cached.subList(0, safeTopN);
	}

	private record CandleCache(List<Candle> candles, long cachedAtMillis) {
	}
}
