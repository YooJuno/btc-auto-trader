package com.juno.btctrader.market;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.juno.btctrader.config.AppProperties;

@Service
public class MarketStreamService {
	private final UpbitMarketDataService marketDataService;
	private final AppProperties properties;
	private final List<StreamClient> clients = new CopyOnWriteArrayList<>();

	public MarketStreamService(UpbitMarketDataService marketDataService, AppProperties properties) {
		this.marketDataService = marketDataService;
		this.properties = properties;
	}

	public SseEmitter subscribe(int topN) {
		int safeTopN = clampTopN(topN);
		SseEmitter emitter = new SseEmitter(0L);
		StreamClient client = new StreamClient(emitter, safeTopN);
		clients.add(client);

		emitter.onCompletion(() -> clients.remove(client));
		emitter.onTimeout(() -> clients.remove(client));
		emitter.onError(ex -> clients.remove(client));

		send(client);
		return emitter;
	}

	@Scheduled(fixedDelayString = "${app.upbit.streamIntervalMs:5000}")
	public void publish() {
		if (clients.isEmpty()) {
			return;
		}
		for (StreamClient client : clients) {
			if (!send(client)) {
				clients.remove(client);
			}
		}
	}

	private boolean send(StreamClient client) {
		try {
			List<MarketRecommendation> recommendations = marketDataService.recommendTopCached(
					client.topN(),
					properties.getUpbit().getRecommendationCacheMs()
			);
			MarketStreamEvent event = new MarketStreamEvent(Instant.now(), recommendations);
			client.emitter().send(SseEmitter.event().name("recommendations").data(event));
			return true;
		} catch (IOException | IllegalStateException ex) {
			return false;
		}
	}

	private int clampTopN(int topN) {
		int safe = topN;
		if (safe < 1) {
			safe = 1;
		}
		if (safe > 20) {
			safe = 20;
		}
		return safe;
	}

	private record StreamClient(SseEmitter emitter, int topN) {
	}
}
