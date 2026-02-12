package com.btcautotrader.engine;

import java.math.BigDecimal;

public record AutoTradeAction(
        String market,
        String action,
        String reason,
        BigDecimal price,
        BigDecimal quantity,
        BigDecimal funds,
        String orderId,
        String requestStatus
) {
}
