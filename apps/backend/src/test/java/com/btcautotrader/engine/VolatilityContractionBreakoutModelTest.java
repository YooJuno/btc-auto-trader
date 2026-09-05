package com.btcautotrader.engine;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class VolatilityContractionBreakoutModelTest {
    private static final double SQUEEZE_MAX = 2.5;

    private final VolatilityContractionBreakoutModel model = new VolatilityContractionBreakoutModel();

    @Test
    void entersOnABreakOutOfAContractedRange() {
        BuySignalDecision decision = model.evaluateBuy(context(
                indicators("101", "100.5", "99", "1.2", "56", "22", "1.6", "100.5")
        ));

        assertThat(decision.proceed()).isTrue();
        assertThat(decision.reason()).isEqualTo("squeeze_breakout");
    }

    @Test
    void skipsWhenVolatilityHasNotContracted() {
        // Same break, but the band is wide: the move is already underway and the edge is spent.
        BuySignalDecision decision = model.evaluateBuy(context(
                indicators("101", "100.5", "99", "6.0", "56", "22", "1.6", "100.5")
        ));

        assertThat(decision.proceed()).isFalse();
        assertThat(decision.reason()).isEqualTo("no_squeeze");
    }

    @Test
    void skipsABreakWithoutParticipation() {
        BuySignalDecision decision = model.evaluateBuy(context(
                indicators("101", "100.5", "99", "1.2", "56", "22", "0.3", "100.5")
        ));

        assertThat(decision.proceed()).isFalse();
        assertThat(decision.reason()).isEqualTo("low_volume");
    }

    @Test
    void skipsWhenPriceHasNotBrokenTheLevel() {
        BuySignalDecision decision = model.evaluateBuy(context(
                indicators("100", "100.5", "99", "1.2", "56", "22", "1.6", "101")
        ));

        assertThat(decision.proceed()).isFalse();
        assertThat(decision.reason()).isEqualTo("no breakout");
    }

    @Test
    void takesBreakoutsThatRunFarFromTheMovingAverage() {
        // The defining difference from trend_breakout: that model rejected exactly this entry via its
        // overextension cap, leaving only weak breaks near the mean. Price far above MA_LONG on a
        // contracted band is the setup, not a disqualification.
        BuySignalDecision decision = model.evaluateBuy(context(
                indicators("140", "120", "99", "1.2", "60", "30", "2.0", "130")
        ));

        assertThat(decision.proceed()).isTrue();
        assertThat(decision.reason()).isEqualTo("squeeze_breakout");
    }

    @Test
    void skipsWhenOverbought() {
        BuySignalDecision decision = model.evaluateBuy(context(
                indicators("101", "100.5", "99", "1.2", "85", "22", "1.6", "100.5")
        ));

        assertThat(decision.proceed()).isFalse();
        assertThat(decision.reason()).isEqualTo("overbought");
    }

    @Test
    void isRegisteredUnderItsOwnName() {
        assertThat(model.name()).isEqualTo("squeeze_breakout");
        assertThat(new UnifiedTrendSignalModel().name()).isEqualTo("trend_breakout");
    }

    private static BuySignalContext context(MarketIndicators indicators) {
        return new BuySignalContext(indicators, tuning(), SQUEEZE_MAX);
    }

    private static SignalTuning tuning() {
        return new SignalTuning(55.0, 45.0, 70.0, 18.0, 0.9, 0.5, 2, 1.0, 0.0);
    }

    private static MarketIndicators indicators(
            String currentPrice,
            String maShort,
            String maLong,
            String bollingerBandwidthPct,
            String rsi,
            String adx,
            String volumeRatio,
            String breakoutLevel
    ) {
        return new MarketIndicators(
                decimal(currentPrice),
                decimal(maShort),
                decimal(maLong),
                null,
                decimal("1.0"),
                decimal(rsi),
                decimal("1.0"),
                decimal(adx),
                decimal(volumeRatio),
                null,
                null,
                null,
                decimal(bollingerBandwidthPct),
                null,
                decimal(breakoutLevel),
                null,
                null,
                decimal("0.2"),
                null,
                null
        );
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
