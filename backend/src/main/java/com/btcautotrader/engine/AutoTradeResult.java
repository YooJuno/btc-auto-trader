package com.btcautotrader.engine;

import java.util.List;

public record AutoTradeResult(
        String executedAt,
        List<AutoTradeAction> actions
) {
}
