package com.btcautotrader.portfolio;

import java.util.List;

public record PortfolioPerformanceResponse(
        String timezone,
        boolean estimated,
        String note,
        String from,
        String to,
        PortfolioPerformanceMetrics total,
        List<PortfolioPerformanceMetrics> yearly,
        List<PortfolioPerformanceMetrics> monthly
) {
}
