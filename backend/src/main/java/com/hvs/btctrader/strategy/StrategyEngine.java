package com.hvs.btctrader.strategy;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.hvs.btctrader.bot.BotConfig;
import com.hvs.btctrader.enums.RiskPreset;
import com.hvs.btctrader.enums.StrategyMode;

@Service
public class StrategyEngine {
	private final IndicatorService indicatorService;

	public StrategyEngine(IndicatorService indicatorService) {
		this.indicatorService = indicatorService;
	}

	public StrategyDecision evaluate(BotConfig config, List<Candle> candles) {
		StrategyParameters params = parametersForConfig(config);
		return evaluate(config.getStrategyMode(), config.getRiskPreset(), config.getMaxPositions(), params, candles);
	}

	public StrategyDecision evaluate(StrategyMode mode, RiskPreset riskPreset, int maxPositions,
			StrategyParameters params, List<Candle> candles) {
		int minBars = Math.max(params.emaSlow(), Math.max(params.bbPeriod(), Math.max(params.rsiPeriod(), params.atrPeriod()))) + 1;
		if (candles == null || candles.size() < minBars) {
			return hold("Not enough candle data", mode, riskPreset, maxPositions);
		}

		List<Double> closes = new ArrayList<>();
		for (Candle candle : candles) {
			closes.add(candle.close());
		}

		double lastClose = closes.get(closes.size() - 1);
		double emaFast = indicatorService.ema(closes, params.emaFast());
		double emaSlow = indicatorService.ema(closes, params.emaSlow());
		double rsi = indicatorService.rsi(closes, params.rsiPeriod());
		double atr = indicatorService.atr(candles, params.atrPeriod());
		IndicatorService.BollingerBands bb = indicatorService.bollinger(closes, params.bbPeriod(), params.bbStdDev());

		if (Double.isNaN(emaFast) || Double.isNaN(emaSlow) || Double.isNaN(rsi) || Double.isNaN(atr)) {
			return hold("Indicators not available", mode, riskPreset, maxPositions);
		}

		double trendStrength = (emaFast - emaSlow) / lastClose;
		double volatility = atr / lastClose;
		MarketRegime regime = classifyRegime(trendStrength, volatility, params);

		String strategy;
		SignalAction action = SignalAction.HOLD;
		double confidence = 0.0;
		List<String> reasons = new ArrayList<>();

		if (regime == MarketRegime.HIGH_VOLATILITY) {
			strategy = "Cooldown";
			reasons.add("Volatility high");
		} else if (regime == MarketRegime.TREND_UP || regime == MarketRegime.TREND_DOWN) {
			strategy = "TrendFollow";
			if (trendStrength > params.trendThreshold() && rsi >= params.trendRsiBuyMin()) {
				action = SignalAction.BUY;
				confidence = trendConfidence(trendStrength, params.trendThreshold(), rsi, true);
				reasons.add("Uptrend with momentum");
			} else if (trendStrength < -params.trendThreshold() && rsi <= params.trendRsiSellMax()) {
				action = SignalAction.SELL;
				confidence = trendConfidence(trendStrength, params.trendThreshold(), rsi, false);
				reasons.add("Downtrend with weak momentum");
			} else {
				reasons.add("Trend not strong enough for entry");
			}
		} else {
			strategy = "MeanReversion";
			if (!Double.isNaN(bb.lower()) && lastClose < bb.lower() && rsi <= params.rangeRsiBuyMax()) {
				action = SignalAction.BUY;
				confidence = rangeConfidence(lastClose, bb.lower(), rsi, true);
				reasons.add("Price below lower band with oversold RSI");
			} else if (!Double.isNaN(bb.upper()) && lastClose > bb.upper() && rsi >= params.rangeRsiSellMin()) {
				action = SignalAction.SELL;
				confidence = rangeConfidence(lastClose, bb.upper(), rsi, false);
				reasons.add("Price above upper band with overbought RSI");
			} else {
				reasons.add("No mean reversion signal");
			}
		}

		if (reasons.isEmpty()) {
			reasons.add("No actionable signal");
		}

		return new StrategyDecision(
				action,
				strategy,
				regime,
				clamp(confidence, 0.0, 1.0),
				riskPerTradePct(riskPreset),
				maxPositions,
				volatility * 100.0,
				trendStrength * 100.0,
				reasons
		);
	}

	private MarketRegime classifyRegime(double trendStrength, double volatility, StrategyParameters params) {
		if (volatility >= params.volatilityHigh()) {
			return MarketRegime.HIGH_VOLATILITY;
		}
		if (Math.abs(trendStrength) >= params.trendThreshold()) {
			return trendStrength > 0 ? MarketRegime.TREND_UP : MarketRegime.TREND_DOWN;
		}
		return MarketRegime.RANGE;
	}

