package com.btcautotrader.portfolio;

import com.btcautotrader.engine.TradeDecisionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioPerformanceSnapshotServiceTest {
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Seoul");

    @Mock
    private TradeDecisionRepository tradeDecisionRepository;
    @Mock
    private PortfolioSnapshotRepository portfolioSnapshotRepository;
    @Mock
    private PortfolioSnapshotItemRepository portfolioSnapshotItemRepository;

    @Test
    void saveSnapshot_reusesExistingSeedAndReplacesItems() {
        PortfolioPerformanceSnapshotService service = new PortfolioPerformanceSnapshotService(
                tradeDecisionRepository,
                portfolioSnapshotRepository,
                portfolioSnapshotItemRepository,
                new BigDecimal("0.0005"),
                new BigDecimal("0.001")
        );

        LocalDate day = LocalDate.of(2026, 3, 10);
        OffsetDateTime occurredAt = day.plusDays(1).atStartOfDay(REPORT_ZONE).toOffsetDateTime();

        PortfolioSnapshotEntity latestSnapshot = new PortfolioSnapshotEntity();
        ReflectionTestUtils.setField(latestSnapshot, "id", 44L);
        PortfolioSnapshotEntity olderDuplicate = new PortfolioSnapshotEntity();
        ReflectionTestUtils.setField(olderDuplicate, "id", 43L);

        when(portfolioSnapshotRepository.findByEventTypeAndSourceAndOccurredAtOrderByIdDesc(
                "PERFORMANCE_SEED",
                "SYSTEM",
                occurredAt
        )).thenReturn(List.of(latestSnapshot, olderDuplicate));
        when(portfolioSnapshotRepository.save(latestSnapshot)).thenReturn(latestSnapshot);

        Map<String, PortfolioPerformanceAccounting.PositionState> inventory = new HashMap<>();
        inventory.put(
                "KRW-BTC",
                PortfolioPerformanceAccounting.restorePosition(
                        new BigDecimal("0.125"),
                        new BigDecimal("140000000")
                )
        );

        ReflectionTestUtils.invokeMethod(service, "saveSnapshot", day, inventory);

        verify(portfolioSnapshotRepository).save(latestSnapshot);
        verify(portfolioSnapshotRepository).deleteAll(List.of(olderDuplicate));
        verify(portfolioSnapshotItemRepository).deleteBySnapshotId(44L);
        verify(portfolioSnapshotItemRepository).saveAll(anyList());
    }
}
