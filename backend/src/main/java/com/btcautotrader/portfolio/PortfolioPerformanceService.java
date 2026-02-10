package com.btcautotrader.portfolio;

import com.btcautotrader.engine.TradeDecisionEntity;
import com.btcautotrader.engine.TradeDecisionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PortfolioPerformanceService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal EPSILON_QUANTITY = new BigDecimal("0.000000000001");
    private static final int CALC_SCALE = 18;
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Seoul");
    private static final List<String> TRADE_ACTIONS = List.of("BUY", "SELL");

    private final TradeDecisionRepository tradeDecisionRepository;
    private final BigDecimal feeRate;
    private final BigDecimal slippagePct;
    private final BigDecimal tradeCostRate;

    public PortfolioPerformanceService(
            TradeDecisionRepository tradeDecisionRepository,
            @Value("${trading.fee-rate:0.0005}") BigDecimal feeRate,
            @Value("${trading.slippage-pct:0.001}") BigDecimal slippagePct
    ) {
        this.tradeDecisionRepository = tradeDecisionRepository;
        this.feeRate = normalizeRate(feeRate);
        this.slippagePct = normalizeRate(slippagePct);
        BigDecimal combined = this.feeRate.add(this.slippagePct);
        this.tradeCostRate = combined.compareTo(ZERO) < 0 ? ZERO : combined;
    }

    @Transactional(readOnly = true)
    public PortfolioPerformanceResponse getPerformance(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException("from/to date are required");
        }
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("from date must be before or equal to to date");
        }

        OffsetDateTime fromAt = fromDate.atStartOfDay(REPORT_ZONE).toOffsetDateTime();
        OffsetDateTime toExclusive = toDate.plusDays(1).atStartOfDay(REPORT_ZONE).toOffsetDateTime();

        List<TradeDecisionEntity> decisions = tradeDecisionRepository
                .findByActionInAndExecutedAtBeforeOrderByExecutedAtAsc(TRADE_ACTIONS, toExclusive);

        MetricsAccumulator total = new MetricsAccumulator();
        Map<Integer, MetricsAccumulator> yearly = new LinkedHashMap<>();
        Map<YearMonth, MetricsAccumulator> monthly = new LinkedHashMap<>();
        Map<String, PositionState> inventoryByMarket = new HashMap<>();

        for (TradeDecisionEntity decision : decisions) {
            if (decision == null || decision.getExecutedAt() == null) {
                continue;
            }

            EventMetrics event = toEvent(decision, inventoryByMarket, tradeCostRate);
            if (event == null) {
                continue;
            }

            OffsetDateTime executedAt = decision.getExecutedAt();
            if (executedAt.isBefore(fromAt) || !executedAt.isBefore(toExclusive)) {
                continue;
            }

            LocalDate eventDate = executedAt.atZoneSameInstant(REPORT_ZONE).toLocalDate();
            total.add(eventDate, event);
            yearly.computeIfAbsent(eventDate.getYear(), key -> new MetricsAccumulator()).add(eventDate, event);
            monthly.computeIfAbsent(YearMonth.from(eventDate), key -> new MetricsAccumulator()).add(eventDate, event);
        }

        List<PortfolioPerformanceMetrics> yearlyMetrics = yearly.entrySet().stream()
                .map(entry -> entry.getValue().toMetrics(String.valueOf(entry.getKey())))
                .toList();
        List<PortfolioPerformanceMetrics> monthlyMetrics = monthly.entrySet().stream()
                .map(entry -> entry.getValue().toMetrics(entry.getKey().toString()))
                .toList();

        return new PortfolioPerformanceResponse(
                REPORT_ZONE.getId(),
                true,
                "자동매매 BUY/SELL 의사결정 로그 기반 추정치이며 수수료/슬리피지 추정치를 포함합니다.",
                fromDate.toString(),
                toDate.toString(),
                total.toMetrics("TOTAL"),
                yearlyMetrics,
                monthlyMetrics
        );
    }

    private static EventMetrics toEvent(
            TradeDecisionEntity decision,
            Map<String, PositionState> inventoryByMarket,
            BigDecimal tradeCostRate
    ) {
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

    private static BigDecimal normalizeRate(BigDecimal rate) {
        if (rate == null) {
            return ZERO;
        }
        if (rate.compareTo(ZERO) < 0) {
            return ZERO;
        }
        if (rate.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE;
        }
        return rate;
    }

    private static final class PositionState {
        private BigDecimal quantity = ZERO;
        private BigDecimal cost = ZERO;

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

    private record EventMetrics(
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

    private static final class MetricsAccumulator {
        private BigDecimal estimatedRealizedPnlKrw = ZERO;
        private BigDecimal netCashFlowKrw = ZERO;
        private BigDecimal buyNotionalKrw = ZERO;
        private BigDecimal sellNotionalKrw = ZERO;
        private BigDecimal unmatchedSellNotionalKrw = ZERO;
        private BigDecimal estimatedFeeKrw = ZERO;
        private long buyCount = 0L;
        private long sellCount = 0L;
        private long matchedSellCount = 0L;
        private long winningSellCount = 0L;
        private long losingSellCount = 0L;
        private LocalDate firstDate;
        private LocalDate lastDate;

        private void add(LocalDate date, EventMetrics event) {
            if (date != null) {
                if (firstDate == null || date.isBefore(firstDate)) {
                    firstDate = date;
                }
                if (lastDate == null || date.isAfter(lastDate)) {
                    lastDate = date;
                }
            }

            if (event.buy()) {
                buyCount++;
                buyNotionalKrw = buyNotionalKrw.add(event.buyNotional());
                estimatedFeeKrw = estimatedFeeKrw.add(event.feeKrw());
                netCashFlowKrw = netCashFlowKrw.add(event.cashFlowDeltaKrw());
            }
            if (event.sell()) {
                sellCount++;
                sellNotionalKrw = sellNotionalKrw.add(event.sellNotional());
                unmatchedSellNotionalKrw = unmatchedSellNotionalKrw.add(event.unmatchedSellNotional());
                estimatedFeeKrw = estimatedFeeKrw.add(event.feeKrw());
                netCashFlowKrw = netCashFlowKrw.add(event.cashFlowDeltaKrw());
                estimatedRealizedPnlKrw = estimatedRealizedPnlKrw.add(event.realizedPnl());
                if (event.matchedSell()) {
                    matchedSellCount++;
                    if (event.winningSell()) {
                        winningSellCount++;
                    }
                    if (event.losingSell()) {
                        losingSellCount++;
                    }
                }
            }
        }

        private PortfolioPerformanceMetrics toMetrics(String period) {
            BigDecimal winRate = null;
            if (matchedSellCount > 0) {
                winRate = BigDecimal.valueOf(winningSellCount)
                        .divide(BigDecimal.valueOf(matchedSellCount), 8, RoundingMode.HALF_UP);
            }
            return new PortfolioPerformanceMetrics(
                    period,
                    firstDate == null ? null : firstDate.toString(),
                    lastDate == null ? null : lastDate.toString(),
                    estimatedRealizedPnlKrw,
                    netCashFlowKrw,
                    buyNotionalKrw,
                    sellNotionalKrw,
                    unmatchedSellNotionalKrw,
                    estimatedFeeKrw,
                    buyCount,
                    sellCount,
                    buyCount + sellCount,
                    matchedSellCount,
                    winningSellCount,
                    losingSellCount,
                    winRate
            );
        }
    }
}
