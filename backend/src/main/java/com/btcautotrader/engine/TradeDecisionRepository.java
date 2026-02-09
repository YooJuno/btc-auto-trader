package com.btcautotrader.engine;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface TradeDecisionRepository extends JpaRepository<TradeDecisionEntity, Long> {
    Page<TradeDecisionEntity> findByActionIn(Collection<String> actions, Pageable pageable);
}
