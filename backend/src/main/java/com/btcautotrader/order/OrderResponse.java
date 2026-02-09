package com.btcautotrader.order;

import java.math.BigDecimal;

public record OrderResponse(
        String orderId,
        String status,
        String requestStatus,
        String errorMessage,
        String receivedAt,
        String market,
        String side,
        String type,
        BigDecimal price,
        BigDecimal volume,
        BigDecimal funds,
        String clientOrderId
) {
}
