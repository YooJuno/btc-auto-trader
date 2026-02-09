package com.btcautotrader.strategy;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

    private static void validatePct(String field, Double value, Map<String, String> errors) {
        if (value == null) {
            return;
        }
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0 || value > 100) {
            errors.put(field, "must be between 0 and 100");
        }
    }
}
