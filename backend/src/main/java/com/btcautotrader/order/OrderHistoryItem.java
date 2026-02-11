package com.btcautotrader.order;

import java.math.BigDecimal;

public record OrderHistoryItem(
        Long id,
        String market,
        String side,
        String type,
        String ordType,
        String requestStatus,
        String state,
        BigDecimal price,
        BigDecimal volume,
        BigDecimal funds,
        String requestedAt,
        String createdAt,
        String orderId,
        String clientOrderId,
        String errorMessage
) {
}
