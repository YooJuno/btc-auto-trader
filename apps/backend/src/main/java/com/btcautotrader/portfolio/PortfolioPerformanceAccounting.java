package com.btcautotrader.portfolio;

import com.btcautotrader.engine.TradeDecisionEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

final class PortfolioPerformanceAccounting {
    static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal EPSILON_QUANTITY = new BigDecimal("0.000000000001");
    private static final int CALC_SCALE = 18;

    private PortfolioPerformanceAccounting() {
    }

    static EventMetrics applyDecision(
            TradeDecisionEntity decision,
            Map<String, PositionState> inventoryByMarket,
            BigDecimal tradeCostRate
    ) {
        if (decision == null || decision.getExecutedAt() == null) {
            return null;
        }
        if (isFailedDecision(decision)) {
            return null;
        }
        String action = normalize(decision.getAction());
        String market = normalize(decision.getMarket());
        if (action == null || market == null) {
            return null;
        }
        if (!"BUY".equals(action) && !"SELL".equals(action)) {
            return null;
        }

        PositionState inventory = inventoryByMarket.computeIfAbsent(market, key -> new PositionState());
        BigDecimal price = positiveOrNull(decision.getPrice());
        BigDecimal quantity = positiveOrNull(decision.getQuantity());
        BigDecimal funds = positiveOrNull(decision.getFunds());

        if ("BUY".equals(action)) {
            if (quantity == null && funds != null && price != null) {
                quantity = safeDivide(funds, price);
            }
            if (funds == null && quantity != null && price != null) {
                funds = quantity.multiply(price);
            }
            if (quantity == null || funds == null) {
                return null;
            }

            BigDecimal fee = computeFee(funds, tradeCostRate);
            BigDecimal buyCost = funds.add(fee);
            inventory.addBuy(quantity, buyCost);
            return EventMetrics.buy(funds, fee);
        }

        if (quantity == null && funds != null && price != null) {
            quantity = safeDivide(funds, price);
        }
        if (quantity == null || price == null) {
            return null;
        }
        if (funds == null) {
            funds = quantity.multiply(price);
        }
        BigDecimal fee = computeFee(funds, tradeCostRate);
        SellAccounting sellAccounting = inventory.applySell(quantity, funds, fee);
        return EventMetrics.sell(
                funds,
                sellAccounting.realizedPnl(),
                sellAccounting.unmatchedNotional(),
                sellAccounting.matchedQuantity(),
                fee
        );
    }

    static PositionState restorePosition(BigDecimal quantity, BigDecimal averageBuyPrice) {
        PositionState restored = new PositionState();
        BigDecimal safeQuantity = positiveOrNull(quantity);
        BigDecimal safeAverageBuyPrice = positiveOrNull(averageBuyPrice);
        if (safeQuantity == null) {
            return restored;
        }
        restored.quantity = safeQuantity;
        if (safeAverageBuyPrice != null) {
            restored.cost = safeQuantity.multiply(safeAverageBuyPrice);
        }
        return restored;
    }

    private static boolean isFailedDecision(TradeDecisionEntity decision) {
        String status = decision.getRequestStatus();
        if (status == null || status.isBlank()) {
            return false;
        }
        return "FAILED".equalsIgnoreCase(status.trim());
    }

    private static BigDecimal computeFee(BigDecimal notional, BigDecimal tradeCostRate) {
        if (notional == null || tradeCostRate == null) {
            return ZERO;
        }
        if (notional.compareTo(ZERO) <= 0 || tradeCostRate.compareTo(ZERO) <= 0) {
            return ZERO;
        }
        return notional.multiply(tradeCostRate);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase();
    }

    private static BigDecimal positiveOrNull(BigDecimal value) {
        if (value == null || value.compareTo(ZERO) <= 0) {
            return null;
        }
        return value;
    }

