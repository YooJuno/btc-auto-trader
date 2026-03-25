package com.btcautotrader.engine;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

record MarketIndicators(
        BigDecimal currentPrice,
        BigDecimal maShort,
        BigDecimal maLong,
        BigDecimal volatilityPct,
        BigDecimal atrPct,
        BigDecimal rsi,
        BigDecimal macdHistogram,
        BigDecimal adx,
        BigDecimal volumeRatio,
        BigDecimal bollingerMiddle,
        BigDecimal bollingerUpper,
        BigDecimal bollingerLower,
        BigDecimal bollingerBandwidthPct,
        BigDecimal bollingerPercentB,
        BigDecimal breakoutLevel,
        BigDecimal breakdownLevel,
        BigDecimal trailingHigh,
        BigDecimal maLongSlopePct,
        BigDecimal latestHigh,
        OffsetDateTime latestClosedAt
) {
}
