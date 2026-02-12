package com.btcautotrader.portfolio;

import java.math.BigDecimal;

public record CashBalance(
        String currency,
        BigDecimal balance,
        BigDecimal locked,
        BigDecimal total
) {
}
