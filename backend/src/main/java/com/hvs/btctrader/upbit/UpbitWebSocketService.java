package com.hvs.btctrader.upbit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hvs.btctrader.config.AppProperties;
import com.hvs.btctrader.market.LiveTicker;
import com.hvs.btctrader.market.UpbitTickerCache;
import com.hvs.btctrader.market.UpbitMarketDataService;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class UpbitWebSocketService implements Listener {
	private static final Logger log = LoggerFactory.getLogger(UpbitWebSocketService.class);

	private final AppProperties properties;
	private final UpbitMarketDataService marketDataService;
	private final UpbitTickerCache tickerCache;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient = HttpClient.newHttpClient();

	private final AtomicReference<WebSocket> socketRef = new AtomicReference<>();
	private final AtomicReference<List<String>> marketsRef = new AtomicReference<>(List.of());

	public UpbitWebSocketService(AppProperties properties,
			UpbitMarketDataService marketDataService,
			UpbitTickerCache tickerCache,
			ObjectMapper objectMapper) {
		this.properties = properties;
		this.marketDataService = marketDataService;
		this.tickerCache = tickerCache;
		this.objectMapper = objectMapper;
	}

	@PostConstruct
	public void init() {
		if (properties.getUpbit().isWsEnabled()) {
			connectOrRefresh();
		}
	}

	@PreDestroy
	public void shutdown() {
		WebSocket socket = socketRef.getAndSet(null);
		if (socket != null) {
			socket.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
		}
	}

	@Scheduled(fixedDelayString = "${app.upbit.wsRefreshMs:300000}")
	public void refreshSubscription() {
		if (!properties.getUpbit().isWsEnabled()) {
			return;
		}
		connectOrRefresh();
	}

	private synchronized void connectOrRefresh() {
		int topN = properties.getUpbit().getWsTopN();
		List<String> markets = marketDataService.topMarketsByVolume(topN);
		if (markets.isEmpty()) {
			return;
		}
		List<String> current = marketsRef.get();
		if (!current.isEmpty() && current.equals(markets)) {
			return;
		}
		disconnect();
		marketsRef.set(markets);
		connect(markets);
	}

	private void connect(List<String> markets) {
		log.info("Connecting Upbit WS for {} markets", markets.size());
		CompletableFuture<WebSocket> future = httpClient.newWebSocketBuilder()
				.buildAsync(URI.create(properties.getUpbit().getWsUrl()), this);
		future.thenAccept(socket -> {
			socketRef.set(socket);
			sendSubscription(socket, markets);
		}).exceptionally(ex -> {
			log.warn("Failed to connect Upbit WS", ex);
			return null;
		});
	}

	private void disconnect() {
		WebSocket socket = socketRef.getAndSet(null);
		if (socket != null) {
			try {
				socket.sendClose(WebSocket.NORMAL_CLOSURE, "refresh");
			} catch (Exception ex) {
				log.debug("WS close error", ex);
			}
		}
	}

	private void sendSubscription(WebSocket socket, List<String> markets) {
		try {
			String payload = objectMapper.writeValueAsString(List.of(
					new Ticket("btctrader-" + UUID.randomUUID()),
					new Subscribe("ticker", markets)
			));
			socket.sendText(payload, true);
		} catch (Exception ex) {
			log.warn("Failed to send WS subscription", ex);
		}
	}

	@Override
	public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
		if (!last) {
			return Listener.super.onText(webSocket, data, last);
		}
		handleMessage(data.toString());
		return Listener.super.onText(webSocket, data, last);
	}

	@Override
	public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
		if (last) {
			String text = StandardCharsets.UTF_8.decode(data).toString();
			handleMessage(text);
		}
		return Listener.super.onBinary(webSocket, data, last);
	}

	@Override
	public void onError(WebSocket webSocket, Throwable error) {
		log.warn("Upbit WS error", error);
		disconnect();
		connectOrRefresh();
	}

	private void handleMessage(String payload) {
		try {
			UpbitWsTicker ticker = objectMapper.readValue(payload, UpbitWsTicker.class);
			if (ticker.market() == null || ticker.market().isBlank()) {
				return;
			}
			Instant timestamp = ticker.timestamp() > 0 ? Instant.ofEpochMilli(ticker.timestamp()) : Instant.now();
			tickerCache.update(new LiveTicker(
					ticker.market(),
					ticker.tradePrice(),
					ticker.highPrice(),
					ticker.lowPrice(),
					ticker.accTradePrice24h(),
					ticker.accTradeVolume24h(),
					timestamp
			));
		} catch (Exception ex) {
			log.debug("WS parse ignored: {}", ex.getMessage());
		}
	}

	private record Ticket(String ticket) {
	}

	private record Subscribe(String type, List<String> codes) {
		Subscribe {
			Objects.requireNonNull(type);
			Objects.requireNonNull(codes);
		}
	}
}
