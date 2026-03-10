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
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Seoul");
    private static final List<String> TRADE_ACTIONS = List.of("BUY", "SELL");

    private final TradeDecisionRepository tradeDecisionRepository;
    private final PortfolioPerformanceSnapshotService portfolioPerformanceSnapshotService;
    private final BigDecimal feeRate;
    private final BigDecimal slippagePct;
    private final BigDecimal tradeCostRate;

    public PortfolioPerformanceService(
            TradeDecisionRepository tradeDecisionRepository,
            PortfolioPerformanceSnapshotService portfolioPerformanceSnapshotService,
            @Value("${trading.fee-rate:0.0005}") BigDecimal feeRate,
            @Value("${trading.slippage-pct:0.001}") BigDecimal slippagePct
    ) {
        this.tradeDecisionRepository = tradeDecisionRepository;
        this.portfolioPerformanceSnapshotService = portfolioPerformanceSnapshotService;
        this.feeRate = normalizeRate(feeRate);
        this.slippagePct = normalizeRate(slippagePct);
        BigDecimal combined = this.feeRate.add(this.slippagePct);
        this.tradeCostRate = combined.compareTo(PortfolioPerformanceAccounting.ZERO) < 0
                ? PortfolioPerformanceAccounting.ZERO
                : combined;
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

        Map<String, PortfolioPerformanceAccounting.PositionState> inventoryByMarket =
                portfolioPerformanceSnapshotService.loadInventoryAt(fromDate);
        List<TradeDecisionEntity> decisions = tradeDecisionRepository
                .findByActionInAndExecutedAtGreaterThanEqualAndExecutedAtLessThanOrderByExecutedAtAsc(
                        TRADE_ACTIONS,
                        fromAt,
                        toExclusive
                );

        MetricsAccumulator total = new MetricsAccumulator();
        Map<Integer, MetricsAccumulator> yearly = new LinkedHashMap<>();
        Map<YearMonth, MetricsAccumulator> monthly = new LinkedHashMap<>();

        for (TradeDecisionEntity decision : decisions) {
            PortfolioPerformanceAccounting.EventMetrics event =
                    PortfolioPerformanceAccounting.applyDecision(decision, inventoryByMarket, tradeCostRate);
            if (event == null) {
                continue;
            }

            OffsetDateTime executedAt = decision.getExecutedAt();
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

    private static BigDecimal normalizeRate(BigDecimal rate) {
        if (rate == null) {
            return PortfolioPerformanceAccounting.ZERO;
        }
        if (rate.compareTo(PortfolioPerformanceAccounting.ZERO) < 0) {
            return PortfolioPerformanceAccounting.ZERO;
        }
        if (rate.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE;
        }
        return rate;
    }

    private static final class MetricsAccumulator {
        private BigDecimal estimatedRealizedPnlKrw = PortfolioPerformanceAccounting.ZERO;
        private BigDecimal netCashFlowKrw = PortfolioPerformanceAccounting.ZERO;
        private BigDecimal buyNotionalKrw = PortfolioPerformanceAccounting.ZERO;
        private BigDecimal sellNotionalKrw = PortfolioPerformanceAccounting.ZERO;
        private BigDecimal unmatchedSellNotionalKrw = PortfolioPerformanceAccounting.ZERO;
        private BigDecimal estimatedFeeKrw = PortfolioPerformanceAccounting.ZERO;
        private long buyCount = 0L;
        private long sellCount = 0L;
        private long matchedSellCount = 0L;
        private long winningSellCount = 0L;
        private long losingSellCount = 0L;
        private LocalDate firstDate;
        private LocalDate lastDate;

        private void add(LocalDate date, PortfolioPerformanceAccounting.EventMetrics event) {
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
