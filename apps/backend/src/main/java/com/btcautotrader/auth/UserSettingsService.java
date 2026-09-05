package com.btcautotrader.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.btcautotrader.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * user_settings is identity-scoped and lives only in the system database. Tenant-scoped endpoints
 * (/api/strategy/**) reach this service with TenantContext already bound, so every repository call is
 * pinned back to the system database; otherwise the same userId would be written to the tenant database
 * (violating user_settings.user_id -> app_users(id)) and read back from the system database.
 */
@Service
public class UserSettingsService {
    private static final Pattern MARKET_CODE_PATTERN = Pattern.compile("^[A-Z]{2,10}-[A-Z0-9]{2,15}$");
    private static final Set<String> ALLOWED_PROFILES = Set.of("BALANCED", "AGGRESSIVE", "CONSERVATIVE");
    private static final String DEFAULT_PROFILE = "BALANCED";
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final UserSettingsRepository userSettingsRepository;
    private final ObjectMapper objectMapper;

    public UserSettingsService(
            UserSettingsRepository userSettingsRepository,
            ObjectMapper objectMapper
    ) {
        this.userSettingsRepository = userSettingsRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public UserSettingsResponse getSettings(Long userId) {
        return TenantContext.callWithTenantDatabase(null, () -> {
            UserSettingsEntity entity = userSettingsRepository.findById(userId).orElse(null);
            if (entity == null) {
                return new UserSettingsResponse(List.of(), DEFAULT_PROFILE, Map.of(), null);
            }
            return toResponse(entity);
        });
    }

    @Transactional(readOnly = true)
    public Optional<List<String>> findPreferredMarkets(Long userId) {
        return TenantContext.callWithTenantDatabase(null, () -> {
            UserSettingsEntity entity = userSettingsRepository.findById(userId).orElse(null);
            if (entity == null) {
                return Optional.empty();
            }
            String rawJson = entity.getPreferredMarketsJson();
            if (rawJson == null || rawJson.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(parseMarkets(rawJson));
        });
    }

    @Transactional
    public UserSettingsResponse updateSettings(Long userId, UserSettingsRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }

        List<String> markets = normalizeMarkets(request.markets());
        String riskProfile = normalizeRiskProfile(request.riskProfile());
        Map<String, Object> uiPrefs = normalizeUiPrefs(request.uiPrefs());

        return TenantContext.callWithTenantDatabase(null, () -> {
            UserSettingsEntity entity = findOrCreateEntity(userId);
            entity.setPreferredMarketsJson(toJson(markets));
            entity.setRiskProfile(riskProfile);
            entity.setUiPrefsJson(toJson(uiPrefs));
            return toResponse(userSettingsRepository.save(entity));
        });
    }

    @Transactional
    public UserSettingsResponse updateMarkets(Long userId, List<String> markets) {
        List<String> normalizedMarkets = normalizeMarkets(markets);
        return TenantContext.callWithTenantDatabase(null, () -> {
            UserSettingsEntity entity = findOrCreateEntity(userId);
            entity.setPreferredMarketsJson(toJson(normalizedMarkets));
            if (entity.getRiskProfile() == null || entity.getRiskProfile().isBlank()) {
                entity.setRiskProfile(DEFAULT_PROFILE);
            }
            if (entity.getUiPrefsJson() == null || entity.getUiPrefsJson().isBlank()) {
                entity.setUiPrefsJson(toJson(Map.of()));
            }
            return toResponse(userSettingsRepository.save(entity));
        });
    }

    private UserSettingsResponse toResponse(UserSettingsEntity entity) {
        List<String> markets = parseMarkets(entity.getPreferredMarketsJson());
        String riskProfile = normalizeRiskProfile(entity.getRiskProfile());
        Map<String, Object> uiPrefs = parseUiPrefs(entity.getUiPrefsJson());
        OffsetDateTime updatedAt = entity.getUpdatedAt();

        return new UserSettingsResponse(markets, riskProfile, uiPrefs, updatedAt);
    }

    private UserSettingsEntity findOrCreateEntity(Long userId) {
        return userSettingsRepository.findById(userId).orElseGet(() -> {
            UserSettingsEntity created = new UserSettingsEntity();
            created.setUserId(userId);
            return created;
        });
    }

    private List<String> normalizeMarkets(List<String> markets) {
        if (markets == null) {
            return List.of();
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String raw : markets) {
            if (raw == null || raw.isBlank()) {
                continue;
            }

            String market = raw.trim().toUpperCase(Locale.ROOT);
            if (!MARKET_CODE_PATTERN.matcher(market).matches()) {
                throw new IllegalArgumentException("invalid market code: " + raw);
            }
            normalized.add(market);
        }

        return List.copyOf(normalized);
    }

    private String normalizeRiskProfile(String riskProfile) {
        if (riskProfile == null || riskProfile.isBlank()) {
            return DEFAULT_PROFILE;
        }

        String normalized = riskProfile.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_PROFILES.contains(normalized)) {
            throw new IllegalArgumentException("riskProfile must be AGGRESSIVE, BALANCED, or CONSERVATIVE");
        }
        return normalized;
    }

    private Map<String, Object> normalizeUiPrefs(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key == null || key.isBlank()) {
                return;
            }
            normalized.put(key.trim(), value);
        });

        return normalized;
    }

    private List<String> parseMarkets(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return List.of();
        }

        try {
            List<String> parsed = objectMapper.readValue(rawJson, STRING_LIST_TYPE);
            return normalizeMarkets(parsed);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private Map<String, Object> parseUiPrefs(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Map.of();
        }

        try {
            Map<String, Object> parsed = objectMapper.readValue(rawJson, MAP_TYPE);
            return normalizeUiPrefs(parsed);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize settings", ex);
        }
    }
}
