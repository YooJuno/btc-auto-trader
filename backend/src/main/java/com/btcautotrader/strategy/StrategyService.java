package com.btcautotrader.strategy;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StrategyService {
    private static final long CONFIG_ID = 1L;
    private static final StrategyConfig DEFAULT_CONFIG =
            new StrategyConfig(true, 10000.0, 3.0, 1.5, 2.0, 50.0, StrategyProfile.BALANCED.name());

    private final StrategyConfigRepository repository;

    public StrategyService(StrategyConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public StrategyConfig getConfig() {
        StrategyConfigEntity entity = repository.findById(CONFIG_ID)
                .orElseGet(() -> repository.save(StrategyConfigEntity.from(CONFIG_ID, DEFAULT_CONFIG)));

        if (entity.getTrailingStopPct() == 0.0 && entity.getPartialTakeProfitPct() == 0.0) {
            entity.setTrailingStopPct(DEFAULT_CONFIG.trailingStopPct());
            entity.setPartialTakeProfitPct(DEFAULT_CONFIG.partialTakeProfitPct());
            entity = repository.save(entity);
        }
        if (entity.getProfile() == null || entity.getProfile().isBlank()) {
            entity.setProfile(DEFAULT_CONFIG.profile());
            entity = repository.save(entity);
        }

        return entity.toRecord();
    }

    @Transactional
    public StrategyConfig updateConfig(StrategyConfig config) {
        StrategyConfigEntity entity = repository.findById(CONFIG_ID)
                .orElseGet(() -> StrategyConfigEntity.from(CONFIG_ID, DEFAULT_CONFIG));
        entity.apply(config);
        return repository.save(entity).toRecord();
    }
}
