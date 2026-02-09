package com.btcautotrader.engine;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/engine")
public class EngineController {
    private final EngineService engineService;
    private final AutoTradeService autoTradeService;

    public EngineController(EngineService engineService, AutoTradeService autoTradeService) {
        this.engineService = engineService;
        this.autoTradeService = autoTradeService;
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start() {
        boolean running = engineService.start();
        return ResponseEntity.ok(statusResponse(running));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(statusResponse(engineService.isRunning()));
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stop() {
        boolean running = engineService.stop();
        return ResponseEntity.ok(statusResponse(running));
    }

    @PostMapping("/tick")
    public ResponseEntity<AutoTradeResult> tick(
            @RequestParam(name = "force", defaultValue = "false") boolean force
    ) {
        if (!force && !engineService.isRunning()) {
            AutoTradeAction action = new AutoTradeAction(
                    "SYSTEM",
                    "SKIP",
                    "engine_stopped",
                    null,
                    null,
                    null,
                    null,
                    null
            );
            AutoTradeResult result = new AutoTradeResult(OffsetDateTime.now().toString(), List.of(action));
            return ResponseEntity.status(409).body(result);
        }
        return ResponseEntity.ok(autoTradeService.runOnce());
    }

    private Map<String, Object> statusResponse(boolean running) {
        Map<String, Object> response = new HashMap<>();
        response.put("running", running);
        response.put("timestamp", OffsetDateTime.now().toString());
        return response;
    }
}
