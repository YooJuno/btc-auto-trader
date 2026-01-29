package com.hvs.btctrader.strategy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

@Service
public class AutoSelector {
	public List<CoinScore> topN(List<MarketSnapshot> snapshots, int topN) {
		if (snapshots == null || snapshots.isEmpty() || topN <= 0) {
			return List.of();
		}

		double minVolume = minValue(snapshots, MarketSnapshot::volume24h);
		double maxVolume = maxValue(snapshots, MarketSnapshot::volume24h);
		double minSpread = minValue(snapshots, MarketSnapshot::spreadPct);
		double maxSpread = maxValue(snapshots, MarketSnapshot::spreadPct);
		double minTrend = minValue(snapshots, snapshot -> Math.abs(snapshot.trendStrengthPct()));
		double maxTrend = maxValue(snapshots, snapshot -> Math.abs(snapshot.trendStrengthPct()));

		List<CoinScore> scores = new ArrayList<>();
		for (MarketSnapshot snapshot : snapshots) {
			double volumeScore = normalize(snapshot.volume24h(), minVolume, maxVolume);
			double spreadScore = 1.0 - normalize(snapshot.spreadPct(), minSpread, maxSpread);
			double trendScore = normalize(Math.abs(snapshot.trendStrengthPct()), minTrend, maxTrend);
			double volatilityScore = volatilityScore(snapshot.volatilityPct());

			double score = (0.4 * volumeScore) + (0.2 * spreadScore) + (0.25 * trendScore) + (0.15 * volatilityScore);
			scores.add(new CoinScore(snapshot.symbol(), round(score)));
		}

		return scores.stream()
				.sorted(Comparator.comparingDouble(CoinScore::score).reversed())
				.limit(topN)
				.collect(Collectors.toList());
	}

	private double normalize(double value, double min, double max) {
		if (Double.isNaN(value) || Double.isNaN(min) || Double.isNaN(max) || max == min) {
			return 0.5;
		}
		return clamp((value - min) / (max - min), 0.0, 1.0);
	}

	private double volatilityScore(double volatilityPct) {
		double target = 3.0;
		double distance = Math.abs(volatilityPct - target);
		double score = 1.0 - (distance / target);
		return clamp(score, 0.0, 1.0);
	}

	private double minValue(List<MarketSnapshot> snapshots, ValueExtractor extractor) {
		OptionalDouble value = snapshots.stream().mapToDouble(extractor::value).min();
		return value.isPresent() ? value.getAsDouble() : 0.0;
	}

	private double maxValue(List<MarketSnapshot> snapshots, ValueExtractor extractor) {
		OptionalDouble value = snapshots.stream().mapToDouble(extractor::value).max();
		return value.isPresent() ? value.getAsDouble() : 1.0;
	}

	private double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private double round(double value) {
		return Math.round(value * 1000.0) / 1000.0;
	}

	private interface ValueExtractor {
		double value(MarketSnapshot snapshot);
	}
}
