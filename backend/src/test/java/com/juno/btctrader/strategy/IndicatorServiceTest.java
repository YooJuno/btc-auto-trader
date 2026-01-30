package com.juno.btctrader.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class IndicatorServiceTest {
	private final IndicatorService indicatorService = new IndicatorService();

	@Test
	void rsiReturns50WhenFlat() {
		List<Double> values = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			values.add(100.0);
		}
		double rsi = indicatorService.rsi(values, 14);
		assertEquals(50.0, rsi, 0.0001);
	}

	@Test
	void emaStaysWithinRange() {
		List<Double> values = new ArrayList<>();
		for (int i = 1; i <= 20; i++) {
			values.add((double) i);
		}
		double ema = indicatorService.ema(values, 10);
		assertTrue(ema > 1.0);
		assertTrue(ema < 20.0);
	}

	@Test
	void atrMatchesTrueRangeForStableCloses() {
		List<Candle> candles = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			candles.add(new Candle(Instant.now().plusSeconds(i * 60L), 100.0, 101.0, 99.0, 100.0, 10.0));
		}
		double atr = indicatorService.atr(candles, 14);
		assertEquals(2.0, atr, 0.0001);
	}
}
