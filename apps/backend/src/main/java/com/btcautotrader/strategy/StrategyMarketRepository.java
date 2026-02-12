package com.btcautotrader.strategy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StrategyMarketRepository extends JpaRepository<StrategyMarketEntity, String> {
    List<StrategyMarketEntity> findAllByOrderByMarketAsc();
}
