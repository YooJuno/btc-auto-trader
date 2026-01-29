package com.hvs.btctrader.market;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.hvs.btctrader.config.AppProperties;
import com.hvs.btctrader.strategy.AutoSelector;
import com.hvs.btctrader.strategy.Candle;
import com.hvs.btctrader.strategy.CoinScore;
import com.hvs.btctrader.strategy.IndicatorService;
import com.hvs.btctrader.strategy.MarketSnapshot;
import com.hvs.btctrader.upbit.UpbitCandle;
import com.hvs.btctrader.upbit.UpbitClient;
import com.hvs.btctrader.upbit.UpbitMarket;
import com.hvs.btctrader.upbit.UpbitTicker;

@Service
public class UpbitMarketDataService {
	private final UpbitClient upbitClient;
	private final IndicatorService indicatorService;
	private final AutoSelector autoSelector;
	private final AppProperties properties;

	public UpbitMarketDataService(UpbitClient upbitClient, IndicatorService indicatorService,
			AutoSelector autoSelector, AppProperties properties) {
		this.upbitClient = upbitClient;
		this.indicatorService = indicatorService;
		this.autoSelector = autoSelector;
		this.properties = properties;
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

	public List<MarketSnapshot> buildSnapshots(int topN) {
		List<UpbitMarket> markets = upbitClient.getMarkets(true).stream()
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
		int candidateCount = Math.max(topN * 4, topN);
		List<UpbitTicker> topByVolume = tickers.stream()
				.sorted(Comparator.comparingDouble(UpbitTicker::accTradePrice24h).reversed())
				.limit(candidateCount)
				.toList();

		List<MarketSnapshot> snapshots = new ArrayList<>();
		for (UpbitTicker ticker : topByVolume) {
			List<Candle> candles = fetchCandles(ticker.market());
			if (candles.size() < 20) {
				continue;
			}
			List<Double> closes = candles.stream().map(Candle::close).toList();
			double lastPrice = ticker.tradePrice();
			double atr = indicatorService.atr(candles, 14);
			double emaFast = indicatorService.ema(closes, 12);
			double emaSlow = indicatorService.ema(closes, 26);
			double volatilityPct = Double.isNaN(atr) ? 0.0 : (atr / lastPrice) * 100.0;
			double trendStrengthPct = Double.isNaN(emaFast) || Double.isNaN(emaSlow)
					? 0.0
					: ((emaFast - emaSlow) / lastPrice) * 100.0;

			MarketSnapshot snapshot = new MarketSnapshot(
					ticker.market(),
					lastPrice,
					ticker.accTradePrice24h(),
					0.0,
					volatilityPct,
					trendStrengthPct
			);
			snapshots.add(snapshot);
		}

		return snapshots;
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
}
