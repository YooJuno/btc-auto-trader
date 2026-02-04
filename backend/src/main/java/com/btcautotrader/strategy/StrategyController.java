package com.btcautotrader.strategy;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/strategy")
public class StrategyController {
    private final StrategyService strategyService;

    public StrategyController(StrategyService strategyService) {
        this.strategyService = strategyService;
    }

    @GetMapping
    public ResponseEntity<StrategyConfig> getStrategy() {
        return ResponseEntity.ok(strategyService.getConfig());
    }

    @PutMapping
    public ResponseEntity<?> updateStrategy(@RequestBody(required = false) StrategyConfig config) {
        if (config == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "request body is required");
            return ResponseEntity.badRequest().body(error);
        }
        return ResponseEntity.ok(strategyService.updateConfig(config));
    }
}
