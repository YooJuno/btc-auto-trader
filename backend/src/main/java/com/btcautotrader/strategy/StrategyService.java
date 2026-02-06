package com.btcautotrader.strategy;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StrategyService {
    private static final long CONFIG_ID = 1L;
    private static final StrategyConfig DEFAULT_CONFIG = new StrategyConfig(true, 10000.0, 3.0, 1.5);

    private final StrategyConfigRepository repository;

    public StrategyService(StrategyConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public StrategyConfig getConfig() {
        return repository.findById(CONFIG_ID)
                .map(StrategyConfigEntity::toRecord)
                .orElseGet(() -> {
                    StrategyConfigEntity created = StrategyConfigEntity.from(CONFIG_ID, DEFAULT_CONFIG);
                    return repository.save(created).toRecord();
                });
    }

    @Transactional
    public StrategyConfig updateConfig(StrategyConfig config) {
        StrategyConfigEntity entity = repository.findById(CONFIG_ID)
                .orElseGet(() -> StrategyConfigEntity.from(CONFIG_ID, DEFAULT_CONFIG));
        entity.apply(config);
        return repository.save(entity).toRecord();
    }
}
