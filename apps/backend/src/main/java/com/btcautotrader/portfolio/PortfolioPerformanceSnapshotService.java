package com.btcautotrader.portfolio;

import com.btcautotrader.engine.TradeDecisionEntity;
import com.btcautotrader.engine.TradeDecisionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
class PortfolioPerformanceSnapshotService {
    private static final String SNAPSHOT_EVENT_TYPE = "PERFORMANCE_SEED";
    private static final String SNAPSHOT_SOURCE = "SYSTEM";
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Seoul");
    private static final List<String> TRADE_ACTIONS = List.of("BUY", "SELL");

    private final TradeDecisionRepository tradeDecisionRepository;
    private final PortfolioSnapshotRepository portfolioSnapshotRepository;
    private final PortfolioSnapshotItemRepository portfolioSnapshotItemRepository;
    private final BigDecimal tradeCostRate;

    PortfolioPerformanceSnapshotService(
            TradeDecisionRepository tradeDecisionRepository,
            PortfolioSnapshotRepository portfolioSnapshotRepository,
            PortfolioSnapshotItemRepository portfolioSnapshotItemRepository,
            @Value("${trading.fee-rate:0.0005}") BigDecimal feeRate,
            @Value("${trading.slippage-pct:0.001}") BigDecimal slippagePct
    ) {
        this.tradeDecisionRepository = tradeDecisionRepository;
        this.portfolioSnapshotRepository = portfolioSnapshotRepository;
        this.portfolioSnapshotItemRepository = portfolioSnapshotItemRepository;
        BigDecimal safeFeeRate = normalizeRate(feeRate);
        BigDecimal safeSlippagePct = normalizeRate(slippagePct);
        BigDecimal combined = safeFeeRate.add(safeSlippagePct);
        this.tradeCostRate = combined.compareTo(PortfolioPerformanceAccounting.ZERO) < 0
                ? PortfolioPerformanceAccounting.ZERO
                : combined;
    }

    @Transactional
    Map<String, PortfolioPerformanceAccounting.PositionState> loadInventoryAt(LocalDate fromDate) {
        if (fromDate == null) {
            return new HashMap<>();
        }

        OffsetDateTime anchorOccurredAt = fromDate.atStartOfDay(REPORT_ZONE).toOffsetDateTime();
        PortfolioSnapshotEntity latestSnapshot = findLatestSeedSnapshot(anchorOccurredAt);

        Map<String, PortfolioPerformanceAccounting.PositionState> inventory = latestSnapshot == null
                ? new HashMap<>()
                : loadSnapshotInventory(latestSnapshot.getId());

        OffsetDateTime replayFrom = latestSnapshot == null ? null : latestSnapshot.getOccurredAt();
        List<TradeDecisionEntity> decisions = replayFrom == null
                ? tradeDecisionRepository.findByActionInAndExecutedAtBeforeOrderByExecutedAtAsc(TRADE_ACTIONS, anchorOccurredAt)
                : tradeDecisionRepository.findByActionInAndExecutedAtGreaterThanEqualAndExecutedAtLessThanOrderByExecutedAtAsc(
                        TRADE_ACTIONS,
                        replayFrom,
                        anchorOccurredAt
                );

        if (decisions.isEmpty()) {
            return inventory;
        }

        materializeSnapshots(decisions, inventory);
        PortfolioSnapshotEntity resolvedSnapshot = findLatestSeedSnapshot(anchorOccurredAt);
        if (resolvedSnapshot == null) {
            return inventory;
        }
        return loadSnapshotInventory(resolvedSnapshot.getId());
    }

    private void materializeSnapshots(
            List<TradeDecisionEntity> decisions,
            Map<String, PortfolioPerformanceAccounting.PositionState> inventory
    ) {
        LocalDate currentDay = null;
        for (TradeDecisionEntity decision : decisions) {
            if (decision == null || decision.getExecutedAt() == null) {
                continue;
            }
            LocalDate eventDate = decision.getExecutedAt().atZoneSameInstant(REPORT_ZONE).toLocalDate();
            if (currentDay == null) {
                currentDay = eventDate;
            } else if (!eventDate.equals(currentDay)) {
                saveSnapshot(currentDay, inventory);
                currentDay = eventDate;
            }
            PortfolioPerformanceAccounting.applyDecision(decision, inventory, tradeCostRate);
        }
        if (currentDay != null) {
            saveSnapshot(currentDay, inventory);
        }
    }

