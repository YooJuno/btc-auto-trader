package com.btcautotrader.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshotEntity, Long> {
    Optional<PortfolioSnapshotEntity> findTopByEventTypeAndSourceAndOccurredAtLessThanEqualOrderByOccurredAtDescIdDesc(
            String eventType,
            String source,
            OffsetDateTime occurredAt
    );

    List<PortfolioSnapshotEntity> findByEventTypeAndSourceAndOccurredAtOrderByIdDesc(
            String eventType,
            String source,
            OffsetDateTime occurredAt
    );
}
