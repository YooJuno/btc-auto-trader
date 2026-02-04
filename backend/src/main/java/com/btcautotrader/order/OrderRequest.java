package com.btcautotrader.order;

public record OrderRequest(
        String market,
        String side,
        String type,
        Double price,
        Double volume,
        Double funds
) {
}
