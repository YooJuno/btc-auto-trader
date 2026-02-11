package com.btcautotrader.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class TradeDecisionService {
    private static final Pattern OID_LITERAL_PATTERN = Pattern.compile("^\\d{5,19}$");
    private static final List<String> TRADE_ACTIONS = List.of("BUY", "SELL");

    private final TradeDecisionRepository repository;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public TradeDecisionService(
            TradeDecisionRepository repository,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(TradeDecisionEntity entity, Map<String, Object> details) {
        if (entity == null) {
            return;
        }
        if (details != null && !details.isEmpty()) {
            entity.setDetails(safeSerialize(details));
        }
        repository.save(entity);
    }

    public List<TradeDecisionItem> listRecent(int limit) {
        return listRecent(limit, true);
    }

    @Transactional(readOnly = true)
    public List<TradeDecisionItem> listRecent(int limit, boolean includeSkips) {
        int safeLimit = normalizeLimit(limit);
        PageRequest pageRequest = PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "executedAt"));
        List<TradeDecisionEntity> entities = includeSkips
                ? repository.findAll(pageRequest).getContent()
                : repository.findByActionIn(TRADE_ACTIONS, pageRequest).getContent();
        return entities
                .stream()
                .map(this::toItem)
                .toList();
    }

    private String safeSerialize(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return details.toString();
        }
    }

    private TradeDecisionItem toItem(TradeDecisionEntity entity) {
        return new TradeDecisionItem(
                entity.getId(),
                entity.getMarket(),
                entity.getAction(),
                entity.getReason(),
                entity.getExecutedAt() == null ? null : entity.getExecutedAt().toString(),
                entity.getProfile(),
                entity.getPrice(),
                entity.getQuantity(),
                entity.getFunds(),
                entity.getOrderId(),
                entity.getRequestStatus(),
                entity.getMaShort(),
                entity.getMaLong(),
                entity.getRsi(),
                entity.getMacdHistogram(),
                entity.getBreakoutLevel(),
                entity.getTrailingHigh(),
                entity.getMaLongSlopePct(),
                entity.getVolatilityPct(),
                parseDetails(entity.getDetails())
        );
    }

    private Map<String, Object> parseDetails(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() { });
        } catch (JsonProcessingException ex) {
            String recovered = tryRecoverLargeObjectText(raw);
            if (recovered != null) {
                try {
                    return objectMapper.readValue(recovered, new TypeReference<Map<String, Object>>() { });
                } catch (JsonProcessingException ignored) {
                    // Ignore and fallback to raw details below.
                }
            }
            return Map.of("raw", raw);
        }
    }

    private String tryRecoverLargeObjectText(String raw) {
        if (raw == null || !OID_LITERAL_PATTERN.matcher(raw).matches()) {
            return null;
        }
        try {
            return jdbcTemplate.query(
                    "select convert_from(lo_get(cast(? as oid)), 'UTF8')",
                    rs -> rs.next() ? rs.getString(1) : null,
                    raw
            );
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 30;
        }
        return Math.min(limit, 200);
    }
}
