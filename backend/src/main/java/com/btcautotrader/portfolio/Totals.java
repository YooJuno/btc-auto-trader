package com.btcautotrader.portfolio;

import java.math.BigDecimal;

public record Totals(
        BigDecimal cash,
        BigDecimal positionValue,
        BigDecimal positionCost,
        BigDecimal positionPnl,
        BigDecimal positionPnlRate,
        BigDecimal totalAsset
) {
}
