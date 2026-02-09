package com.btcautotrader.strategy;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class StrategyService {
    private static final long CONFIG_ID = 1L;
    private static final StrategyConfig DEFAULT_CONFIG =
            new StrategyConfig(true, 10000.0, 4.0, 2.0, 2.0, 50.0, StrategyProfile.CONSERVATIVE.name(),
                    100.0, 50.0, 50.0);

    private final StrategyConfigRepository repository;
    private final StrategyMarketOverrideRepository marketOverrideRepository;
    private final String forcedProfile;
    private final String marketsConfig;

    public StrategyService(
            StrategyConfigRepository repository,
            StrategyMarketOverrideRepository marketOverrideRepository,
            @Value("${trading.markets:KRW-BTC}") String marketsConfig,
            @Value("${strategy.force-profile:}") String forcedProfile
    ) {
        this.repository = repository;
        this.marketOverrideRepository = marketOverrideRepository;
        this.marketsConfig = marketsConfig;
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

    @Transactional
    public StrategyConfig updateRatios(StrategyRatiosRequest request) {
        StrategyConfigEntity entity = repository.findById(CONFIG_ID)
                .orElseGet(() -> StrategyConfigEntity.from(CONFIG_ID, DEFAULT_CONFIG));

        if (request.takeProfitPct() != null) {
            entity.setTakeProfitPct(request.takeProfitPct());
        }
        if (request.stopLossPct() != null) {
            entity.setStopLossPct(request.stopLossPct());
        }
        if (request.trailingStopPct() != null) {
            entity.setTrailingStopPct(request.trailingStopPct());
        }
        if (request.partialTakeProfitPct() != null) {
            entity.setPartialTakeProfitPct(request.partialTakeProfitPct());
        }
        if (request.stopExitPct() != null) {
            entity.setStopExitPct(request.stopExitPct());
        }
        if (request.trendExitPct() != null) {
            entity.setTrendExitPct(request.trendExitPct());
        }
        if (request.momentumExitPct() != null) {
            entity.setMomentumExitPct(request.momentumExitPct());
        }

        return repository.save(entity).toRecord();
    }

    @Transactional(readOnly = true)
    public List<String> configuredMarkets() {
        return parseMarkets(marketsConfig);
    }

    @Transactional(readOnly = true)
    public StrategyMarketOverrides getMarketOverridesSnapshot() {
        return toMarketOverrides(marketOverrideRepository.findAll());
    }

    @Transactional(readOnly = true)
    public StrategyMarketOverridesResponse getMarketOverrides() {
        StrategyMarketOverrides overrides = getMarketOverridesSnapshot();
        return new StrategyMarketOverridesResponse(
                configuredMarkets(),
                overrides.maxOrderKrwByMarket(),
                overrides.profileByMarket()
        );
    }

    @Transactional
    public StrategyMarketOverridesResponse replaceMarketOverrides(StrategyMarketOverridesRequest request) {
        Map<String, StrategyMarketOverrideEntity> byMarket = new HashMap<>();
        if (request != null && request.maxOrderKrwByMarket() != null) {
            for (Map.Entry<String, Double> entry : request.maxOrderKrwByMarket().entrySet()) {
                String market = normalizeMarket(entry.getKey());
                Double maxOrderKrw = entry.getValue();
                if (market == null || maxOrderKrw == null) {
                    continue;
                }
                StrategyMarketOverrideEntity entity = byMarket.computeIfAbsent(
                        market,
                        key -> new StrategyMarketOverrideEntity(key, null, null)
                );
                entity.setMaxOrderKrw(maxOrderKrw);
            }
        }
        if (request != null && request.profileByMarket() != null) {
            for (Map.Entry<String, String> entry : request.profileByMarket().entrySet()) {
                String market = normalizeMarket(entry.getKey());
                if (market == null || entry.getValue() == null || entry.getValue().isBlank()) {
                    continue;
                }
                StrategyProfile profile = StrategyProfile.from(entry.getValue());
                StrategyMarketOverrideEntity entity = byMarket.computeIfAbsent(
                        market,
                        key -> new StrategyMarketOverrideEntity(key, null, null)
                );
                entity.setProfile(profile.name());
            }
        }

        marketOverrideRepository.deleteAllInBatch();
        if (!byMarket.isEmpty()) {
            marketOverrideRepository.saveAll(byMarket.values());
        }
        return getMarketOverrides();
    }

    private static StrategyMarketOverrides toMarketOverrides(List<StrategyMarketOverrideEntity> entities) {
        Map<String, Double> maxOrderKrwByMarket = new HashMap<>();
        Map<String, String> profileByMarket = new HashMap<>();
        for (StrategyMarketOverrideEntity entity : entities) {
            if (entity == null) {
                continue;
            }
            String market = normalizeMarket(entity.getMarket());
            if (market == null) {
                continue;
            }
            Double maxOrderKrw = entity.getMaxOrderKrw();
            if (maxOrderKrw != null && maxOrderKrw > 0) {
                maxOrderKrwByMarket.put(market, maxOrderKrw);
            }
            String profile = entity.getProfile();
            if (profile != null && !profile.isBlank()) {
                profileByMarket.put(market, StrategyProfile.from(profile).name());
            }
        }
        return new StrategyMarketOverrides(Map.copyOf(maxOrderKrwByMarket), Map.copyOf(profileByMarket));
    }

    private static List<String> parseMarkets(String config) {
        if (config == null || config.isBlank()) {
            return List.of();
        }
        String[] raw = config.split(",");
        Set<String> unique = new LinkedHashSet<>();
        for (String item : raw) {
            String market = normalizeMarket(item);
            if (market != null) {
                unique.add(market);
            }
        }
        return new ArrayList<>(unique);
    }

    private static String normalizeMarket(String market) {
        if (market == null) {
            return null;
        }
        String normalized = market.trim().toUpperCase();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized;
    }
}
