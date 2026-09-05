package com.btcautotrader.engine;

import com.btcautotrader.auth.TradingAccessService;
import com.btcautotrader.auth.CurrentUserService;
import com.btcautotrader.auth.UserEntity;
import com.btcautotrader.paper.TradingAccountService;
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
    private final TradingAccountService tradingAccountService;

    public EngineController(
            EngineService engineService,
            AutoTradeService autoTradeService,
            TradeDecisionService tradeDecisionService,
            TradingAccessService tradingAccessService,
            CurrentUserService currentUserService,
            TradingAccountService tradingAccountService
    ) {
        this.tradingAccountService = tradingAccountService;
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
        tradingAccessService.requireTenantReadAllowed(authentication);
        boolean running = callInUserTenant(authentication, engineService::isRunning);
        return ResponseEntity.ok(statusResponse(running));
    }

    @GetMapping("/decisions")
    public ResponseEntity<List<TradeDecisionItem>> decisions(
            Authentication authentication,
            @RequestParam(name = "limit", defaultValue = "30") int limit,
            @RequestParam(name = "includeSkips", defaultValue = "true") boolean includeSkips
    ) {
        tradingAccessService.requireTenantReadAllowed(authentication);
        List<TradeDecisionItem> items = callInUserTenant(
                authentication,
                () -> tradeDecisionService.listRecent(limit, includeSkips)
        );
        return ResponseEntity.ok(items);
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stop(Authentication authentication) {
        tradingAccessService.requireEngineExecutionAllowed(authentication);
        boolean running = callInUserTenant(authentication, engineService::stop);
        return ResponseEntity.ok(statusResponse(running));
    }

    /**
     * Kill switch: stop the engine AND flatten every position, in that order.
     *
     * Plain /stop only halts decision-making; it leaves open positions with nothing evaluating their
     * stop-loss. Stopping first means the tick cannot re-enter a market while the liquidation runs.
     */
    @PostMapping("/panic")
    public ResponseEntity<Map<String, Object>> panic(Authentication authentication) {
        tradingAccessService.requireEngineExecutionAllowed(authentication);
        UserEntity user = TenantContext.callWithTenantDatabase(null, () -> currentUserService.requireUser(authentication));
        String tenantDatabase = tradingAccessService.requireTenantDatabase(user);

        AutoTradeResult result = TenantContext.callWithTenantDatabase(tenantDatabase, () -> {
            engineService.stop();
            return autoTradeService.liquidateAll("panic_exit");
        });

        long submitted = result.actions().stream()
                .filter(action -> "SELL".equalsIgnoreCase(action.action()))
                .count();

        Map<String, Object> response = new HashMap<>();
        response.put("running", false);
        response.put("timestamp", OffsetDateTime.now().toString());
        response.put("liquidationsSubmitted", submitted);
        response.put("actions", result.actions());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/tick")
    public ResponseEntity<AutoTradeResult> tick(
            Authentication authentication,
            @RequestParam(name = "force", defaultValue = "false") boolean force
    ) {
        tradingAccessService.requireEngineExecutionAllowed(authentication);
        UserEntity user = TenantContext.callWithTenantDatabase(null, () -> currentUserService.requireUser(authentication));
        String tenantDatabase = tradingAccessService.requireTenantDatabase(user);
        AutoTradeResult result = TenantContext.callWithTenantDatabase(tenantDatabase, () -> {
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
            return autoTradeService.runOnce(user.getId());
        });
        if (!force && result.actions().size() == 1 && "engine_stopped".equals(result.actions().get(0).reason())) {
            return ResponseEntity.status(409).body(result);
        }
        return ResponseEntity.ok(result);
    }

    private <T> T callInUserTenant(Authentication authentication, Supplier<T> supplier) {
        UserEntity user = TenantContext.callWithTenantDatabase(null, () -> currentUserService.requireUser(authentication));
        String tenantDatabase = tradingAccessService.requireTenantDatabase(user);
        return TenantContext.callWithTenantDatabase(tenantDatabase, supplier);
    }

    private Map<String, Object> statusResponse(boolean running) {
        Map<String, Object> response = new HashMap<>();
        response.put("running", running);
        // Rides on the status poll the dashboard already makes, so the UI can never be uncertain about
        // whether it is showing real money.
        response.put("tradingMode", tradingAccountService.isPaperMode() ? "PAPER" : "LIVE");
        response.put("timestamp", OffsetDateTime.now().toString());
        return response;
    }
}
