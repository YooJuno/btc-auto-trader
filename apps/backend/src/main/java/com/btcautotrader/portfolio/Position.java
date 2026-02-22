package com.btcautotrader.portfolio;

import java.math.BigDecimal;

public record Position(
        String market,
        String currency,
        String unitCurrency,
        BigDecimal quantity,
        BigDecimal avgBuyPrice,
        BigDecimal currentPrice,
        BigDecimal valuation,
        BigDecimal cost,
        BigDecimal pnl,
        BigDecimal pnlRate
) {
}
