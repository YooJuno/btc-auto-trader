package com.btcautotrader.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

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

    private String safeSerialize(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return details.toString();
        }
    }
}
