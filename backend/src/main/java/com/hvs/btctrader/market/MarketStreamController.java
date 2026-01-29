package com.hvs.btctrader.market;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/market")
public class MarketStreamController {
	private final MarketStreamService marketStreamService;

	public MarketStreamController(MarketStreamService marketStreamService) {
		this.marketStreamService = marketStreamService;
	}

	@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter stream(@RequestParam(defaultValue = "5") int topN) {
		return marketStreamService.subscribe(topN);
	}
}
