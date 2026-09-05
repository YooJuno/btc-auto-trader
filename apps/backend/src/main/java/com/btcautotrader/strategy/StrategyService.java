package com.btcautotrader.strategy;

import com.btcautotrader.auth.UserSettingsResponse;
import com.btcautotrader.auth.UserSettingsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class StrategyService {
    private static final long CONFIG_ID = 1L;
    private static final StrategyConfig LEGACY_BALANCED_CONFIG =
            new StrategyConfig(true, 30000.0, 2.4, 1.28, 0.9, 35.0, StrategyProfile.BALANCED.name(),
                    100.0, 40.0, 25.0, 0.7);
    private static final StrategyConfig PREVIOUS_BALANCED_CONFIG =
            new StrategyConfig(true, 30000.0, 1.92, 1.024, 0.675, 35.0, StrategyProfile.BALANCED.name(),
                    100.0, 40.0, 25.0, 0.7);
    private static final StrategyConfig DEFAULT_CONFIG =
            new StrategyConfig(true, 30000.0, 1.44, 1.024, 0.675, 35.0, StrategyProfile.BALANCED.name(),
                    100.0, 40.0, 25.0, 0.7);
    private static final List<StrategyPresetItem> DEFAULT_PRESETS = List.of(
            new StrategyPresetItem(
                    "BALANCED",
                    "밸런스",
                    1.44,
                    1.024,
                    0.675,
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
    private final StrategyMarketOverrideRepository marketOverrideRepository;
    private final StrategyPresetRepository presetRepository;
    private final UserSettingsService userSettingsService;
    private final String marketsConfig;

    public StrategyService(
            StrategyConfigRepository repository,
            StrategyMarketOverrideRepository marketOverrideRepository,
            StrategyPresetRepository presetRepository,
            UserSettingsService userSettingsService,
            @Value("${trading.markets:KRW-BTC}") String marketsConfig
    ) {
        this.repository = repository;
        this.marketOverrideRepository = marketOverrideRepository;
        this.presetRepository = presetRepository;
        this.userSettingsService = userSettingsService;
        this.marketsConfig = marketsConfig;
    }

    @Transactional
    public StrategyConfig getConfig() {
        Optional<StrategyConfigEntity> found = repository.findById(CONFIG_ID);
        StrategyConfigEntity entity = found.orElseGet(() -> StrategyConfigEntity.from(CONFIG_ID, DEFAULT_CONFIG));
        boolean dirty = found.isEmpty();

        if (usesAutoUpgradeableBalancedDefaults(entity)) {
            applyDefaultRiskRatios(entity);
            dirty = true;
        }
        if (entity.getTrailingStopPct() == 0.0 && entity.getPartialTakeProfitPct() == 0.0) {
            entity.setTrailingStopPct(DEFAULT_CONFIG.trailingStopPct());
            entity.setPartialTakeProfitPct(DEFAULT_CONFIG.partialTakeProfitPct());
            dirty = true;
        }
        if (entity.getProfile() == null || entity.getProfile().isBlank()) {
            entity.setProfile(DEFAULT_CONFIG.profile());
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
        if (entity.getRiskPerTradePct() <= 0.0) {
            entity.setRiskPerTradePct(DEFAULT_CONFIG.riskPerTradePct());
            dirty = true;
        }
        if (dirty) {
            entity = repository.save(entity);
        }

        return entity.toRecord();
    }

    private static boolean usesAutoUpgradeableBalancedDefaults(StrategyConfigEntity entity) {
        if (entity == null) {
            return false;
        }
        return matches(entity, LEGACY_BALANCED_CONFIG) || matches(entity, PREVIOUS_BALANCED_CONFIG);
    }

    private static void applyDefaultRiskRatios(StrategyConfigEntity entity) {
        entity.setTakeProfitPct(DEFAULT_CONFIG.takeProfitPct());
        entity.setStopLossPct(DEFAULT_CONFIG.stopLossPct());
        entity.setTrailingStopPct(DEFAULT_CONFIG.trailingStopPct());
        entity.setPartialTakeProfitPct(DEFAULT_CONFIG.partialTakeProfitPct());
        entity.setStopExitPct(DEFAULT_CONFIG.stopExitPct());
        entity.setTrendExitPct(DEFAULT_CONFIG.trendExitPct());
        entity.setMomentumExitPct(DEFAULT_CONFIG.momentumExitPct());
        entity.setRiskPerTradePct(DEFAULT_CONFIG.riskPerTradePct());
        entity.setProfile(DEFAULT_CONFIG.profile());
    }

    private static boolean matches(StrategyConfigEntity entity, StrategyConfig config) {
        return matches(entity.getTakeProfitPct(), config.takeProfitPct())
                && matches(entity.getStopLossPct(), config.stopLossPct())
                && matches(entity.getTrailingStopPct(), config.trailingStopPct())
                && matches(entity.getPartialTakeProfitPct(), config.partialTakeProfitPct())
                && matches(entity.getStopExitPct(), config.stopExitPct())
                && matches(entity.getTrendExitPct(), config.trendExitPct())
                && matches(entity.getMomentumExitPct(), config.momentumExitPct())
                && matches(entity.getRiskPerTradePct(), config.riskPerTradePct())
                && matches(entity.getMaxOrderKrw(), config.maxOrderKrw())
                && StrategyProfile.from(entity.getProfile()) == StrategyProfile.BALANCED;
    }

    private static boolean matches(double actual, double expected) {
        return Math.abs(actual - expected) < 0.000001;
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
    public String configuredMarket() {
        List<String> configured = configuredMarkets(null);
        if (configured.isEmpty()) {
            return "KRW-BTC";
        }
        return configured.get(0);
    }

    @Transactional(readOnly = true)
    public List<String> configuredMarkets(Long userId) {
        Optional<List<String>> configured = configuredUserMarkets(userId);
        if (configured.isPresent()) {
            return configured.get();
        }
        return parseMarkets(marketsConfig);
    }

    @Transactional(readOnly = true)
    public StrategyMarketOverrides getMarketOverridesSnapshot(Long userId) {
        Set<String> configuredMarkets = new LinkedHashSet<>(configuredMarkets(userId));
        if (configuredMarkets.isEmpty()) {
            return emptyMarketOverrides();
        }
        return toMarketOverrides(marketOverrideRepository.findAll(), configuredMarkets);
    }

    @Transactional
    public StrategyMarketOverridesResponse getMarketOverrides(Long userId) {
        List<String> markets = configuredMarkets(userId);
        StrategyConfig config = getConfig();
        StrategyMarketOverrides overrides = getMarketOverridesSnapshot(userId);

        Map<String, Double> maxOrderKrwByMarket = new HashMap<>();
        Map<String, String> profileByMarket = new HashMap<>();
        Map<String, String> signalModelByMarket = new HashMap<>();
        Map<String, Boolean> tradePausedByMarket = new HashMap<>();
        Map<String, StrategyMarketRatios> ratiosByMarket = new HashMap<>();

        String defaultProfile = StrategyProfile.from(config.profile()).name();
        for (String market : markets) {
            maxOrderKrwByMarket.put(
                    market,
                    overrides.maxOrderKrwByMarket().getOrDefault(market, config.maxOrderKrw())
            );
            profileByMarket.put(
                    market,
                    overrides.profileByMarket().getOrDefault(market, defaultProfile)
            );
            // Absent means "inherit signal.model"; the engine, not this response, owns that default.
            String signalModel = overrides.signalModelByMarket().get(market);
            if (signalModel != null) {
                signalModelByMarket.put(market, signalModel);
            }
            tradePausedByMarket.put(
                    market,
                    overrides.tradePausedByMarket().getOrDefault(market, false)
            );
            StrategyMarketRatios ratios = overrides.ratiosByMarket().get(market);
            if (ratios != null) {
                ratiosByMarket.put(market, ratios);
            }
        }

        return new StrategyMarketOverridesResponse(
                List.copyOf(markets),
                Map.copyOf(maxOrderKrwByMarket),
                Map.copyOf(profileByMarket),
                Map.copyOf(signalModelByMarket),
                Map.copyOf(tradePausedByMarket),
                Map.copyOf(ratiosByMarket)
        );
    }

    @Transactional
    public StrategyMarketOverridesResponse replaceMarketOverrides(Long userId, StrategyMarketOverridesRequest request) {
        List<String> markets = normalizeMarkets(request == null ? null : request.markets());
        userSettingsService.updateMarkets(userId, markets);

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
                String profile = entry.getValue();
                if (market == null || profile == null || profile.isBlank()) {
                    continue;
                }
                StrategyMarketOverrideEntity entity = getOrCreateOverride(byMarket, market);
                entity.setProfile(StrategyProfile.from(profile).name());
            }
        }
        if (request != null && request.signalModelByMarket() != null) {
            for (Map.Entry<String, String> entry : request.signalModelByMarket().entrySet()) {
                String market = normalizeMarket(entry.getKey());
                String signalModel = normalizeSignalModel(entry.getValue());
                if (market == null) {
                    continue;
                }
                StrategyMarketOverrideEntity entity = getOrCreateOverride(byMarket, market);
                entity.setSignalModel(signalModel);
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
        return getMarketOverrides(userId);
    }

    private Optional<List<String>> configuredUserMarkets(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        Optional<List<String>> settingsMarkets = userSettingsService.findPreferredMarkets(userId);
        if (settingsMarkets == null) {
            return Optional.empty();
        }
        return settingsMarkets.map(List::copyOf);
    }

    /**
     * Known entry-model names. Kept here rather than referencing the engine so the strategy module does
     * not depend on it; the engine falls back to its default for anything it does not recognise anyway.
     */
    private static final Set<String> SIGNAL_MODELS = Set.of("trend_breakout", "squeeze_breakout");

    /** null means "inherit signal.model", which is also what an unknown value degrades to. */
    static String normalizeSignalModel(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return SIGNAL_MODELS.contains(normalized) ? normalized : null;
    }

    private static StrategyMarketOverrides emptyMarketOverrides() {
        return new StrategyMarketOverrides(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
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

    private static StrategyMarketOverrides toMarketOverrides(
            List<StrategyMarketOverrideEntity> entities,
            Set<String> configuredMarkets
    ) {
        if (entities == null || entities.isEmpty() || configuredMarkets == null || configuredMarkets.isEmpty()) {
            return emptyMarketOverrides();
        }

        Map<String, Double> maxOrderKrwByMarket = new HashMap<>();
        Map<String, String> profileByMarket = new HashMap<>();
        Map<String, String> signalModelByMarket = new HashMap<>();
        Map<String, Boolean> tradePausedByMarket = new HashMap<>();
        Map<String, StrategyMarketRatios> ratiosByMarket = new HashMap<>();
        for (StrategyMarketOverrideEntity entity : entities) {
            if (entity == null) {
                continue;
            }
            String market = normalizeMarket(entity.getMarket());
            if (market == null || !configuredMarkets.contains(market)) {
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
            String signalModel = normalizeSignalModel(entity.getSignalModel());
            if (signalModel != null) {
                signalModelByMarket.put(market, signalModel);
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
                Map.copyOf(signalModelByMarket),
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
