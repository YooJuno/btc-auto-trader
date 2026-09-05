package com.btcautotrader.engine;

/**
 * Volatility-contraction breakout.
 *
 * The repair of what the trend-breakout model was trying to be. That model paired a breakout requirement
 * (price above the 20-bar high) with an overextension cap (price near MA_LONG) — contradictory demands
 * that rejected strong breakouts and admitted only weak ones drifting near the mean, which is adverse
 * selection: the entries it took were the ones where the fixed cost was largest relative to the move.
 *
 * This inverts the setup. Instead of asking "is price still cheap relative to the mean", it asks "has
 * volatility contracted enough that a break is worth paying for". Range expansion out of a squeeze is a
 * far better-documented setup than a bare channel break, and the entries it produces have the move size
 * needed to clear a 0.1-0.3% round trip.
 *
 * Requirements, all of which must hold:
 *   - trend gate: MA_SHORT > MA_LONG and price above MA_LONG (unchanged, this part was sound)
 *   - squeeze: Bollinger bandwidth at or below {@code signal.squeeze.max-bandwidth-pct}
 *   - expansion: price breaks the lookback high
 *   - participation: quote-volume ratio above the tuned minimum
 *
 * Note on the squeeze test: {@code bollingerBandwidthPct} is a point-in-time value, so this compares it
 * to a fixed threshold rather than to its own percentile over a trailing window. A percentile would be
 * stricter and adapt per market; it needs bandwidth history that MarketIndicators does not currently
 * carry. The threshold is an honest approximation, not the textbook form.
 */
final class VolatilityContractionBreakoutModel implements TradeSignalModel {
    static final String NAME = "squeeze_breakout";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public BuySignalDecision evaluateBuy(BuySignalContext context) {
        MarketIndicators indicators = context.indicators();
        SignalTuning tuning = context.tuning();
        if (indicators == null || tuning == null || indicators.currentPrice() == null
                || indicators.maShort() == null || indicators.maLong() == null) {
            return BuySignalDecision.skip("insufficient candles");
        }

        if (indicators.maShort().compareTo(indicators.maLong()) <= 0
                || indicators.currentPrice().compareTo(indicators.maLong()) <= 0) {
            return BuySignalDecision.skip("no trend");
        }

        if (tuning.minMaLongSlopePct() > 0) {
            if (indicators.maLongSlopePct() == null) {
                return BuySignalDecision.skip("no trend slope");
            }
            if (indicators.maLongSlopePct().doubleValue() < tuning.minMaLongSlopePct()) {
                return BuySignalDecision.skip("trend weakening");
            }
        }

        double squeezeMax = context.squeezeMaxBandwidthPct();
        if (squeezeMax > 0) {
            if (indicators.bollingerBandwidthPct() == null) {
                return BuySignalDecision.skip("no bandwidth");
            }
            if (indicators.bollingerBandwidthPct().doubleValue() > squeezeMax) {
                return BuySignalDecision.skip("no_squeeze");
            }
        }

        if (tuning.minAdx() > 0 && indicators.adx() != null
                && indicators.adx().doubleValue() < tuning.minAdx()) {
            return BuySignalDecision.skip("weak_trend");
        }

        // A break with no participation is the classic false breakout, so this is a hard requirement
        // rather than one of several optional confirmations.
        if (tuning.minVolumeRatio() > 0) {
            if (indicators.volumeRatio() == null) {
                return BuySignalDecision.skip("no volume");
            }
            if (indicators.volumeRatio().doubleValue() < tuning.minVolumeRatio()) {
                return BuySignalDecision.skip("low_volume");
            }
        }

        if (indicators.breakoutLevel() == null) {
            return BuySignalDecision.skip("no breakout");
        }
        if (indicators.currentPrice().compareTo(indicators.breakoutLevel()) <= 0) {
            return BuySignalDecision.skip("no breakout");
        }

        // Deliberately no overextension cap. Requiring price to stay near MA_LONG while also requiring it
        // to break the lookback high is the contradiction this model exists to remove.
        if (indicators.rsi() != null && tuning.rsiOverbought() > 0
                && indicators.rsi().doubleValue() >= tuning.rsiOverbought()) {
            return BuySignalDecision.skip("overbought");
        }

        return BuySignalDecision.proceed("squeeze_breakout");
    }
}
