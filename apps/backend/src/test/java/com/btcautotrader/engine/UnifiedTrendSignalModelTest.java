package com.btcautotrader.engine;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class UnifiedTrendSignalModelTest {
    private final UnifiedTrendSignalModel model = new UnifiedTrendSignalModel();

    @Test
    void evaluateBuy_skipsWhenTrendIsAbsent() {
        BuySignalDecision decision = model.evaluateBuy(new BuySignalContext(
                indicators(
                        "100",
                        "99",
                        "100",
                        null,
                        "56",
                        "1.0",
                        "20",
                        "1.2",
                        "101",
                        "0.1"
                ),
                tuning(),
                0.0,
                0.0
        ));

        assertThat(decision.proceed()).isFalse();
        assertThat(decision.reason()).isEqualTo("no trend");
    }

    @Test
    void evaluateBuy_requiresBreakoutConfirmation() {
        BuySignalDecision decision = model.evaluateBuy(new BuySignalContext(
                indicators(
                        "100.8",
                        "101",
                        "100",
                        null,
                        "56",
                        "1.0",
                        "20",
                        "1.2",
                        "101",
                        "0.1"
                ),
                tuning(),
                0.0,
                0.0
        ));

        assertThat(decision.proceed()).isFalse();
        assertThat(decision.reason()).isEqualTo("no breakout");
    }

    @Test
    void evaluateBuy_rejectsWeakTrendStrength() {
        BuySignalDecision decision = model.evaluateBuy(new BuySignalContext(
                indicators(
                        "100.8",
                        "101",
                        "100",
                        null,
                        "56",
                        "1.0",
                        "10",
                        "1.2",
                        "100.5",
                        "0.1"
                ),
                tuning(),
                0.0,
                0.0
        ));

        assertThat(decision.proceed()).isFalse();
        assertThat(decision.reason()).isEqualTo("weak_trend");
    }

    @Test
    void evaluateBuy_returnsTrendBreakoutReasonWhenUnifiedFiltersPass() {
        BuySignalDecision decision = model.evaluateBuy(new BuySignalContext(
                indicators(
                        "100.9",
                        "101",
                        "100",
                        null,
                        "56",
                        "1.0",
                        "22",
                        "1.3",
                        "100.5",
                        "0.2"
                ),
                tuning(),
                0.0,
                0.0
        ));

        assertThat(decision.proceed()).isTrue();
        assertThat(decision.reason()).isEqualTo("trend_breakout");
    }

    private static SignalTuning tuning() {
        return new SignalTuning(55.0, 45.0, 70.0, 18.0, 0.9, 0.5, 2, 1.0, 0.0);
    }

    private static MarketIndicators indicators(
            String currentPrice,
            String maShort,
            String maLong,
            String volatilityPct,
            String rsi,
            String macdHistogram,
            String adx,
            String volumeRatio,
            String breakoutLevel,
            String maLongSlopePct
    ) {
        return new MarketIndicators(
                decimal(currentPrice),
                decimal(maShort),
                decimal(maLong),
                decimal(volatilityPct),
                decimal("1.0"),
                decimal(rsi),
                decimal(macdHistogram),
                decimal(adx),
                decimal(volumeRatio),
                null,
                null,
                null,
                null,
                null,
                decimal(breakoutLevel),
                null,
                null,
                decimal(maLongSlopePct),
                null,
                null
        );
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
