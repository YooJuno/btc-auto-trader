package com.btcautotrader.strategy;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/strategy")
public class StrategyController {
    private static final Pattern MARKET_CODE_PATTERN = Pattern.compile("^[A-Z]{2,10}-[A-Z0-9]{2,15}$");

    private final StrategyService strategyService;

    public StrategyController(StrategyService strategyService) {
        this.strategyService = strategyService;
    }

    @GetMapping
    public ResponseEntity<StrategyConfig> getStrategy() {
        return ResponseEntity.ok(strategyService.getConfig());
    }

    @GetMapping("/market-overrides")
    public ResponseEntity<StrategyMarketOverridesResponse> getMarketOverrides() {
        return ResponseEntity.ok(strategyService.getMarketOverrides());
    }

    @GetMapping("/presets")
    public ResponseEntity<List<StrategyPresetItem>> getPresets() {
        return ResponseEntity.ok(strategyService.getPresets());
    }

    @GetMapping("/markets")
    public ResponseEntity<StrategyMarketsResponse> getMarkets() {
        return ResponseEntity.ok(strategyService.getMarkets());
    }

    @PutMapping("/markets")
    public ResponseEntity<?> replaceMarkets(@RequestBody(required = false) StrategyMarketsRequest request) {
        if (request == null || request.markets() == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "request body is required");
            return ResponseEntity.badRequest().body(error);
        }

