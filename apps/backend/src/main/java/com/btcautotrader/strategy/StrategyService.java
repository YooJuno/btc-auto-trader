package com.btcautotrader.strategy;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class StrategyService {
    private static final long CONFIG_ID = 1L;
    private static final StrategyConfig DEFAULT_CONFIG =
            new StrategyConfig(true, 30000.0, 2.4, 1.28, 0.9, 35.0, StrategyProfile.BALANCED.name(),
                    100.0, 40.0, 25.0, "V1", 65.0, 60.0, 0.7, 180);
    private static final List<StrategyPresetItem> DEFAULT_PRESETS = List.of(
            new StrategyPresetItem(
                    "BALANCED",
                    "밸런스",
                    2.4,
                    1.28,
                    0.9,
                    35.0,
                    100.0,
                    40.0,
                    25.0
            ),
            new StrategyPresetItem(
                    "AGGRESSIVE",
                    "공격형",
                    3.4,
                    2.4,
                    2.0,
                    30.0,
                    100.0,
                    25.0,
                    15.0
            ),
            new StrategyPresetItem(
                    "CONSERVATIVE",
                    "안정형",
                    1.6,
                    1.1,
                    0.9,
                    50.0,
                    100.0,
                    60.0,
                    40.0
            )
    );

    private final StrategyConfigRepository repository;
    private final StrategyMarketRepository marketRepository;
    private final StrategyMarketOverrideRepository marketOverrideRepository;
    private final StrategyPresetRepository presetRepository;
    private final String marketsConfig;

    public StrategyService(
            StrategyConfigRepository repository,
            StrategyMarketRepository marketRepository,
            StrategyMarketOverrideRepository marketOverrideRepository,
            StrategyPresetRepository presetRepository,
            @Value("${trading.markets:KRW-BTC}") String marketsConfig
    ) {
        this.repository = repository;
        this.marketRepository = marketRepository;
        this.marketOverrideRepository = marketOverrideRepository;
        this.presetRepository = presetRepository;
        this.marketsConfig = marketsConfig;
    }

    @Transactional
    public StrategyConfig getConfig() {
        Optional<StrategyConfigEntity> found = repository.findById(CONFIG_ID);
        StrategyConfigEntity entity = found.orElseGet(() -> StrategyConfigEntity.from(CONFIG_ID, DEFAULT_CONFIG));
        boolean dirty = found.isEmpty();

        if (entity.getTrailingStopPct() == 0.0 && entity.getPartialTakeProfitPct() == 0.0) {
            entity.setTrailingStopPct(DEFAULT_CONFIG.trailingStopPct());
            entity.setPartialTakeProfitPct(DEFAULT_CONFIG.partialTakeProfitPct());
            dirty = true;
        }
        if (entity.getProfile() == null || entity.getProfile().isBlank()) {
            entity.setProfile(DEFAULT_CONFIG.profile());
            dirty = true;
        }
        if (isLegacyConservativeDefaults(entity)
                || isLegacyBalancedDefaults(entity)
                || isBaselineBalancedDefaults(entity)) {
            entity.apply(DEFAULT_CONFIG);
            dirty = true;
        }
        if (entity.getStopExitPct() == 0.0
                && entity.getTrendExitPct() == 0.0
                && entity.getMomentumExitPct() == 0.0) {
            entity.setStopExitPct(DEFAULT_CONFIG.stopExitPct());
            entity.setTrendExitPct(DEFAULT_CONFIG.trendExitPct());
            entity.setMomentumExitPct(DEFAULT_CONFIG.momentumExitPct());
            dirty = true;
        }
        if (entity.getSignalModel() == null || entity.getSignalModel().isBlank()) {
            entity.setSignalModel(DEFAULT_CONFIG.signalModel());
            dirty = true;
        }
        if (entity.getEntryScoreThreshold() <= 0.0) {
            entity.setEntryScoreThreshold(DEFAULT_CONFIG.entryScoreThreshold());
            dirty = true;
        }
        if (entity.getExitScoreThreshold() <= 0.0) {
            entity.setExitScoreThreshold(DEFAULT_CONFIG.exitScoreThreshold());
            dirty = true;
        }
        if (entity.getRiskPerTradePct() <= 0.0) {
            entity.setRiskPerTradePct(DEFAULT_CONFIG.riskPerTradePct());
            dirty = true;
        }
        if (entity.getTimeStopCandles() <= 0) {
            entity.setTimeStopCandles(DEFAULT_CONFIG.timeStopCandles());
            dirty = true;
        }
        if (dirty) {
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

    @Transactional
    public List<StrategyPresetItem> getPresets() {
        ensureDefaultPresets();
        return presetRepository.findAllByOrderByCodeAsc()
                .stream()
                .map(StrategyPresetEntity::toItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> configuredMarkets() {
        List<String> configured = loadPersistedMarkets();
        if (!configured.isEmpty()) {
            return configured;
        }
        return parseMarkets(marketsConfig);
    }

    @Transactional(readOnly = true)
    public StrategyMarketsResponse getMarkets() {
        return new StrategyMarketsResponse(configuredMarkets());
    }

    @Transactional
    public StrategyMarketsResponse replaceMarkets(List<String> markets) {
        List<String> normalized = normalizeMarkets(markets);
        List<String> existing = loadPersistedMarkets();
        if (!existing.equals(normalized)) {
            // Avoid stale entity state conflicts when replacing the same ids in one transaction.
            marketRepository.deleteAll();
            if (!normalized.isEmpty()) {
                List<StrategyMarketEntity> entities = normalized.stream()
                        .map(StrategyMarketEntity::new)
                        .toList();
                marketRepository.saveAll(entities);
            }
        }

        Set<String> allowed = new LinkedHashSet<>(normalized);
        List<String> staleOverrideMarkets = marketOverrideRepository.findAll()
                .stream()
                .map(StrategyMarketOverrideEntity::getMarket)
                .map(StrategyService::normalizeMarket)
                .filter(market -> market != null && !allowed.contains(market))
                .toList();
        if (!staleOverrideMarkets.isEmpty()) {
            marketOverrideRepository.deleteAllByIdInBatch(staleOverrideMarkets);
        }

        return new StrategyMarketsResponse(normalized);
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
                overrides.profileByMarket(),
                overrides.tradePausedByMarket(),
                overrides.ratiosByMarket()
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
                StrategyMarketOverrideEntity entity = getOrCreateOverride(byMarket, market);
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
                StrategyMarketOverrideEntity entity = getOrCreateOverride(byMarket, market);
                entity.setProfile(profile.name());
            }
        }
        if (request != null && request.tradePausedByMarket() != null) {
            for (Map.Entry<String, Boolean> entry : request.tradePausedByMarket().entrySet()) {
                String market = normalizeMarket(entry.getKey());
                Boolean paused = entry.getValue();
                if (market == null || paused == null) {
                    continue;
                }
                StrategyMarketOverrideEntity entity = getOrCreateOverride(byMarket, market);
                entity.setTradePaused(paused);
            }
        }
        if (request != null && request.ratiosByMarket() != null) {
            for (Map.Entry<String, StrategyMarketRatios> entry : request.ratiosByMarket().entrySet()) {
                String market = normalizeMarket(entry.getKey());
                StrategyMarketRatios ratios = entry.getValue();
                if (market == null || ratios == null || !hasAnyRatio(ratios)) {
                    continue;
                }
                StrategyMarketOverrideEntity entity = getOrCreateOverride(byMarket, market);
                applyRatios(entity, ratios);
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
        Map<String, Boolean> tradePausedByMarket = new HashMap<>();
        Map<String, StrategyMarketRatios> ratiosByMarket = new HashMap<>();
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
            Boolean tradePaused = entity.getTradePaused();
            if (tradePaused != null) {
                tradePausedByMarket.put(market, tradePaused);
            }
            StrategyMarketRatios ratios = toRatios(entity);
            if (ratios != null) {
                ratiosByMarket.put(market, ratios);
            }
        }
        return new StrategyMarketOverrides(
                Map.copyOf(maxOrderKrwByMarket),
                Map.copyOf(profileByMarket),
                Map.copyOf(tradePausedByMarket),
                Map.copyOf(ratiosByMarket)
        );
    }

    private static StrategyMarketRatios toRatios(StrategyMarketOverrideEntity entity) {
        if (entity == null) {
            return null;
        }
        StrategyMarketRatios ratios = new StrategyMarketRatios(
                entity.getTakeProfitPct(),
                entity.getStopLossPct(),
                entity.getTrailingStopPct(),
                entity.getPartialTakeProfitPct(),
                entity.getStopExitPct(),
                entity.getTrendExitPct(),
                entity.getMomentumExitPct()
        );
        return hasAnyRatio(ratios) ? ratios : null;
    }

    private List<String> loadPersistedMarkets() {
        return marketRepository.findAllByOrderByMarketAsc()
                .stream()
                .map(StrategyMarketEntity::getMarket)
                .map(StrategyService::normalizeMarket)
                .filter(market -> market != null)
                .toList();
    }

    private static StrategyMarketOverrideEntity getOrCreateOverride(
            Map<String, StrategyMarketOverrideEntity> byMarket,
            String market
    ) {
        return byMarket.computeIfAbsent(
                market,
                key -> new StrategyMarketOverrideEntity(key, null, null, null, null, null, null, null, null, null, null)
        );
    }

    private static boolean hasAnyRatio(StrategyMarketRatios ratios) {
        if (ratios == null) {
            return false;
        }
        return ratios.takeProfitPct() != null
                || ratios.stopLossPct() != null
                || ratios.trailingStopPct() != null
                || ratios.partialTakeProfitPct() != null
                || ratios.stopExitPct() != null
                || ratios.trendExitPct() != null
                || ratios.momentumExitPct() != null;
    }

    private static void applyRatios(StrategyMarketOverrideEntity entity, StrategyMarketRatios ratios) {
        if (entity == null || ratios == null) {
            return;
        }
        entity.setTakeProfitPct(ratios.takeProfitPct());
        entity.setStopLossPct(ratios.stopLossPct());
        entity.setTrailingStopPct(ratios.trailingStopPct());
        entity.setPartialTakeProfitPct(ratios.partialTakeProfitPct());
        entity.setStopExitPct(ratios.stopExitPct());
        entity.setTrendExitPct(ratios.trendExitPct());
        entity.setMomentumExitPct(ratios.momentumExitPct());
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

    private static List<String> normalizeMarkets(List<String> markets) {
        if (markets == null || markets.isEmpty()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String raw : markets) {
            String market = normalizeMarket(raw);
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

    private void ensureDefaultPresets() {
        List<StrategyPresetEntity> existing = presetRepository.findAllByOrderByCodeAsc();
        Map<String, StrategyPresetEntity> existingByCode = new HashMap<>();
        for (StrategyPresetEntity entity : existing) {
            if (entity == null) {
                continue;
            }
            String code = normalizePresetCode(entity.getCode());
            if (code == null || code.isBlank()) {
                continue;
            }
            existingByCode.putIfAbsent(code, entity);
        }

        List<StrategyPresetEntity> toSave = new ArrayList<>();
        for (StrategyPresetItem defaultPreset : DEFAULT_PRESETS) {
            String code = normalizePresetCode(defaultPreset.code());
            if (code == null) {
                continue;
            }
            StrategyPresetEntity entity = existingByCode.get(code);
            if (entity == null) {
                StrategyPresetEntity insert = StrategyPresetEntity.from(defaultPreset);
                insert.setCode(code);
                toSave.add(insert);
                continue;
            }
            if (syncPresetEntity(entity, defaultPreset, code)) {
                toSave.add(entity);
            }
        }

        if (!toSave.isEmpty()) {
            presetRepository.saveAll(toSave);
        }
    }

    private static boolean syncPresetEntity(StrategyPresetEntity entity, StrategyPresetItem item, String code) {
        boolean changed = false;
        if (!code.equals(entity.getCode())) {
            entity.setCode(code);
            changed = true;
        }
        if (!item.displayName().equals(entity.getDisplayName())) {
            entity.setDisplayName(item.displayName());
            changed = true;
        }
        if (Double.compare(entity.getTakeProfitPct(), item.takeProfitPct()) != 0) {
            entity.setTakeProfitPct(item.takeProfitPct());
            changed = true;
        }
        if (Double.compare(entity.getStopLossPct(), item.stopLossPct()) != 0) {
            entity.setStopLossPct(item.stopLossPct());
            changed = true;
        }
        if (Double.compare(entity.getTrailingStopPct(), item.trailingStopPct()) != 0) {
            entity.setTrailingStopPct(item.trailingStopPct());
            changed = true;
        }
        if (Double.compare(entity.getPartialTakeProfitPct(), item.partialTakeProfitPct()) != 0) {
            entity.setPartialTakeProfitPct(item.partialTakeProfitPct());
            changed = true;
        }
        if (Double.compare(entity.getStopExitPct(), item.stopExitPct()) != 0) {
            entity.setStopExitPct(item.stopExitPct());
            changed = true;
        }
        if (Double.compare(entity.getTrendExitPct(), item.trendExitPct()) != 0) {
            entity.setTrendExitPct(item.trendExitPct());
            changed = true;
        }
        if (Double.compare(entity.getMomentumExitPct(), item.momentumExitPct()) != 0) {
            entity.setMomentumExitPct(item.momentumExitPct());
            changed = true;
        }
        return changed;
    }

    private static boolean isLegacyConservativeDefaults(StrategyConfigEntity entity) {
        if (entity == null) {
            return false;
        }
        return Double.compare(entity.getTakeProfitPct(), 4.0) == 0
                && Double.compare(entity.getStopLossPct(), 2.0) == 0
                && Double.compare(entity.getTrailingStopPct(), 2.0) == 0
                && Double.compare(entity.getPartialTakeProfitPct(), 50.0) == 0
                && Double.compare(entity.getStopExitPct(), 100.0) == 0
                && Double.compare(entity.getTrendExitPct(), 50.0) == 0
                && Double.compare(entity.getMomentumExitPct(), 50.0) == 0
                && StrategyProfile.CONSERVATIVE.name().equalsIgnoreCase(entity.getProfile());
    }

    private static boolean isLegacyBalancedDefaults(StrategyConfigEntity entity) {
        if (entity == null) {
            return false;
        }
        return Double.compare(entity.getTakeProfitPct(), 4.5) == 0
                && Double.compare(entity.getStopLossPct(), 2.2) == 0
                && Double.compare(entity.getTrailingStopPct(), 2.3) == 0
                && Double.compare(entity.getPartialTakeProfitPct(), 40.0) == 0
                && Double.compare(entity.getStopExitPct(), 100.0) == 0
                && Double.compare(entity.getTrendExitPct(), 0.0) == 0
                && Double.compare(entity.getMomentumExitPct(), 0.0) == 0
                && StrategyProfile.BALANCED.name().equalsIgnoreCase(entity.getProfile());
    }

    private static boolean isBaselineBalancedDefaults(StrategyConfigEntity entity) {
        if (entity == null) {
            return false;
        }
        return Double.compare(entity.getTakeProfitPct(), 2.4) == 0
                && Double.compare(entity.getStopLossPct(), 1.6) == 0
                && Double.compare(entity.getTrailingStopPct(), 1.2) == 0
                && Double.compare(entity.getPartialTakeProfitPct(), 35.0) == 0
                && Double.compare(entity.getStopExitPct(), 100.0) == 0
                && Double.compare(entity.getTrendExitPct(), 40.0) == 0
                && Double.compare(entity.getMomentumExitPct(), 25.0) == 0
                && StrategyProfile.BALANCED.name().equalsIgnoreCase(entity.getProfile());
    }

    private static String normalizePresetCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized;
    }
}
