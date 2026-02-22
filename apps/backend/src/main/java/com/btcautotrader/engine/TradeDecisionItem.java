package com.btcautotrader.engine;

import java.math.BigDecimal;
import java.util.Map;

public record TradeDecisionItem(
        Long id,
        String market,
        String action,
        String reason,
        String executedAt,
        String profile,
        BigDecimal price,
        BigDecimal quantity,
        BigDecimal funds,
        String orderId,
        String requestStatus,
        BigDecimal maShort,
        BigDecimal maLong,
        BigDecimal rsi,
        BigDecimal macdHistogram,
        BigDecimal breakoutLevel,
        BigDecimal trailingHigh,
        BigDecimal maLongSlopePct,
        BigDecimal volatilityPct,
        Map<String, Object> details
) {
}
