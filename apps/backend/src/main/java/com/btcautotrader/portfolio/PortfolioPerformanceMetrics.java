package com.btcautotrader.portfolio;

import java.math.BigDecimal;

public record PortfolioPerformanceMetrics(
        String period,
        String from,
        String to,
        BigDecimal estimatedRealizedPnlKrw,
        BigDecimal netCashFlowKrw,
        BigDecimal buyNotionalKrw,
        BigDecimal sellNotionalKrw,
        BigDecimal unmatchedSellNotionalKrw,
        BigDecimal estimatedFeeKrw,
        long buyCount,
        long sellCount,
        long tradeCount,
        long matchedSellCount,
        long winningSellCount,
        long losingSellCount,
        BigDecimal sellWinRate
) {
}
