package com.hvs.btctrader.market;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
