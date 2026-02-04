package com.btcautotrader.engine;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/engine")
public class EngineController {
    private final EngineService engineService;

    public EngineController(EngineService engineService) {
        this.engineService = engineService;
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start() {
        boolean running = engineService.start();
        return ResponseEntity.ok(statusResponse(running));
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stop() {
        boolean running = engineService.stop();
        return ResponseEntity.ok(statusResponse(running));
    }

    private Map<String, Object> statusResponse(boolean running) {
        Map<String, Object> response = new HashMap<>();
        response.put("running", running);
        response.put("timestamp", OffsetDateTime.now().toString());
        return response;
    }
}
