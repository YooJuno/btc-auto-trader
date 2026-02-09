package com.btcautotrader.strategy;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

@Service
public class StrategyService {
    private static final long CONFIG_ID = 1L;
    private static final StrategyConfig DEFAULT_CONFIG =
            new StrategyConfig(true, 10000.0, 4.0, 2.0, 2.0, 50.0, StrategyProfile.CONSERVATIVE.name(),
                    100.0, 50.0, 50.0);

    private final StrategyConfigRepository repository;
    private final String forcedProfile;

    public StrategyService(
            StrategyConfigRepository repository,
            @Value("${strategy.force-profile:}") String forcedProfile
    ) {
        this.repository = repository;
        this.forcedProfile = forcedProfile == null ? "" : forcedProfile.trim();
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
        if (entity.getStopExitPct() == 0.0
                && entity.getTrendExitPct() == 0.0
                && entity.getMomentumExitPct() == 0.0) {
            entity.setStopExitPct(DEFAULT_CONFIG.stopExitPct());
            entity.setTrendExitPct(DEFAULT_CONFIG.trendExitPct());
            entity.setMomentumExitPct(DEFAULT_CONFIG.momentumExitPct());
            entity = repository.save(entity);
        }
        if (!forcedProfile.isBlank()) {
            StrategyProfile profile = StrategyProfile.from(forcedProfile);
            if (!profile.name().equalsIgnoreCase(entity.getProfile())) {
                entity.setProfile(profile.name());
                entity = repository.save(entity);
            }
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
