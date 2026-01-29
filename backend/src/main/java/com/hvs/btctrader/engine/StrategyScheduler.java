package com.hvs.btctrader.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.hvs.btctrader.bot.BotConfig;
import com.hvs.btctrader.bot.BotConfigRepository;
import com.hvs.btctrader.config.AppProperties;
import com.hvs.btctrader.enums.SelectionMode;
import com.hvs.btctrader.market.MarketRecommendation;
import com.hvs.btctrader.market.UpbitMarketDataService;
import com.hvs.btctrader.market.UpbitTickerCache;
import com.hvs.btctrader.paper.PaperTradingService;
import com.hvs.btctrader.strategy.Candle;
import com.hvs.btctrader.strategy.StrategyDecision;
import com.hvs.btctrader.strategy.StrategyEngine;
import com.hvs.btctrader.upbit.UpbitClient;
import com.hvs.btctrader.upbit.UpbitTicker;

@Component
public class StrategyScheduler {
	private final BotConfigRepository botConfigRepository;
	private final UpbitMarketDataService marketDataService;
	private final StrategyEngine strategyEngine;
	private final PaperTradingService paperTradingService;
	private final UpbitClient upbitClient;
	private final AppProperties properties;
	private final UpbitTickerCache tickerCache;

	public StrategyScheduler(BotConfigRepository botConfigRepository,
			UpbitMarketDataService marketDataService,
			StrategyEngine strategyEngine,
			PaperTradingService paperTradingService,
			UpbitClient upbitClient,
			AppProperties properties,
			UpbitTickerCache tickerCache) {
		this.botConfigRepository = botConfigRepository;
		this.marketDataService = marketDataService;
		this.strategyEngine = strategyEngine;
		this.paperTradingService = paperTradingService;
		this.upbitClient = upbitClient;
		this.properties = properties;
		this.tickerCache = tickerCache;
	}

	@Scheduled(fixedDelayString = "${app.engine.intervalMs:60000}")
	public void tick() {
		if (!properties.getEngine().isEnabled()) {
			return;
		}
		Map<UUID, BotConfig> configs = latestConfigs();
		for (BotConfig config : configs.values()) {
			List<String> markets = selectMarkets(config);
			if (markets.isEmpty()) {
				continue;
			}
			Map<String, Double> lastPrices = fetchLastPrices(markets);
			for (String market : markets) {
				Double price = lastPrices.get(market);
				if (price == null || price <= 0.0) {
					continue;
				}
				List<Candle> candles = marketDataService.candlesForMarket(market);
				if (candles.isEmpty()) {
					continue;
				}
				StrategyDecision decision = strategyEngine.evaluate(config, candles);
				paperTradingService.updateLastPrice(config.getOwner().getId().toString(), market, price);
				paperTradingService.applySignal(config, market, price, decision);
			}
			paperTradingService.recordEquity(config.getOwner().getId().toString());
		}
	}

	private Map<UUID, BotConfig> latestConfigs() {
		List<BotConfig> configs = botConfigRepository.findAll();
		Map<UUID, BotConfig> latest = new HashMap<>();
		for (BotConfig config : configs) {
			UUID ownerId = config.getOwner().getId();
			BotConfig existing = latest.get(ownerId);
			if (existing == null || config.getCreatedAt().isAfter(existing.getCreatedAt())) {
				latest.put(ownerId, config);
			}
		}
		return latest;
	}

	private List<String> selectMarkets(BotConfig config) {
		if (config.getSelectionMode() == SelectionMode.MANUAL) {
			return parseManualMarkets(config.getManualMarkets());
		}
		List<MarketRecommendation> recommendations = marketDataService.recommendTop(config.getAutoPickTopN());
		return recommendations.stream().map(MarketRecommendation::market).toList();
	}

	private Map<String, Double> fetchLastPrices(List<String> markets) {
		Map<String, Double> prices = new HashMap<>();
		if (markets.isEmpty()) {
			return prices;
		}
		List<String> missing = new ArrayList<>();
		for (String market : markets) {
			tickerCache.getFresh(market, properties.getUpbit().getWsMaxAgeSec())
					.ifPresentOrElse(
							live -> prices.put(market, live.tradePrice()),
							() -> missing.add(market)
					);
		}
		if (!missing.isEmpty()) {
			List<UpbitTicker> tickers = upbitClient.getTickers(missing);
			for (UpbitTicker ticker : tickers) {
				prices.put(ticker.market(), ticker.tradePrice());
			}
		}
		return prices;
	}

	private List<String> parseManualMarkets(String manualMarkets) {
		if (manualMarkets == null || manualMarkets.isBlank()) {
			return List.of();
		}
		String[] tokens = manualMarkets.split(",");
		List<String> markets = new ArrayList<>();
		for (String token : tokens) {
			String trimmed = token.trim();
			if (!trimmed.isBlank()) {
				if (trimmed.contains("-")) {
					markets.add(trimmed);
				} else {
					markets.add(properties.getUpbit().getMarketPrefix() + trimmed);
				}
			}
		}
		return markets;
	}
}