	private StrategyDecision hold(String reason, StrategyMode mode, RiskPreset riskPreset, int maxPositions) {
		return new StrategyDecision(
				SignalAction.HOLD,
				mode == StrategyMode.AUTO ? "Auto" : mode.name(),
				MarketRegime.UNKNOWN,
				0.0,
				riskPerTradePct(riskPreset),
				maxPositions,
				0.0,
				0.0,
				List.of(reason)
		);
	}

	private StrategyParameters parametersForConfig(BotConfig config) {
		StrategyParameters base = StrategyDefaults.forMode(config.getStrategyMode());
		int emaFast = pickInt(config.getEmaFast(), base.emaFast(), 2, 200);
		int emaSlow = pickInt(config.getEmaSlow(), base.emaSlow(), 5, 400);
		if (emaSlow <= emaFast) {
			emaSlow = Math.max(emaFast + 1, base.emaSlow());
		}
		int rsiPeriod = pickInt(config.getRsiPeriod(), base.rsiPeriod(), 5, 60);
		int atrPeriod = pickInt(config.getAtrPeriod(), base.atrPeriod(), 5, 60);
		int bbPeriod = pickInt(config.getBbPeriod(), base.bbPeriod(), 10, 60);
		double bbStdDev = pickDouble(config.getBbStdDev(), base.bbStdDev(), 0.5, 5.0);
		double trendThreshold = pickDouble(config.getTrendThreshold(), base.trendThreshold(), 0.001, 0.05);
		double volatilityHigh = pickDouble(config.getVolatilityHigh(), base.volatilityHigh(), 0.01, 0.2);
		int trendRsiBuyMin = pickInt(config.getTrendRsiBuyMin(), base.trendRsiBuyMin(), 10, 90);
		int trendRsiSellMax = pickInt(config.getTrendRsiSellMax(), base.trendRsiSellMax(), 10, 90);
		if (trendRsiBuyMin <= trendRsiSellMax) {
			trendRsiBuyMin = base.trendRsiBuyMin();
			trendRsiSellMax = base.trendRsiSellMax();
		}
		int rangeRsiBuyMax = pickInt(config.getRangeRsiBuyMax(), base.rangeRsiBuyMax(), 5, 70);
		int rangeRsiSellMin = pickInt(config.getRangeRsiSellMin(), base.rangeRsiSellMin(), 50, 95);
		if (rangeRsiBuyMax >= rangeRsiSellMin) {
			rangeRsiBuyMax = base.rangeRsiBuyMax();
			rangeRsiSellMin = base.rangeRsiSellMin();
		}
		return new StrategyParameters(
				emaFast,
				emaSlow,
				rsiPeriod,
				atrPeriod,
				bbPeriod,
				bbStdDev,
				trendThreshold,
				volatilityHigh,
				trendRsiBuyMin,
				trendRsiSellMax,
				rangeRsiBuyMax,
				rangeRsiSellMin
		);
	}

	private int pickInt(Integer override, int fallback, int min, int max) {
		if (override == null) {
			return fallback;
		}
		if (override < min || override > max) {
			return fallback;
		}
		return override;
	}

	private double pickDouble(Double override, double fallback, double min, double max) {
		if (override == null) {
			return fallback;
		}
		if (override < min || override > max) {
			return fallback;
		}
		return override;
	}

	private double trendConfidence(double trendStrength, double threshold, double rsi, boolean isBuy) {
		double trendScore = Math.min(1.0, Math.abs(trendStrength) / (threshold * 2.0));
		double rsiScore = isBuy ? (rsi - 50.0) / 50.0 : (50.0 - rsi) / 50.0;
		return clamp((trendScore * 0.6) + (clamp(rsiScore, 0.0, 1.0) * 0.4), 0.0, 1.0);
	}

	private double rangeConfidence(double price, double band, double rsi, boolean isBuy) {
		double distance = Math.abs(price - band) / band;
		double distanceScore = Math.min(1.0, distance / 0.02);
		double rsiScore = isBuy ? (50.0 - rsi) / 50.0 : (rsi - 50.0) / 50.0;
		return clamp((distanceScore * 0.5) + (clamp(rsiScore, 0.0, 1.0) * 0.5), 0.0, 1.0);
	}

	private double riskPerTradePct(RiskPreset preset) {
		return switch (preset) {
			case CONSERVATIVE -> 0.3;
			case STANDARD -> 0.7;
			case AGGRESSIVE -> 1.2;
		};
	}

	private double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