    private void saveSnapshot(
            LocalDate day,
            Map<String, PortfolioPerformanceAccounting.PositionState> inventory
    ) {
        OffsetDateTime occurredAt = day.plusDays(1).atStartOfDay(REPORT_ZONE).toOffsetDateTime();
        List<PortfolioSnapshotEntity> existingSnapshots = portfolioSnapshotRepository
                .findByEventTypeAndSourceAndOccurredAtOrderByIdDesc(
                        SNAPSHOT_EVENT_TYPE,
                        SNAPSHOT_SOURCE,
                        occurredAt
                );

        PortfolioSnapshotEntity snapshot = existingSnapshots.isEmpty()
                ? new PortfolioSnapshotEntity()
                : existingSnapshots.get(0);
        snapshot.setEventType(SNAPSHOT_EVENT_TYPE);
        snapshot.setSource(SNAPSHOT_SOURCE);
        snapshot.setOccurredAt(occurredAt);
        snapshot.setNote("portfolio performance inventory seed");
        PortfolioSnapshotEntity savedSnapshot = portfolioSnapshotRepository.save(snapshot);

        if (existingSnapshots.size() > 1) {
            portfolioSnapshotRepository.deleteAll(existingSnapshots.subList(1, existingSnapshots.size()));
        }
        portfolioSnapshotItemRepository.deleteBySnapshotId(savedSnapshot.getId());

        List<PortfolioSnapshotItemEntity> items = inventory.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .filter(entry -> entry.getValue().quantity().compareTo(PortfolioPerformanceAccounting.ZERO) > 0)
                .map(entry -> toSnapshotItem(savedSnapshot.getId(), entry.getKey(), entry.getValue()))
                .toList();
        if (!items.isEmpty()) {
            portfolioSnapshotItemRepository.saveAll(items);
        }
    }

    private PortfolioSnapshotEntity findLatestSeedSnapshot(OffsetDateTime occurredAt) {
        return portfolioSnapshotRepository
                .findTopByEventTypeAndSourceAndOccurredAtLessThanEqualOrderByOccurredAtDescIdDesc(
                        SNAPSHOT_EVENT_TYPE,
                        SNAPSHOT_SOURCE,
                        occurredAt
                )
                .orElse(null);
    }

    private Map<String, PortfolioPerformanceAccounting.PositionState> loadSnapshotInventory(Long snapshotId) {
        if (snapshotId == null) {
            return new HashMap<>();
        }
        Map<String, PortfolioPerformanceAccounting.PositionState> inventory = new HashMap<>();
        for (PortfolioSnapshotItemEntity item : portfolioSnapshotItemRepository.findBySnapshotIdOrderByCurrencyAsc(snapshotId)) {
            String currency = normalize(item.getCurrency());
            String unitCurrency = normalize(item.getUnitCurrency());
            if (currency == null || unitCurrency == null) {
                continue;
            }
            String market = unitCurrency + "-" + currency;
            inventory.put(
                    market,
                    PortfolioPerformanceAccounting.restorePosition(item.getBalance(), item.getAvgBuyPrice())
            );
        }
        return inventory;
    }

    private static PortfolioSnapshotItemEntity toSnapshotItem(
            Long snapshotId,
            String market,
            PortfolioPerformanceAccounting.PositionState position
    ) {
        MarketParts marketParts = MarketParts.from(market);
        PortfolioSnapshotItemEntity item = new PortfolioSnapshotItemEntity();
        item.setSnapshotId(snapshotId);
        item.setCurrency(marketParts.currency());
        item.setUnitCurrency(marketParts.unitCurrency());
        item.setBalance(position.quantity());
        item.setLocked(PortfolioPerformanceAccounting.ZERO);
        item.setAvgBuyPrice(position.averageBuyPrice());
        return item;
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

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private record MarketParts(String unitCurrency, String currency) {
        static MarketParts from(String market) {
            String normalized = normalize(market);
            if (normalized == null) {
                return new MarketParts("KRW", "UNKNOWN");
            }
            int separator = normalized.indexOf('-');
            if (separator < 0 || separator == normalized.length() - 1) {
                return new MarketParts("KRW", normalized);
            }
            return new MarketParts(normalized.substring(0, separator), normalized.substring(separator + 1));
        }
    }
}
