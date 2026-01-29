package com.hvs.btctrader.market;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hvs.btctrader.strategy.Candle;

@RestController
@RequestMapping("/api/market")
public class MarketDataController {
	private final UpbitMarketDataService marketDataService;

	public MarketDataController(UpbitMarketDataService marketDataService) {
		this.marketDataService = marketDataService;
	}

	@GetMapping("/recommendations")
	public List<MarketRecommendation> recommendations(@RequestParam(defaultValue = "5") int topN) {
		return marketDataService.recommendTop(topN);
	}

	@GetMapping("/candles")
	public List<MarketCandle> candles(@RequestParam String market,
			@RequestParam(defaultValue = "30") int limit) {
		if (market == null || market.isBlank()) {
			return List.of();
		}
		List<Candle> candles = marketDataService.candlesForMarket(market.trim().toUpperCase());
		if (candles.isEmpty()) {
			return List.of();
		}
		int safeLimit = Math.min(Math.max(1, limit), candles.size());
		int fromIndex = Math.max(0, candles.size() - safeLimit);
		return candles.subList(fromIndex, candles.size())
				.stream()
				.map(candle -> new MarketCandle(
						candle.timestamp(),
						candle.open(),
						candle.high(),
						candle.low(),
						candle.close()
				))
				.toList();
	}
}
