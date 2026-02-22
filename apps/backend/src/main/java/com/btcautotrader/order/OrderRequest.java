package com.btcautotrader.order;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.math.BigDecimal;

public record OrderRequest(
        String market,
        String side,
        String type,
        BigDecimal price,
        BigDecimal volume,
        BigDecimal funds,
        @JsonAlias({"identifier", "client_order_id"}) String clientOrderId
) {
}
