package com.btcautotrader.strategy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StrategyPresetRepository extends JpaRepository<StrategyPresetEntity, String> {
    List<StrategyPresetEntity> findAllByOrderByCodeAsc();
}
