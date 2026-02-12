package com.btcautotrader.portfolio;

import java.util.List;

public record PortfolioSummary(
        String queriedAt,
        CashBalance cash,
        List<Position> positions,
        Totals totals
) {
}
