package com.btcautotrader.engine;

import com.btcautotrader.auth.TradingAccessService;
import com.btcautotrader.auth.CurrentUserService;
import com.btcautotrader.auth.UserEntity;
import com.btcautotrader.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/engine")
public class EngineController {
    private final EngineService engineService;
    private final AutoTradeService autoTradeService;
    private final TradeDecisionService tradeDecisionService;
    private final TradingAccessService tradingAccessService;
    private final CurrentUserService currentUserService;

    public EngineController(
            EngineService engineService,
            AutoTradeService autoTradeService,
            TradeDecisionService tradeDecisionService,
            TradingAccessService tradingAccessService,
            CurrentUserService currentUserService
    ) {
        this.engineService = engineService;
        this.autoTradeService = autoTradeService;
        this.tradeDecisionService = tradeDecisionService;
        this.tradingAccessService = tradingAccessService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start(Authentication authentication) {
        tradingAccessService.requireEngineExecutionAllowed(authentication);
        boolean running = callInUserTenant(authentication, engineService::start);
        return ResponseEntity.ok(statusResponse(running));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(Authentication authentication) {
        boolean running = callInUserTenant(authentication, engineService::isRunning);
        return ResponseEntity.ok(statusResponse(running));
    }

    @GetMapping("/decisions")
    public ResponseEntity<List<TradeDecisionItem>> decisions(
            Authentication authentication,
            @RequestParam(name = "limit", defaultValue = "30") int limit,
            @RequestParam(name = "includeSkips", defaultValue = "true") boolean includeSkips
    ) {
        List<TradeDecisionItem> items = callInUserTenant(
                authentication,
                () -> tradeDecisionService.listRecent(limit, includeSkips)
        );
        return ResponseEntity.ok(items);
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stop(Authentication authentication) {
        boolean running = callInUserTenant(authentication, engineService::stop);
        return ResponseEntity.ok(statusResponse(running));
    }

    @PostMapping("/tick")
    public ResponseEntity<AutoTradeResult> tick(
            Authentication authentication,
            @RequestParam(name = "force", defaultValue = "false") boolean force
    ) {
        tradingAccessService.requireEngineExecutionAllowed(authentication);
        AutoTradeResult result = callInUserTenant(authentication, () -> {
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
                return new AutoTradeResult(OffsetDateTime.now().toString(), List.of(action));
            }
            return autoTradeService.runOnce();
        });
        if (!force && result.actions().size() == 1 && "engine_stopped".equals(result.actions().get(0).reason())) {
            return ResponseEntity.status(409).body(result);
        }
        return ResponseEntity.ok(result);
    }

    private <T> T callInUserTenant(Authentication authentication, Supplier<T> supplier) {
        UserEntity user = TenantContext.callWithTenantDatabase(null, () -> currentUserService.requireUser(authentication));
        String tenantDatabase = user.getTenantDatabase();
        return TenantContext.callWithTenantDatabase(tenantDatabase, supplier);
    }

    private Map<String, Object> statusResponse(boolean running) {
        Map<String, Object> response = new HashMap<>();
        response.put("running", running);
        response.put("timestamp", OffsetDateTime.now().toString());
        return response;
    }
}
