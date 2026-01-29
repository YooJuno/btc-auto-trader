package com.hvs.btctrader.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
	private final Jwt jwt = new Jwt();
	private final Crypto crypto = new Crypto();

	public Jwt getJwt() {
		return jwt;
	}

	public Crypto getCrypto() {
		return crypto;
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
}
