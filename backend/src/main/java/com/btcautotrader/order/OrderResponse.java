package com.btcautotrader.order;

public record OrderResponse(
        String orderId,
        String status,
        String receivedAt,
        String market,
        String side,
        String type,
        Double price,
        Double volume,
        Double funds
) {
}