    private static BigDecimal safeDivide(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(ZERO) <= 0) {
            return null;
        }
        return numerator.divide(denominator, CALC_SCALE, RoundingMode.HALF_UP);
    }

    static final class PositionState {
        private BigDecimal quantity = ZERO;
        private BigDecimal cost = ZERO;

        BigDecimal quantity() {
            return quantity;
        }

        BigDecimal averageBuyPrice() {
            if (quantity.compareTo(ZERO) <= 0) {
                return ZERO;
            }
            BigDecimal average = safeDivide(cost, quantity);
            return average == null ? ZERO : average;
        }

        private void addBuy(BigDecimal buyQuantity, BigDecimal buyFunds) {
            if (buyQuantity == null || buyFunds == null) {
                return;
            }
            if (buyQuantity.compareTo(ZERO) <= 0 || buyFunds.compareTo(ZERO) <= 0) {
                return;
            }
            quantity = quantity.add(buyQuantity);
            cost = cost.add(buyFunds);
        }

        private SellAccounting applySell(
                BigDecimal sellQuantity,
                BigDecimal sellNotional,
                BigDecimal sellFee
        ) {
            if (sellQuantity == null || sellNotional == null) {
                return SellAccounting.empty();
            }
            if (sellQuantity.compareTo(ZERO) <= 0 || sellNotional.compareTo(ZERO) <= 0) {
                return SellAccounting.empty();
            }

            BigDecimal matchedQuantity = sellQuantity.min(quantity.max(ZERO));
            if (matchedQuantity.compareTo(ZERO) <= 0) {
                return new SellAccounting(ZERO, sellNotional, ZERO);
            }

            BigDecimal matchedRatio = safeDivide(matchedQuantity, sellQuantity);
            if (matchedRatio == null) {
                matchedRatio = ZERO;
            }

            BigDecimal matchedNotional = sellNotional.multiply(matchedRatio);
            BigDecimal unmatchedNotional = sellNotional.subtract(matchedNotional);
            BigDecimal matchedFee = sellFee == null ? ZERO : sellFee.multiply(matchedRatio);

            BigDecimal averageCost = safeDivide(cost, quantity);
            if (averageCost == null) {
                averageCost = ZERO;
            }

            BigDecimal costBasis = averageCost.multiply(matchedQuantity);
            BigDecimal realizedPnl = matchedNotional.subtract(costBasis).subtract(matchedFee);

            quantity = quantity.subtract(matchedQuantity);
            cost = cost.subtract(costBasis);
            if (quantity.compareTo(EPSILON_QUANTITY) <= 0) {
                quantity = ZERO;
                cost = ZERO;
            } else if (cost.compareTo(ZERO) < 0) {
                cost = ZERO;
            }

            return new SellAccounting(matchedQuantity, unmatchedNotional.max(ZERO), realizedPnl);
        }
    }

    private record SellAccounting(
            BigDecimal matchedQuantity,
            BigDecimal unmatchedNotional,
            BigDecimal realizedPnl
    ) {
        static SellAccounting empty() {
            return new SellAccounting(ZERO, ZERO, ZERO);
        }
    }

    record EventMetrics(
            BigDecimal buyNotional,
            BigDecimal sellNotional,
            BigDecimal realizedPnl,
            BigDecimal unmatchedSellNotional,
            BigDecimal feeKrw,
            BigDecimal cashFlowDeltaKrw,
            boolean buy,
            boolean sell,
            boolean matchedSell,
            boolean winningSell,
            boolean losingSell
    ) {
        static EventMetrics buy(BigDecimal notional, BigDecimal fee) {
            BigDecimal safeNotional = notional == null ? ZERO : notional;
            BigDecimal safeFee = fee == null ? ZERO : fee;
            BigDecimal cashFlow = safeNotional.add(safeFee).negate();
            return new EventMetrics(
                    safeNotional,
                    ZERO,
                    ZERO,
                    ZERO,
                    safeFee,
                    cashFlow,
                    true,
                    false,
                    false,
                    false,
                    false
            );
        }

        static EventMetrics sell(
                BigDecimal notional,
                BigDecimal realized,
                BigDecimal unmatched,
                BigDecimal matchedQty,
                BigDecimal fee
        ) {
            BigDecimal safeNotional = notional == null ? ZERO : notional;
            BigDecimal safeRealized = realized == null ? ZERO : realized;
            BigDecimal safeUnmatched = unmatched == null ? ZERO : unmatched;
            BigDecimal safeMatchedQty = matchedQty == null ? ZERO : matchedQty;
            BigDecimal safeFee = fee == null ? ZERO : fee;
            BigDecimal cashFlow = safeNotional.subtract(safeFee);
            boolean matchedSell = safeMatchedQty.compareTo(ZERO) > 0;
            boolean winningSell = matchedSell && safeRealized.compareTo(ZERO) > 0;
            boolean losingSell = matchedSell && safeRealized.compareTo(ZERO) < 0;
            return new EventMetrics(
                    ZERO,
                    safeNotional,
                    safeRealized,
                    safeUnmatched,
                    safeFee,
                    cashFlow,
                    false,
                    true,
                    matchedSell,
                    winningSell,
                    losingSell
            );
        }
    }
}
