package com.btcautotrader.engine;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface TradeDecisionRepository extends JpaRepository<TradeDecisionEntity, Long> {
    Page<TradeDecisionEntity> findByActionIn(Collection<String> actions, Pageable pageable);

    List<TradeDecisionEntity> findByActionInAndExecutedAtBeforeOrderByExecutedAtAsc(
            Collection<String> actions,
            OffsetDateTime executedAt
    );
}
