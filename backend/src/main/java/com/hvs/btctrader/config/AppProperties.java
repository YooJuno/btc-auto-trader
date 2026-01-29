package com.hvs.btctrader.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
	private final Jwt jwt = new Jwt();
	private final Crypto crypto = new Crypto();
	private final Upbit upbit = new Upbit();
	private final Engine engine = new Engine();
	private final Paper paper = new Paper();

	public Jwt getJwt() {
		return jwt;
	}

	public Crypto getCrypto() {
		return crypto;
	}

	public Upbit getUpbit() {
		return upbit;
	}

	public Engine getEngine() {
		return engine;
	}

	public Paper getPaper() {
		return paper;
	}

	public static class Jwt {
		private String issuer = "btc-auto-trader";
		private String secret = "dev-only-change-me";
		private long ttlMinutes = 120;

		public String getIssuer() {
			return issuer;
		}

		public void setIssuer(String issuer) {
			this.issuer = issuer;
		}

		public String getSecret() {
			return secret;
		}

		public void setSecret(String secret) {
			this.secret = secret;
		}

		public long getTtlMinutes() {
			return ttlMinutes;
		}

		public void setTtlMinutes(long ttlMinutes) {
			this.ttlMinutes = ttlMinutes;
		}
	}

	public static class Crypto {
		private String key = "";

		public String getKey() {
			return key;
		}

		public void setKey(String key) {
			this.key = key;
		}
	}

	public static class Upbit {
		private String baseUrl = "https://api.upbit.com";
		private String wsUrl = "wss://api.upbit.com/websocket/v1";
		private String marketPrefix = "KRW-";
		private int candleUnit = 1;
		private int candleCount = 120;
		private int tickerChunkSize = 100;
		private boolean wsEnabled = false;
		private int wsTopN = 30;
		private long wsRefreshMs = 300000;
		private long wsMaxAgeSec = 30;

		public String getBaseUrl() {
			return baseUrl;
		}

		public void setBaseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
		}

		public String getWsUrl() {
			return wsUrl;
		}

		public void setWsUrl(String wsUrl) {
			this.wsUrl = wsUrl;
		}

		public String getMarketPrefix() {
			return marketPrefix;
		}

		public void setMarketPrefix(String marketPrefix) {
			this.marketPrefix = marketPrefix;
		}

		public int getCandleUnit() {
			return candleUnit;
		}

		public void setCandleUnit(int candleUnit) {
			this.candleUnit = candleUnit;
		}

		public int getCandleCount() {
			return candleCount;
		}

		public void setCandleCount(int candleCount) {
			this.candleCount = candleCount;
		}

		public int getTickerChunkSize() {
			return tickerChunkSize;
		}

		public void setTickerChunkSize(int tickerChunkSize) {
			this.tickerChunkSize = tickerChunkSize;
		}

		public boolean isWsEnabled() {
			return wsEnabled;
		}

		public void setWsEnabled(boolean wsEnabled) {
			this.wsEnabled = wsEnabled;
		}

		public int getWsTopN() {
			return wsTopN;
		}

		public void setWsTopN(int wsTopN) {
			this.wsTopN = wsTopN;
		}

		public long getWsRefreshMs() {
			return wsRefreshMs;
		}

		public void setWsRefreshMs(long wsRefreshMs) {
			this.wsRefreshMs = wsRefreshMs;
		}

		public long getWsMaxAgeSec() {
			return wsMaxAgeSec;
		}

		public void setWsMaxAgeSec(long wsMaxAgeSec) {
			this.wsMaxAgeSec = wsMaxAgeSec;
		}
	}

	public static class Engine {
		private boolean enabled = false;
		private long intervalMs = 60000;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public long getIntervalMs() {
			return intervalMs;
		}

		public void setIntervalMs(long intervalMs) {
			this.intervalMs = intervalMs;
		}
	}

	public static class Paper {
		private double initialCash = 1_000_000.0;

		public double getInitialCash() {
			return initialCash;
		}

		public void setInitialCash(double initialCash) {
			this.initialCash = initialCash;
		}
	}
}
