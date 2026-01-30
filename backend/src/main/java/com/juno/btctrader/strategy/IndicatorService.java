package com.juno.btctrader.strategy;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class IndicatorService {
	public double ema(List<Double> values, int period) {
		if (values == null || values.size() < period || period <= 0) {
			return Double.NaN;
		}
		double sum = 0.0;
		for (int i = 0; i < period; i++) {
			sum += values.get(i);
		}
		double ema = sum / period;
		double multiplier = 2.0 / (period + 1);
		for (int i = period; i < values.size(); i++) {
			double price = values.get(i);
			ema = (price - ema) * multiplier + ema;
		}
		return ema;
	}

	public double rsi(List<Double> values, int period) {
		if (values == null || values.size() < period + 1 || period <= 0) {
			return Double.NaN;
		}
		double gain = 0.0;
		double loss = 0.0;
		for (int i = 1; i <= period; i++) {
			double change = values.get(i) - values.get(i - 1);
			if (change > 0) {
				gain += change;
			} else {
				loss -= change;
			}
		}
		double avgGain = gain / period;
		double avgLoss = loss / period;

		for (int i = period + 1; i < values.size(); i++) {
			double change = values.get(i) - values.get(i - 1);
			double g = change > 0 ? change : 0.0;
			double l = change < 0 ? -change : 0.0;
			avgGain = (avgGain * (period - 1) + g) / period;
			avgLoss = (avgLoss * (period - 1) + l) / period;
		}

		if (avgLoss == 0.0) {
			return avgGain == 0.0 ? 50.0 : 100.0;
		}
		double rs = avgGain / avgLoss;
		return 100.0 - (100.0 / (1.0 + rs));
	}

	public double atr(List<Candle> candles, int period) {
		if (candles == null || candles.size() < period + 1 || period <= 0) {
			return Double.NaN;
		}
		double sum = 0.0;
		for (int i = 1; i <= period; i++) {
			sum += trueRange(candles.get(i), candles.get(i - 1).close());
		}
		double atr = sum / period;
		for (int i = period + 1; i < candles.size(); i++) {
			double tr = trueRange(candles.get(i), candles.get(i - 1).close());
			atr = (atr * (period - 1) + tr) / period;
		}
		return atr;
	}

	public BollingerBands bollinger(List<Double> values, int period, double stdDevMultiplier) {
		if (values == null || values.size() < period || period <= 0) {
			return new BollingerBands(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
		}
		int start = values.size() - period;
		double sum = 0.0;
		for (int i = start; i < values.size(); i++) {
			sum += values.get(i);
		}
		double mean = sum / period;
		double varianceSum = 0.0;
		for (int i = start; i < values.size(); i++) {
			double diff = values.get(i) - mean;
			varianceSum += diff * diff;
		}
		double variance = varianceSum / period;
		double stdDev = Math.sqrt(variance);
		double upper = mean + (stdDevMultiplier * stdDev);
		double lower = mean - (stdDevMultiplier * stdDev);
		return new BollingerBands(mean, upper, lower, stdDev);
	}

	private double trueRange(Candle candle, double previousClose) {
		double highLow = candle.high() - candle.low();
		double highClose = Math.abs(candle.high() - previousClose);
		double lowClose = Math.abs(candle.low() - previousClose);
		return Math.max(highLow, Math.max(highClose, lowClose));
	}

	public record BollingerBands(double middle, double upper, double lower, double stdDev) {
	}
}
