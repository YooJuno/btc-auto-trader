package com.btcautotrader.engine;

import com.btcautotrader.order.OrderRepository;
import com.btcautotrader.order.OrderRequest;
import com.btcautotrader.order.OrderResponse;
import com.btcautotrader.order.OrderService;
import com.btcautotrader.strategy.StrategyConfig;
import com.btcautotrader.strategy.StrategyService;
import com.btcautotrader.upbit.UpbitService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AutoTradeService {
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final UpbitService upbitService;
    private final OrderService orderService;
    private final StrategyService strategyService;
    private final EngineService engineService;
    private final OrderRepository orderRepository;

    private final String marketsConfig;
    private final BigDecimal minOrderKrw;
    private final long cooldownSeconds;
    private final long pendingWindowMinutes;
    private final long failureBackoffBaseSeconds;
    private final long failureBackoffMaxSeconds;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean backoffActive = new AtomicBoolean(false);
    private volatile OffsetDateTime backoffUntil;
    private volatile int consecutiveFailures;

    public AutoTradeService(
            UpbitService upbitService,
            OrderService orderService,
            StrategyService strategyService,
            EngineService engineService,
            OrderRepository orderRepository,
            @Value("${trading.markets:KRW-BTC}") String marketsConfig,
            @Value("${trading.min-krw:5000}") BigDecimal minOrderKrw,
            @Value("${engine.order-cooldown-seconds:30}") long cooldownSeconds,
            @Value("${orders.pending-window-minutes:30}") long pendingWindowMinutes,
            @Value("${engine.failure-backoff-base-seconds:5}") long failureBackoffBaseSeconds,
            @Value("${engine.failure-backoff-max-seconds:300}") long failureBackoffMaxSeconds
    ) {
        this.upbitService = upbitService;
        this.orderService = orderService;
        this.strategyService = strategyService;
        this.engineService = engineService;
        this.orderRepository = orderRepository;
        this.marketsConfig = marketsConfig;
        this.minOrderKrw = minOrderKrw;
        this.cooldownSeconds = cooldownSeconds;
        this.pendingWindowMinutes = pendingWindowMinutes;
        this.failureBackoffBaseSeconds = failureBackoffBaseSeconds;
        this.failureBackoffMaxSeconds = failureBackoffMaxSeconds;
    }

    @Scheduled(fixedDelayString = "${engine.tick-ms:5000}")
    public void scheduledTick() {
        if (!engineService.isRunning()) {
            return;
        }
        runOnce();
    }

    public AutoTradeResult runOnce() {
        if (!running.compareAndSet(false, true)) {
            return new AutoTradeResult(OffsetDateTime.now().toString(), List.of());
        }

        try {
            OffsetDateTime now = OffsetDateTime.now();
            if (isBackoffActive(now)) {
                return new AutoTradeResult(now.toString(), List.of(
                        new AutoTradeAction("SYSTEM", "SKIP", "backoff", null, null, null, null, null)
                ));
            }

            StrategyConfig config = strategyService.getConfig();
            if (!config.enabled()) {
                return new AutoTradeResult(now.toString(), List.of());
            }

            List<String> markets = parseMarkets(marketsConfig);
            if (markets.isEmpty()) {
                return new AutoTradeResult(now.toString(), List.of());
            }

            Map<String, AccountSnapshot> accounts;
            try {
                accounts = loadAccounts();
            } catch (RuntimeException ex) {
                recordFailure(now);
                return new AutoTradeResult(now.toString(), List.of(
                        new AutoTradeAction("SYSTEM", "ERROR", truncate(ex.getMessage(), 200), null, null, null, null, null)
                ));
            }
            BigDecimal remainingCash = accounts.getOrDefault("KRW", AccountSnapshot.empty()).balance();

            List<AutoTradeAction> actions = new ArrayList<>();
            boolean hadFailure = false;
            for (String market : markets) {
                try {
                    String currency = extractCurrency(market);
                    if (currency == null) {
                        actions.add(new AutoTradeAction(market, "SKIP", "invalid market", null, null, null, null, null));
                        continue;
                    }

                    AccountSnapshot position = accounts.getOrDefault(currency, AccountSnapshot.empty());
                    BigDecimal total = position.total();

                    if (total.compareTo(BigDecimal.ZERO) > 0) {
                        AutoTradeAction action = handleSell(market, position, config);
                        if (action != null) {
                            actions.add(action);
                        }
                        continue;
                    }

                    AutoTradeAction action = handleBuy(market, remainingCash, config);
                    if (action != null) {
                        actions.add(action);
                        if ("BUY".equalsIgnoreCase(action.action()) && action.funds() != null) {
                            remainingCash = remainingCash.subtract(action.funds());
                            if (remainingCash.compareTo(BigDecimal.ZERO) < 0) {
                                remainingCash = BigDecimal.ZERO;
                            }
                        }
                    }
                } catch (RuntimeException ex) {
                    hadFailure = true;
                    recordFailure(now);
                    actions.add(new AutoTradeAction(
                            market,
                            "ERROR",
                            truncate(ex.getMessage(), 200),
                            null,
                            null,
                            null,
                            null,
                            null
                    ));
                }
            }

            if (!hadFailure) {
                resetFailures();
            }
            return new AutoTradeResult(now.toString(), actions);
        } finally {
            running.set(false);
        }
    }

    private AutoTradeAction handleSell(String market, AccountSnapshot position, StrategyConfig config) {
        BigDecimal available = position.balance();
        if (available.compareTo(BigDecimal.ZERO) <= 0) {
            return new AutoTradeAction(market, "SKIP", "no available balance", null, null, null, null, null);
        }

        BigDecimal avgBuyPrice = position.avgBuyPrice();
        if (avgBuyPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return new AutoTradeAction(market, "SKIP", "avg_buy_price missing", null, available, null, null, null);
        }

        BigDecimal currentPrice = fetchCurrentPrice(market);
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return new AutoTradeAction(market, "SKIP", "price unavailable", null, available, null, null, null);
        }

        BigDecimal estimatedValue = currentPrice.multiply(available);
        if (estimatedValue.compareTo(minOrderKrw) < 0) {
            return new AutoTradeAction(market, "SKIP", "below min order", currentPrice, available, estimatedValue, null, null);
        }

        BigDecimal takeProfitThreshold = avgBuyPrice.multiply(percentFactor(config.takeProfitPct()));
        BigDecimal stopLossThreshold = avgBuyPrice.multiply(percentFactor(-config.stopLossPct()));

        if (currentPrice.compareTo(takeProfitThreshold) >= 0) {
            return submitSell(market, available, "take_profit");
        }
        if (currentPrice.compareTo(stopLossThreshold) <= 0) {
            return submitSell(market, available, "stop_loss");
        }

        return new AutoTradeAction(market, "SKIP", "no signal", currentPrice, available, null, null, null);
    }

    private AutoTradeAction handleBuy(String market, BigDecimal cash, StrategyConfig config) {
        BigDecimal maxOrderKrw = BigDecimal.valueOf(config.maxOrderKrw());
        BigDecimal orderFunds = min(cash, maxOrderKrw);
        if (orderFunds.compareTo(minOrderKrw) < 0) {
            return new AutoTradeAction(market, "SKIP", "insufficient cash", null, null, orderFunds, null, null);
        }

        if (hasOpenRequest(market, "BUY")) {
            return new AutoTradeAction(market, "SKIP", "pending", null, null, orderFunds, null, null);
        }
        if (hasRecentOrder(market, "BUY")) {
            return new AutoTradeAction(market, "SKIP", "cooldown", null, null, orderFunds, null, null);
        }

        OrderRequest request = new OrderRequest(market, "BUY", "MARKET", null, null, orderFunds, null);
        OrderResponse response = orderService.create(request);

        return new AutoTradeAction(
                market,
                "BUY",
                "strategy_entry",
                null,
                null,
                orderFunds,
                response.orderId(),
                response.requestStatus()
        );
    }

    private AutoTradeAction submitSell(String market, BigDecimal volume, String reason) {
        if (volume.compareTo(BigDecimal.ZERO) <= 0) {
            return new AutoTradeAction(market, "SKIP", "no volume", null, volume, null, null, null);
        }

        if (hasOpenRequest(market, "SELL")) {
            return new AutoTradeAction(market, "SKIP", "pending", null, volume, null, null, null);
        }
        if (hasRecentOrder(market, "SELL")) {
            return new AutoTradeAction(market, "SKIP", "cooldown", null, volume, null, null, null);
        }

        OrderRequest request = new OrderRequest(market, "SELL", "MARKET", null, volume, null, null);
        OrderResponse response = orderService.create(request);

        return new AutoTradeAction(
                market,
                "SELL",
                reason,
                null,
                volume,
                null,
                response.orderId(),
                response.requestStatus()
        );
    }

    private boolean hasRecentOrder(String market, String side) {
        OffsetDateTime after = OffsetDateTime.now().minusSeconds(cooldownSeconds);
        return orderRepository.existsByMarketAndSideAndRequestedAtAfter(market, side, after);
    }

    private boolean hasOpenRequest(String market, String side) {
        OffsetDateTime after = OffsetDateTime.now().minusMinutes(pendingWindowMinutes);
        return orderRepository.existsByMarketAndSideAndStatusInAndRequestedAtAfter(
                market,
                side,
                List.of(com.btcautotrader.order.OrderStatus.REQUESTED, com.btcautotrader.order.OrderStatus.PENDING),
                after
        );
    }

    private Map<String, AccountSnapshot> loadAccounts() {
        List<Map<String, Object>> accounts = upbitService.fetchAccounts();
        Map<String, AccountSnapshot> byCurrency = new HashMap<>();
        for (Map<String, Object> account : accounts) {
            String currency = asString(account.get("currency"));
            if (currency == null) {
                continue;
            }
            BigDecimal balance = toDecimal(account.get("balance"));
            BigDecimal locked = toDecimal(account.get("locked"));
            BigDecimal avgBuyPrice = toDecimal(account.get("avg_buy_price"));
            byCurrency.put(currency.toUpperCase(), new AccountSnapshot(balance, locked, avgBuyPrice));
        }
        return byCurrency;
    }

    private BigDecimal fetchCurrentPrice(String market) {
        Map<String, Object> ticker = upbitService.fetchTicker(market);
        if (ticker == null) {
            return null;
        }
        return toDecimal(ticker.get("trade_price"));
    }

    private static BigDecimal percentFactor(double percent) {
        BigDecimal pct = BigDecimal.valueOf(percent).divide(HUNDRED, 8, RoundingMode.HALF_UP);
        return BigDecimal.ONE.add(pct);
    }

    private static BigDecimal min(BigDecimal left, BigDecimal right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.compareTo(right) <= 0 ? left : right;
    }

    private static String extractCurrency(String market) {
        if (market == null) {
            return null;
        }
        int idx = market.indexOf('-');
        if (idx < 0 || idx == market.length() - 1) {
            return null;
        }
        return market.substring(idx + 1).trim().toUpperCase();
    }

    private static List<String> parseMarkets(String config) {
        if (config == null || config.isBlank()) {
            return List.of();
        }
        String[] raw = config.split(",");
        List<String> markets = new ArrayList<>();
        for (String item : raw) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                markets.add(trimmed.toUpperCase());
            }
        }
        return markets;
    }

    private static BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private boolean isBackoffActive(OffsetDateTime now) {
        if (!backoffActive.get()) {
            return false;
        }
        OffsetDateTime until = backoffUntil;
        if (until == null || now.isAfter(until)) {
            backoffActive.set(false);
            backoffUntil = null;
            return false;
        }
        return true;
    }

    private void recordFailure(OffsetDateTime now) {
        int failures = ++consecutiveFailures;
        long delay = failureBackoffBaseSeconds;
        for (int i = 1; i < failures; i++) {
            delay = Math.min(failureBackoffMaxSeconds, delay * 2);
        }
        backoffUntil = now.plusSeconds(delay);
        backoffActive.set(true);
    }

    private void resetFailures() {
        consecutiveFailures = 0;
        backoffActive.set(false);
        backoffUntil = null;
    }

    private record AccountSnapshot(BigDecimal balance, BigDecimal locked, BigDecimal avgBuyPrice) {
        BigDecimal total() {
            return balance.add(locked);
        }

        static AccountSnapshot empty() {
            return new AccountSnapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }
}