        Map<String, String> errors = new LinkedHashMap<>();
        List<String> markets = normalizeMarkets(request.markets(), errors);
        if (markets.isEmpty()) {
            errors.put("markets", "at least one market is required");
        }
        if (!errors.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "invalid market values");
            error.put("fields", errors);
            return ResponseEntity.badRequest().body(error);
        }

        return ResponseEntity.ok(strategyService.replaceMarkets(markets));
    }

    @PutMapping
    public ResponseEntity<?> updateStrategy(@RequestBody(required = false) StrategyConfig config) {
        if (config == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "request body is required");
            return ResponseEntity.badRequest().body(error);
        }

        Map<String, String> errors = new HashMap<>();
        validateRequiredPositive("maxOrderKrw", config.maxOrderKrw(), errors);
        validatePct("takeProfitPct", config.takeProfitPct(), errors);
        validatePct("stopLossPct", config.stopLossPct(), errors);
        validatePct("trailingStopPct", config.trailingStopPct(), errors);
        validatePct("partialTakeProfitPct", config.partialTakeProfitPct(), errors);
        validatePct("stopExitPct", config.stopExitPct(), errors);
        validatePct("trendExitPct", config.trendExitPct(), errors);
        validatePct("momentumExitPct", config.momentumExitPct(), errors);
        if (config.profile() != null && !config.profile().isBlank() && parseProfile(config.profile()) == null) {
            errors.put("profile", "must be AGGRESSIVE, BALANCED, or CONSERVATIVE");
        }
        if (!errors.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "invalid strategy values");
            error.put("fields", errors);
            return ResponseEntity.badRequest().body(error);
        }

        return ResponseEntity.ok(strategyService.updateConfig(config));
    }

    @PatchMapping("/ratios")
    public ResponseEntity<?> updateRatios(@RequestBody(required = false) StrategyRatiosRequest request) {
        if (request == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "request body is required");
            return ResponseEntity.badRequest().body(error);
        }

        Map<String, String> errors = new HashMap<>();
        validatePct("takeProfitPct", request.takeProfitPct(), errors);
        validatePct("stopLossPct", request.stopLossPct(), errors);
        validatePct("trailingStopPct", request.trailingStopPct(), errors);
        validatePct("partialTakeProfitPct", request.partialTakeProfitPct(), errors);
        validatePct("stopExitPct", request.stopExitPct(), errors);
        validatePct("trendExitPct", request.trendExitPct(), errors);
        validatePct("momentumExitPct", request.momentumExitPct(), errors);

        if (!errors.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "invalid ratio values");
            error.put("fields", errors);
            return ResponseEntity.badRequest().body(error);
        }

        return ResponseEntity.ok(strategyService.updateRatios(request));
    }

    @PutMapping("/market-overrides")
    public ResponseEntity<?> replaceMarketOverrides(
            @RequestBody(required = false) StrategyMarketOverridesRequest request
    ) {
        if (request == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "request body is required");
            return ResponseEntity.badRequest().body(error);
        }

        Map<String, String> errors = new LinkedHashMap<>();
        Set<String> configuredMarkets = new HashSet<>(strategyService.configuredMarkets());
        Map<String, Double> maxOrderKrwByMarket = normalizeMaxOrderKrwMap(
                request.maxOrderKrwByMarket(),
                configuredMarkets,
                errors
        );
        Map<String, String> profileByMarket = normalizeProfileMap(
                request.profileByMarket(),
                configuredMarkets,
                errors
        );
        Map<String, StrategyMarketRatios> ratiosByMarket = normalizeRatiosMap(
                request.ratiosByMarket(),
                configuredMarkets,
                errors
        );
        if (!errors.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "invalid market override values");
            error.put("fields", errors);
            return ResponseEntity.badRequest().body(error);
        }

        StrategyMarketOverridesRequest normalized = new StrategyMarketOverridesRequest(
                maxOrderKrwByMarket,
                profileByMarket,
                ratiosByMarket
        );
        return ResponseEntity.ok(strategyService.replaceMarketOverrides(normalized));
    }

    private static Map<String, Double> normalizeMaxOrderKrwMap(
            Map<String, Double> source,
            Set<String> configuredMarkets,
            Map<String, String> errors
    ) {
        Map<String, Double> normalized = new HashMap<>();
        if (source == null) {
            return normalized;
        }
        for (Map.Entry<String, Double> entry : source.entrySet()) {
            String market = normalizeMarket(entry.getKey());
            if (market == null) {
                errors.put("maxOrderKrwByMarket", "market key is required");
                continue;
            }
            if (!configuredMarkets.contains(market)) {
                errors.put("maxOrderKrwByMarket." + market, "market is not configured");
                continue;
            }
            Double value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0.0) {
                errors.put("maxOrderKrwByMarket." + market, "must be greater than 0");
                continue;
            }
            normalized.put(market, value);
        }
        return normalized;
    }

    private static Map<String, String> normalizeProfileMap(
            Map<String, String> source,
            Set<String> configuredMarkets,
            Map<String, String> errors
    ) {
        Map<String, String> normalized = new HashMap<>();
        if (source == null) {
            return normalized;
        }
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String market = normalizeMarket(entry.getKey());
            if (market == null) {
                errors.put("profileByMarket", "market key is required");
                continue;
            }
            if (!configuredMarkets.contains(market)) {
                errors.put("profileByMarket." + market, "market is not configured");
                continue;
            }
            String value = entry.getValue();
            if (value == null || value.isBlank()) {
                continue;
            }
            StrategyProfile profile = parseProfile(value);
            if (profile == null) {
                errors.put("profileByMarket." + market, "must be AGGRESSIVE, BALANCED, or CONSERVATIVE");
                continue;
            }
            normalized.put(market, profile.name());
        }
        return normalized;
    }

    private static Map<String, StrategyMarketRatios> normalizeRatiosMap(
            Map<String, StrategyMarketRatios> source,
            Set<String> configuredMarkets,
            Map<String, String> errors
    ) {
        Map<String, StrategyMarketRatios> normalized = new HashMap<>();
        if (source == null) {
            return normalized;
        }
        for (Map.Entry<String, StrategyMarketRatios> entry : source.entrySet()) {
            String market = normalizeMarket(entry.getKey());
            if (market == null) {
                errors.put("ratiosByMarket", "market key is required");
                continue;
            }
            if (!configuredMarkets.contains(market)) {
                errors.put("ratiosByMarket." + market, "market is not configured");
                continue;
            }
            StrategyMarketRatios ratios = entry.getValue();
            if (ratios == null) {
                continue;
            }
            validatePct("ratiosByMarket." + market + ".takeProfitPct", ratios.takeProfitPct(), errors);
            validatePct("ratiosByMarket." + market + ".stopLossPct", ratios.stopLossPct(), errors);
            validatePct("ratiosByMarket." + market + ".trailingStopPct", ratios.trailingStopPct(), errors);
            validatePct("ratiosByMarket." + market + ".partialTakeProfitPct", ratios.partialTakeProfitPct(), errors);
            validatePct("ratiosByMarket." + market + ".stopExitPct", ratios.stopExitPct(), errors);
            validatePct("ratiosByMarket." + market + ".trendExitPct", ratios.trendExitPct(), errors);
            validatePct("ratiosByMarket." + market + ".momentumExitPct", ratios.momentumExitPct(), errors);

            StrategyMarketRatios ratioItem = new StrategyMarketRatios(
                    ratios.takeProfitPct(),
                    ratios.stopLossPct(),
                    ratios.trailingStopPct(),
                    ratios.partialTakeProfitPct(),
                    ratios.stopExitPct(),
                    ratios.trendExitPct(),
                    ratios.momentumExitPct()
            );
            if (hasAnyRatio(ratioItem)) {
                normalized.put(market, ratioItem);
            }
        }
        return normalized;
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

    private static StrategyProfile parseProfile(String value) {
        try {
            return StrategyProfile.valueOf(value.trim().toUpperCase());
        } catch (RuntimeException ex) {
            return null;
        }
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

    private static List<String> normalizeMarkets(List<String> source, Map<String, String> errors) {
        if (source == null) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        List<String> normalized = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            String market = normalizeMarket(source.get(i));
            if (market == null) {
                errors.put("markets[" + i + "]", "market is required");
                continue;
            }
            if (!MARKET_CODE_PATTERN.matcher(market).matches()) {
                errors.put("markets[" + i + "]", "invalid market format (e.g. KRW-BTC)");
                continue;
            }
            if (unique.add(market)) {
                normalized.add(market);
            }
        }
        return normalized;
    }

    private static void validatePct(String field, Double value, Map<String, String> errors) {
        if (value == null) {
            return;
        }
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0 || value > 100) {
            errors.put(field, "must be between 0 and 100");
        }
    }

    private static void validateRequiredPositive(String field, double value, Map<String, String> errors) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0) {
            errors.put(field, "must be greater than 0");
        }
    }
}
