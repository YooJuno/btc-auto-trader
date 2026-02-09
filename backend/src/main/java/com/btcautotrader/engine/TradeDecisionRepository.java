package com.btcautotrader.engine;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeDecisionRepository extends JpaRepository<TradeDecisionEntity, Long> {
}
