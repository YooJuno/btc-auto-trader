package com.btcautotrader.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class TradeDecisionService {
    private final TradeDecisionRepository repository;
    private final ObjectMapper objectMapper;

    public TradeDecisionService(TradeDecisionRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
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
        int safeLimit = normalizeLimit(limit);
        PageRequest pageRequest = PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "executedAt"));
        return repository.findAll(pageRequest)
                .getContent()
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
            return Map.of("raw", raw);
        }
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 30;
        }
        return Math.min(limit, 200);
    }
}
