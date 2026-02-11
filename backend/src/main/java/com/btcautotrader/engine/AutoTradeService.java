package com.btcautotrader.engine;

import com.btcautotrader.order.OrderRepository;
import com.btcautotrader.order.OrderRequest;
import com.btcautotrader.order.OrderResponse;
import com.btcautotrader.order.OrderService;
import com.btcautotrader.strategy.StrategyConfig;
import com.btcautotrader.strategy.StrategyMarketOverrides;
import com.btcautotrader.strategy.StrategyMarketRatios;
import com.btcautotrader.strategy.StrategyProfile;
import com.btcautotrader.strategy.StrategyService;
import com.btcautotrader.upbit.UpbitService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AutoTradeService {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal RELATIVE_MOMENTUM_LONG_WEIGHT = new BigDecimal("0.6");
    private static final BigDecimal RELATIVE_MOMENTUM_SHORT_WEIGHT = new BigDecimal("0.4");
    private static final String SYSTEM_KEY = "SYSTEM";

    private final UpbitService upbitService;
    private final OrderService orderService;
    private final StrategyService strategyService;
    private final EngineService engineService;
    private final OrderRepository orderRepository;
    private final TradeDecisionRepository tradeDecisionRepository;
    private final TradeDecisionService tradeDecisionService;

    private final Map<String, BigDecimal> propertyMarketMaxOrderKrwOverrides;
    private final Map<String, StrategyProfile> propertyMarketProfileOverrides;
    private final BigDecimal minOrderKrw;
    private final BigDecimal feeRate;
    private final BigDecimal slippagePct;
    private final BigDecimal tradeCostRate;
    private final long cooldownSeconds;
    private final long pendingWindowMinutes;
    private final long failureBackoffBaseSeconds;
    private final long failureBackoffMaxSeconds;
    private final int maxMarketsPerTick;
    private final int candleUnitMinutes;
    private final int maShort;
    private final int maLong;
    private final int rsiPeriod;
    private final double rsiBuyThreshold;
    private final double rsiSellThreshold;
    private final double rsiOverbought;
    private final int macdFast;
    private final int macdSlow;
    private final int macdSignal;
    private final int adxPeriod;
    private final double minAdx;
    private final int volumeLookback;
    private final double minVolumeRatio;
    private final int bollingerWindow;
    private final double bollingerStdDev;
    private final double bollingerMinBandwidthPct;
    private final double bollingerMaxPercentB;
    private final int breakoutLookback;
    private final double breakoutPct;
    private final double maxExtensionPct;
    private final int maLongSlopeLookback;
    private final int minConfirmations;
    private final int trailingWindow;
    private final long partialTakeProfitCooldownMinutes;
    private final long stopLossCooldownMinutes;
    private final long reentryCooldownMinutes;
    private final long stopLossGuardLookbackMinutes;
    private final int stopLossGuardTriggerCount;
    private final long stopLossGuardLockMinutes;
    private final int volatilityWindow;
    private final BigDecimal targetVolPct;
    private final boolean useClosedCandle;
    private final boolean regimeFilterEnabled;
    private final boolean regimeFilterPerMarket;
    private final String regimeMarket;
    private final int regimeTimeframeUnit;
    private final int regimeMaShort;
    private final int regimeMaLong;
    private final int regimeSlopeLookback;
    private final double regimeMinMaLongSlopePct;
    private final int regimeVolatilityWindow;
    private final BigDecimal regimeMaxVolatilityPct;
    private final boolean relativeMomentumEnabled;
    private final int relativeMomentumTimeframeUnit;
    private final int relativeMomentumShortLookback;
    private final int relativeMomentumLongLookback;
    private final int relativeMomentumTopN;
    private final double relativeMomentumMinScorePct;
    private final long relativeMomentumCacheMinutes;
    private final long orderChanceCacheMinutes;
    private final int stateRestoreLimit;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger marketCursor = new AtomicInteger(0);
    private final Map<String, BackoffState> backoffStates = new ConcurrentHashMap<>();
    private final Map<String, OffsetDateTime> lastPartialTakeProfitAt = new ConcurrentHashMap<>();
    private final Map<String, OffsetDateTime> lastStopLossAt = new ConcurrentHashMap<>();
    private final Map<String, OffsetDateTime> lastExitAt = new ConcurrentHashMap<>();
    private final Map<String, Deque<OffsetDateTime>> stopLossEventsByMarket = new ConcurrentHashMap<>();
    private final Map<String, OffsetDateTime> stopLossGuardUntilByMarket = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> trailingHighByMarket = new ConcurrentHashMap<>();
    private final Map<String, OrderChanceSnapshot> orderChanceCache = new ConcurrentHashMap<>();
    private final Map<String, MomentumSnapshot> relativeMomentumCache = new ConcurrentHashMap<>();

    public AutoTradeService(
            UpbitService upbitService,
            OrderService orderService,
            StrategyService strategyService,
            EngineService engineService,
            OrderRepository orderRepository,
            TradeDecisionRepository tradeDecisionRepository,
            TradeDecisionService tradeDecisionService,
            @Value("${trading.market-max-order-krw:}") String marketMaxOrderKrwConfig,
            @Value("${trading.market-profile:}") String marketProfileConfig,
            @Value("${trading.min-krw:5000}") BigDecimal minOrderKrw,
            @Value("${trading.fee-rate:0.0005}") BigDecimal feeRate,
            @Value("${trading.slippage-pct:0.001}") BigDecimal slippagePct,
            @Value("${engine.order-cooldown-seconds:30}") long cooldownSeconds,
            @Value("${orders.pending-window-minutes:30}") long pendingWindowMinutes,
            @Value("${engine.failure-backoff-base-seconds:5}") long failureBackoffBaseSeconds,
            @Value("${engine.failure-backoff-max-seconds:300}") long failureBackoffMaxSeconds,
            @Value("${engine.max-markets-per-tick:0}") int maxMarketsPerTick,
            @Value("${signal.timeframe-unit:1}") int candleUnitMinutes,
            @Value("${signal.ma-short:20}") int maShort,
            @Value("${signal.ma-long:100}") int maLong,
            @Value("${signal.rsi-period:14}") int rsiPeriod,
            @Value("${signal.rsi-buy-threshold:55}") double rsiBuyThreshold,
            @Value("${signal.rsi-sell-threshold:45}") double rsiSellThreshold,
            @Value("${signal.rsi-overbought:70}") double rsiOverbought,
            @Value("${signal.macd-fast:12}") int macdFast,
            @Value("${signal.macd-slow:26}") int macdSlow,
            @Value("${signal.macd-signal:9}") int macdSignal,
            @Value("${signal.adx-period:14}") int adxPeriod,
            @Value("${signal.min-adx:18}") double minAdx,
            @Value("${signal.volume-lookback:20}") int volumeLookback,
            @Value("${signal.min-volume-ratio:0.8}") double minVolumeRatio,
            @Value("${signal.bollinger.window:20}") int bollingerWindow,
            @Value("${signal.bollinger.stddev:2.0}") double bollingerStdDev,
            @Value("${signal.bollinger.min-bandwidth-pct:0.6}") double bollingerMinBandwidthPct,
            @Value("${signal.bollinger.max-percent-b:1.05}") double bollingerMaxPercentB,
            @Value("${signal.breakout-lookback:20}") int breakoutLookback,
            @Value("${signal.breakout-pct:0.3}") double breakoutPct,
            @Value("${signal.max-extension-pct:1.2}") double maxExtensionPct,
            @Value("${signal.ma-long-slope-lookback:5}") int maLongSlopeLookback,
            @Value("${signal.min-confirmations:2}") int minConfirmations,
            @Value("${risk.trailing-window:20}") int trailingWindow,
            @Value("${risk.partial-take-profit-cooldown-minutes:120}") long partialTakeProfitCooldownMinutes,
            @Value("${risk.stop-loss-cooldown-minutes:30}") long stopLossCooldownMinutes,
            @Value("${risk.reentry-cooldown-minutes:15}") long reentryCooldownMinutes,
            @Value("${risk.stop-loss-guard-lookback-minutes:180}") long stopLossGuardLookbackMinutes,
            @Value("${risk.stop-loss-guard-trigger-count:3}") int stopLossGuardTriggerCount,
            @Value("${risk.stop-loss-guard-lock-minutes:180}") long stopLossGuardLockMinutes,
            @Value("${risk.volatility-window:30}") int volatilityWindow,
            @Value("${risk.target-vol-pct:0.5}") BigDecimal targetVolPct,
            @Value("${signal.use-closed-candle:true}") boolean useClosedCandle,
            @Value("${regime.filter.enabled:true}") boolean regimeFilterEnabled,
            @Value("${regime.filter.per-market:false}") boolean regimeFilterPerMarket,
            @Value("${regime.filter.market:KRW-BTC}") String regimeMarket,
            @Value("${regime.filter.timeframe-unit:15}") int regimeTimeframeUnit,
            @Value("${regime.filter.ma-short:40}") int regimeMaShort,
            @Value("${regime.filter.ma-long:120}") int regimeMaLong,
            @Value("${regime.filter.ma-long-slope-lookback:5}") int regimeSlopeLookback,
            @Value("${regime.filter.min-ma-long-slope-pct:0.0}") double regimeMinMaLongSlopePct,
            @Value("${regime.filter.volatility-window:48}") int regimeVolatilityWindow,
            @Value("${regime.filter.max-volatility-pct:1.2}") BigDecimal regimeMaxVolatilityPct,
            @Value("${signal.relative-momentum.enabled:true}") boolean relativeMomentumEnabled,
            @Value("${signal.relative-momentum.timeframe-unit:15}") int relativeMomentumTimeframeUnit,
            @Value("${signal.relative-momentum.short-lookback:24}") int relativeMomentumShortLookback,
            @Value("${signal.relative-momentum.long-lookback:96}") int relativeMomentumLongLookback,
            @Value("${signal.relative-momentum.top-n:3}") int relativeMomentumTopN,
            @Value("${signal.relative-momentum.min-score-pct:0.0}") double relativeMomentumMinScorePct,
            @Value("${signal.relative-momentum.cache-minutes:5}") long relativeMomentumCacheMinutes,
            @Value("${orders.chance-cache-minutes:5}") long orderChanceCacheMinutes,
            @Value("${engine.state-restore-limit:500}") int stateRestoreLimit
    ) {
        this.upbitService = upbitService;
        this.orderService = orderService;
        this.strategyService = strategyService;
        this.engineService = engineService;
        this.orderRepository = orderRepository;
        this.tradeDecisionRepository = tradeDecisionRepository;
        this.tradeDecisionService = tradeDecisionService;
        this.propertyMarketMaxOrderKrwOverrides = Map.copyOf(parseMarketMaxOrderKrwOverrides(marketMaxOrderKrwConfig));
        this.propertyMarketProfileOverrides = Map.copyOf(parseMarketProfileOverrides(marketProfileConfig));
        this.minOrderKrw = minOrderKrw;
        this.feeRate = normalizeRate(feeRate);
        this.slippagePct = normalizeRate(slippagePct);
        BigDecimal combinedRate = this.feeRate.add(this.slippagePct);
        this.tradeCostRate = combinedRate.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : combinedRate;
        this.cooldownSeconds = cooldownSeconds;
        this.pendingWindowMinutes = pendingWindowMinutes;
        this.failureBackoffBaseSeconds = failureBackoffBaseSeconds;
        this.failureBackoffMaxSeconds = failureBackoffMaxSeconds;
        this.maxMarketsPerTick = maxMarketsPerTick;
        this.candleUnitMinutes = candleUnitMinutes;
        this.maShort = maShort;
        this.maLong = maLong;
        this.rsiPeriod = rsiPeriod;
        this.rsiBuyThreshold = rsiBuyThreshold;
        this.rsiSellThreshold = rsiSellThreshold;
        this.rsiOverbought = rsiOverbought;
        this.macdFast = macdFast;
        this.macdSlow = macdSlow;
        this.macdSignal = macdSignal;
        this.adxPeriod = adxPeriod;
        this.minAdx = minAdx;
        this.volumeLookback = volumeLookback;
        this.minVolumeRatio = minVolumeRatio;
        this.bollingerWindow = Math.max(0, Math.min(bollingerWindow, 200));
        this.bollingerStdDev = Math.max(0.1, Math.min(bollingerStdDev, 6.0));
        this.bollingerMinBandwidthPct = Math.max(0.0, bollingerMinBandwidthPct);
        this.bollingerMaxPercentB = bollingerMaxPercentB;
        this.breakoutLookback = breakoutLookback;
        this.breakoutPct = breakoutPct;
        this.maxExtensionPct = maxExtensionPct;
        this.maLongSlopeLookback = maLongSlopeLookback;
        this.minConfirmations = minConfirmations;
        this.trailingWindow = trailingWindow;
        this.partialTakeProfitCooldownMinutes = partialTakeProfitCooldownMinutes;
        this.stopLossCooldownMinutes = stopLossCooldownMinutes;
        this.reentryCooldownMinutes = reentryCooldownMinutes;
        this.stopLossGuardLookbackMinutes = stopLossGuardLookbackMinutes;
        this.stopLossGuardTriggerCount = stopLossGuardTriggerCount;
        this.stopLossGuardLockMinutes = stopLossGuardLockMinutes;
        this.volatilityWindow = volatilityWindow;
        this.targetVolPct = targetVolPct;
        this.useClosedCandle = useClosedCandle;
        this.regimeFilterEnabled = regimeFilterEnabled;
        this.regimeFilterPerMarket = regimeFilterPerMarket;
        this.regimeMarket = normalizeMarket(regimeMarket, "KRW-BTC");
        this.regimeTimeframeUnit = Math.max(1, regimeTimeframeUnit);
        this.regimeMaShort = Math.max(2, Math.min(regimeMaShort, 199));
        this.regimeMaLong = Math.max(this.regimeMaShort + 1, Math.min(regimeMaLong, 200));
        this.regimeSlopeLookback = Math.max(0, Math.min(regimeSlopeLookback, 60));
        this.regimeMinMaLongSlopePct = regimeMinMaLongSlopePct;
        this.regimeVolatilityWindow = Math.max(2, Math.min(regimeVolatilityWindow, 200));
        this.regimeMaxVolatilityPct = regimeMaxVolatilityPct;
        this.relativeMomentumEnabled = relativeMomentumEnabled;
        this.relativeMomentumTimeframeUnit = Math.max(1, relativeMomentumTimeframeUnit);
        int maxMomentumLookback = useClosedCandle ? 198 : 199;
        this.relativeMomentumShortLookback = Math.max(1, Math.min(relativeMomentumShortLookback, maxMomentumLookback - 1));
        this.relativeMomentumLongLookback = Math.max(
                this.relativeMomentumShortLookback + 1,
                Math.min(relativeMomentumLongLookback, maxMomentumLookback)
        );
        this.relativeMomentumTopN = relativeMomentumTopN;
        this.relativeMomentumMinScorePct = relativeMomentumMinScorePct;
        this.relativeMomentumCacheMinutes = Math.max(0, relativeMomentumCacheMinutes);
        this.orderChanceCacheMinutes = Math.max(0, orderChanceCacheMinutes);
        this.stateRestoreLimit = Math.max(0, stateRestoreLimit);
    }

    @PostConstruct
    void restoreExitState() {
        if (tradeDecisionRepository == null || stateRestoreLimit <= 0) {
            return;
        }
        List<TradeDecisionEntity> decisions = tradeDecisionRepository.findByActionOrderByExecutedAtDesc(
                "SELL",
                PageRequest.of(0, stateRestoreLimit)
        ).getContent();
        if (decisions.isEmpty()) {
            return;
        }

        OffsetDateTime stopLossThreshold = null;
        if (stopLossGuardLookbackMinutes > 0) {
            stopLossThreshold = OffsetDateTime.now().minusMinutes(stopLossGuardLookbackMinutes);
        }

        Map<String, List<OffsetDateTime>> stopEvents = new HashMap<>();

        for (TradeDecisionEntity decision : decisions) {
            if (decision == null || decision.getExecutedAt() == null) {
                continue;
            }
            String market = normalizeMarketKey(decision.getMarket());
            if (market == null) {
                continue;
            }

            if (!lastExitAt.containsKey(market)) {
                lastExitAt.put(market, decision.getExecutedAt());
            }

            String reason = decision.getReason();
            if (!isStopLikeReason(reason)) {
                continue;
            }
            if (stopLossThreshold != null && decision.getExecutedAt().isBefore(stopLossThreshold)) {
                continue;
            }
            stopEvents.computeIfAbsent(market, key -> new ArrayList<>()).add(decision.getExecutedAt());
        }

        for (Map.Entry<String, List<OffsetDateTime>> entry : stopEvents.entrySet()) {
            List<OffsetDateTime> events = entry.getValue();
            if (events == null || events.isEmpty()) {
                continue;
            }
            events.sort(OffsetDateTime::compareTo);
            for (OffsetDateTime occurredAt : events) {
                registerStopLossEvent(entry.getKey(), occurredAt);
            }
            lastStopLossAt.put(entry.getKey(), events.get(events.size() - 1));
        }
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
            if (isBackoffActive(SYSTEM_KEY, now)) {
                AutoTradeAction action = new AutoTradeAction(SYSTEM_KEY, "SKIP", "backoff", null, null, null, null, null);
                recordDecision(SYSTEM_KEY, action, null, null, null, null, null, null, null);
                return new AutoTradeResult(now.toString(), List.of(action));
            }

            StrategyConfig config = strategyService.getConfig();
            if (!config.enabled()) {
                return new AutoTradeResult(now.toString(), List.of());
            }
            StrategyMarketOverrides runtimeOverrides = strategyService.getMarketOverridesSnapshot();
            Map<String, BigDecimal> marketMaxOrderKrwByMarket = mergeMarketMaxOrderKrwOverrides(
                    propertyMarketMaxOrderKrwOverrides,
                    runtimeOverrides
            );
            Map<String, StrategyProfile> marketProfileByMarket = mergeMarketProfileOverrides(
                    propertyMarketProfileOverrides,
                    runtimeOverrides
            );
            Map<String, Boolean> tradePausedByMarket = mergeMarketTradePausedOverrides(runtimeOverrides);
            List<String> markets = strategyService.configuredMarkets();
            if (markets.isEmpty()) {
                return new AutoTradeResult(now.toString(), List.of());
            }

            Map<String, AccountSnapshot> accounts;
            try {
                accounts = loadAccounts();
                resetFailure(SYSTEM_KEY);
            } catch (RuntimeException ex) {
                recordFailure(SYSTEM_KEY, now);
                AutoTradeAction action = new AutoTradeAction(
                        SYSTEM_KEY,
                        "ERROR",
                        truncate(ex.getMessage(), 200),
                        null,
                        null,
                        null,
                        null,
                        null
                );
                recordDecision(SYSTEM_KEY, action, null, null, null, null, null, null, null);
                return new AutoTradeResult(now.toString(), List.of(action));
            }
            RegimeSnapshot globalRegime = null;
            Map<String, RegimeSnapshot> regimeByMarket = new HashMap<>();
            if (!regimeFilterPerMarket) {
                globalRegime = evaluateRegime(regimeMarket);
            }
            MarketSelection selection = selectMarketsForTick(markets, accounts);
            BigDecimal remainingCash = accounts.getOrDefault("KRW", AccountSnapshot.empty()).balance();

            List<AutoTradeAction> actions = new ArrayList<>();
            for (String market : selection.selected()) {
                RegimeSnapshot regime = regimeFilterPerMarket
                        ? regimeByMarket.computeIfAbsent(normalizeMarket(market, regimeMarket), this::evaluateRegime)
                        : globalRegime;
                StrategyConfig marketConfig = resolveConfigForMarket(market, config, runtimeOverrides);
                StrategyProfile profile = resolveProfileForMarket(market, marketConfig, marketProfileByMarket);
                SignalTuning tuning = resolveSignalTuning(profile);
                BigDecimal marketMaxOrderKrw = resolveMarketMaxOrderKrw(market, marketConfig, marketMaxOrderKrwByMarket);
                BigDecimal momentumScorePct = selection.momentumScorePctByMarket().get(market);
                boolean tradePaused = Boolean.TRUE.equals(tradePausedByMarket.get(market));

                if (isBackoffActive(market, now)) {
                    AutoTradeAction action = new AutoTradeAction(market, "SKIP", "backoff", null, null, null, null, null);
                    actions.add(action);
                    recordDecision(market, action, marketConfig, profile, null, tuning, regime, momentumScorePct, marketMaxOrderKrw);
                    continue;
                }

                MarketIndicators indicators = null;
                try {
                    String currency = extractCurrency(market);
                    if (currency == null) {
                        actions.add(new AutoTradeAction(market, "SKIP", "invalid market", null, null, null, null, null));
                        continue;
                    }

                    AccountSnapshot position = accounts.getOrDefault(currency, AccountSnapshot.empty());
                    BigDecimal total = position.total();

                    if (total.compareTo(BigDecimal.ZERO) <= 0) {
                        lastPartialTakeProfitAt.remove(market);
                        String marketKey = normalizeMarketKey(market);
                        if (marketKey != null) {
                            trailingHighByMarket.remove(marketKey);
                        }
                        if (tradePaused) {
                            AutoTradeAction action = new AutoTradeAction(
                                    market,
                                    "SKIP",
                                    "market_paused",
                                    null,
                                    null,
                                    null,
                                    null,
                                    null
                            );
                            actions.add(action);
                            recordDecision(
                                    market,
                                    action,
                                    marketConfig,
                                    profile,
                                    null,
                                    tuning,
                                    regime,
                                    momentumScorePct,
                                    marketMaxOrderKrw
                            );
                            resetFailure(market);
                            continue;
                        }
                        if (regime != null && !regime.allowEntries()) {
                            AutoTradeAction action = new AutoTradeAction(
                                    market,
                                    "SKIP",
                                    "risk_off_regime:" + regime.reason(),
                                    null,
                                    null,
                                    null,
                                    null,
                                    null
                            );
                            actions.add(action);
                            recordDecision(
                                    market,
                                    action,
                                    marketConfig,
                                    profile,
                                    null,
                                    tuning,
                                    regime,
                                    momentumScorePct,
                                    marketMaxOrderKrw
                            );
                            resetFailure(market);
                            continue;
                        }
                    }

                    indicators = fetchIndicators(market, tuning);

                    if (total.compareTo(BigDecimal.ZERO) > 0) {
                        AutoTradeAction sellAction = handleSell(market, position, marketConfig, indicators, tuning);
                        AutoTradeAction actionToRecord = sellAction;

                        if (!tradePaused
                                && canScaleInAfterSellAction(sellAction)
                                && remainingCash.compareTo(BigDecimal.ZERO) > 0) {
                            AutoTradeAction buyAction = handleBuy(
                                    market,
                                    remainingCash,
                                    marketConfig,
                                    indicators,
                                    tuning,
                                    marketMaxOrderKrw
                            );
                            if (buyAction != null && "BUY".equalsIgnoreCase(buyAction.action())) {
                                actionToRecord = buyAction;
                            }
                        }

                        if (actionToRecord != null) {
                            actions.add(actionToRecord);
                            recordDecision(
                                    market,
                                    actionToRecord,
                                    marketConfig,
                                    profile,
                                    indicators,
                                    tuning,
                                    regime,
                                    momentumScorePct,
                                    marketMaxOrderKrw
                            );
                            if ("BUY".equalsIgnoreCase(actionToRecord.action()) && actionToRecord.funds() != null) {
                                remainingCash = remainingCash.subtract(actionToRecord.funds());
                                if (remainingCash.compareTo(BigDecimal.ZERO) < 0) {
                                    remainingCash = BigDecimal.ZERO;
                                }
                            }
                        }
                        resetFailure(market);
                        continue;
                    }

                    AutoTradeAction action = handleBuy(market, remainingCash, marketConfig, indicators, tuning, marketMaxOrderKrw);
                    if (action != null) {
                        actions.add(action);
                        recordDecision(
                                market,
                                action,
                                marketConfig,
                                profile,
                                indicators,
                                tuning,
                                regime,
                                momentumScorePct,
                                marketMaxOrderKrw
                        );
                        if ("BUY".equalsIgnoreCase(action.action()) && action.funds() != null) {
                            remainingCash = remainingCash.subtract(action.funds());
                            if (remainingCash.compareTo(BigDecimal.ZERO) < 0) {
                                remainingCash = BigDecimal.ZERO;
                            }
                        }
                    }
                    resetFailure(market);
                } catch (RuntimeException ex) {
                    recordFailure(market, now);
                    AutoTradeAction action = new AutoTradeAction(
                            market,
                            "ERROR",
                            truncate(ex.getMessage(), 200),
                            null,
                            null,
                            null,
                            null,
                            null
                    );
                    actions.add(action);
                    recordDecision(
                            market,
                            action,
                            marketConfig,
                            profile,
                            indicators,
                            tuning,
                            regime,
                            momentumScorePct,
                            marketMaxOrderKrw
                    );
                }
            }

            for (Map.Entry<String, String> deferred : selection.deferredReasonsByMarket().entrySet()) {
                String market = deferred.getKey();
                RegimeSnapshot regime = regimeFilterPerMarket
                        ? regimeByMarket.computeIfAbsent(normalizeMarket(market, regimeMarket), this::evaluateRegime)
                        : globalRegime;
                StrategyConfig marketConfig = resolveConfigForMarket(market, config, runtimeOverrides);
                StrategyProfile profile = resolveProfileForMarket(market, marketConfig, marketProfileByMarket);
                SignalTuning tuning = resolveSignalTuning(profile);
                BigDecimal marketMaxOrderKrw = resolveMarketMaxOrderKrw(market, marketConfig, marketMaxOrderKrwByMarket);
                BigDecimal momentumScorePct = selection.momentumScorePctByMarket().get(market);
                AutoTradeAction action = new AutoTradeAction(
                        market,
                        "SKIP",
                        deferred.getValue(),
                        null,
                        null,
                        null,
                        null,
                        null
                );
                actions.add(action);
                recordDecision(
                        market,
                        action,
                        marketConfig,
                        profile,
                        null,
                        tuning,
                        regime,
                        momentumScorePct,
                        marketMaxOrderKrw
                );
            }
            return new AutoTradeResult(now.toString(), actions);
        } finally {
            running.set(false);
        }
    }

    private SignalTuning resolveSignalTuning(StrategyProfile profile) {
        double rsiBuy = rsiBuyThreshold;
        double rsiSell = rsiSellThreshold;
        double rsiOver = rsiOverbought;
        double minAdxThreshold = minAdx;
        double minVolumeRatioThreshold = minVolumeRatio;
        double breakout = breakoutPct;
        double maxExtension = maxExtensionPct;
        double minSlope = 0.0;
        int confirmations = minConfirmations;

        switch (profile) {
            case AGGRESSIVE -> {
                rsiBuy -= 5.0;
                rsiSell -= 5.0;
                rsiOver += 10.0;
                minAdxThreshold -= 4.0;
                minVolumeRatioThreshold -= 0.15;
                breakout *= 0.5;
                maxExtension *= 1.5;
                minSlope = -0.1;
                confirmations = minConfirmations - 1;
            }
            case CONSERVATIVE -> {
                rsiBuy += 5.0;
                rsiSell += 5.0;
                rsiOver -= 5.0;
                minAdxThreshold += 4.0;
                minVolumeRatioThreshold += 0.15;
                breakout *= 1.5;
                maxExtension *= 0.7;
                minSlope = 0.05;
                confirmations = minConfirmations + 1;
            }
            case BALANCED -> {
            }
        }

        rsiBuy = clamp(rsiBuy, 40.0, 80.0);
        rsiSell = clamp(rsiSell, 30.0, 70.0);
        rsiOver = clamp(rsiOver, 60.0, 90.0);
        minAdxThreshold = clamp(minAdxThreshold, 5.0, 60.0);
        minVolumeRatioThreshold = clamp(minVolumeRatioThreshold, 0.1, 3.0);
        breakout = clamp(breakout, 0.05, 3.0);
        maxExtension = clamp(maxExtension, 0.2, 5.0);
        minSlope = clamp(minSlope, -0.5, 1.0);
        confirmations = clamp(confirmations, 1, 3);

        return new SignalTuning(
                rsiBuy,
                rsiSell,
                rsiOver,
                minAdxThreshold,
                minVolumeRatioThreshold,
                breakout,
                confirmations,
                maxExtension,
                minSlope
        );
    }

    private BigDecimal updateTrailingHigh(
            String market,
            BigDecimal avgBuyPrice,
            BigDecimal currentPrice,
            BigDecimal windowHigh
    ) {
        String marketKey = normalizeMarketKey(market);
        if (marketKey == null) {
            return null;
        }
        BigDecimal candidate = maxPositive(avgBuyPrice, currentPrice, windowHigh);
        if (candidate == null) {
            return trailingHighByMarket.get(marketKey);
        }
        return trailingHighByMarket.compute(marketKey, (key, previous) -> {
            if (previous == null || previous.compareTo(BigDecimal.ZERO) <= 0) {
                return candidate;
            }
            return candidate.compareTo(previous) > 0 ? candidate : previous;
        });
    }

    private static BigDecimal maxPositive(BigDecimal... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        BigDecimal max = null;
        for (BigDecimal value : values) {
            if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (max == null || value.compareTo(max) > 0) {
                max = value;
            }
        }
        return max;
    }

    private AutoTradeAction handleSell(
            String market,
            AccountSnapshot position,
            StrategyConfig config,
            MarketIndicators indicators,
            SignalTuning tuning
    ) {
        BigDecimal available = position.balance();
        if (available.compareTo(BigDecimal.ZERO) <= 0) {
            return new AutoTradeAction(market, "SKIP", "no available balance", null, null, null, null, null);
        }

        BigDecimal avgBuyPrice = position.avgBuyPrice();
        if (avgBuyPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return new AutoTradeAction(market, "SKIP", "avg_buy_price missing", null, available, null, null, null);
        }

        BigDecimal currentPrice = indicators == null ? null : indicators.currentPrice();
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            currentPrice = fetchCurrentPrice(market);
        }
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return new AutoTradeAction(market, "SKIP", "price unavailable", null, available, null, null, null);
        }

        BigDecimal minTotal = resolveMinOrderKrw(market, "SELL");
        BigDecimal estimatedValue = currentPrice.multiply(available);
        if (estimatedValue.compareTo(minTotal) < 0) {
            return new AutoTradeAction(market, "SKIP", "below min order", currentPrice, available, estimatedValue, null, null);
        }

        BigDecimal takeProfitThreshold = avgBuyPrice.multiply(percentFactor(config.takeProfitPct()));
        BigDecimal stopLossThreshold = avgBuyPrice.multiply(percentFactor(-config.stopLossPct()));
        BigDecimal trailingStopThreshold = null;
        BigDecimal entryTrailingHigh = updateTrailingHigh(
                market,
                avgBuyPrice,
                currentPrice,
                indicators == null ? null : indicators.trailingHigh()
        );
        if (config.trailingStopPct() > 0 && entryTrailingHigh != null) {
            trailingStopThreshold = entryTrailingHigh.multiply(percentFactor(-config.trailingStopPct()));
        }

        if (currentPrice.compareTo(stopLossThreshold) <= 0) {
            return submitSellByPct(market, available, currentPrice, config.stopExitPct(), "stop_loss", true, minTotal);
        }
        if (trailingStopThreshold != null && currentPrice.compareTo(trailingStopThreshold) <= 0) {
            return submitSellByPct(market, available, currentPrice, config.stopExitPct(), "trailing_stop", true, minTotal);
        }
        if (indicators != null
                && indicators.macdHistogram() != null
                && indicators.rsi() != null
                && indicators.macdHistogram().compareTo(BigDecimal.ZERO) < 0
                && indicators.rsi().doubleValue() < tuning.rsiSellThreshold()) {
            return submitSellByPct(market, available, currentPrice, config.momentumExitPct(), "momentum_reversal", false, minTotal);
        }
        if (currentPrice.compareTo(takeProfitThreshold) >= 0) {
            double partialPct = config.partialTakeProfitPct();
            if (partialPct > 0 && partialPct < 100) {
                OffsetDateTime now = OffsetDateTime.now();
                if (!canTakePartialProfit(market, now)) {
                    return new AutoTradeAction(market, "SKIP", "take_profit_hold", currentPrice, available, null, null, null);
                }
                AutoTradeAction partial = attemptPartialTakeProfit(market, available, currentPrice, partialPct, minTotal);
                if (partial != null) {
                    return partial;
                }
            }
            return submitSell(market, available, "take_profit");
        }
        if (indicators != null && indicators.maLong() != null && currentPrice.compareTo(indicators.maLong()) < 0) {
            return submitSellByPct(market, available, currentPrice, config.trendExitPct(), "trend_break", false, minTotal);
        }

        return new AutoTradeAction(market, "SKIP", "no signal", currentPrice, available, null, null, null);
    }

    private AutoTradeAction handleBuy(
            String market,
            BigDecimal cash,
            StrategyConfig config,
            MarketIndicators indicators,
            SignalTuning tuning,
            BigDecimal marketMaxOrderKrw
    ) {
        if (indicators == null || indicators.maShort() == null || indicators.maLong() == null || indicators.currentPrice() == null) {
            return new AutoTradeAction(market, "SKIP", "insufficient candles", null, null, null, null, null);
        }
        if (indicators.maShort().compareTo(indicators.maLong()) <= 0 || indicators.currentPrice().compareTo(indicators.maLong()) <= 0) {
            return new AutoTradeAction(market, "SKIP", "no trend", indicators.currentPrice(), null, null, null, null);
        }
        if (tuning.minMaLongSlopePct() > 0 && indicators.maLongSlopePct() == null) {
            return new AutoTradeAction(market, "SKIP", "no trend slope", indicators.currentPrice(), null, null, null, null);
        }
        if (indicators.maLongSlopePct() != null
                && indicators.maLongSlopePct().doubleValue() < tuning.minMaLongSlopePct()) {
            return new AutoTradeAction(market, "SKIP", "trend weakening", indicators.currentPrice(), null, null, null, null);
        }
        if (tuning.maxExtensionPct() > 0 && indicators.maLong() != null) {
            BigDecimal maxEntryPrice = indicators.maLong().multiply(percentFactor(tuning.maxExtensionPct()));
            if (indicators.currentPrice().compareTo(maxEntryPrice) > 0) {
                return new AutoTradeAction(market, "SKIP", "overextended", indicators.currentPrice(), null, null, null, null);
            }
        }
        if (tuning.minAdx() > 0) {
            if (indicators.adx() == null) {
                return new AutoTradeAction(market, "SKIP", "no adx", indicators.currentPrice(), null, null, null, null);
            }
            if (indicators.adx().doubleValue() < tuning.minAdx()) {
                return new AutoTradeAction(market, "SKIP", "weak_trend", indicators.currentPrice(), null, null, null, null);
            }
        }
        if (tuning.minVolumeRatio() > 0) {
            if (indicators.volumeRatio() == null) {
                return new AutoTradeAction(market, "SKIP", "no volume", indicators.currentPrice(), null, null, null, null);
            }
            if (indicators.volumeRatio().doubleValue() < tuning.minVolumeRatio()) {
                return new AutoTradeAction(market, "SKIP", "low_volume", indicators.currentPrice(), null, null, null, null);
            }
        }
        if (bollingerWindow > 1) {
            if (bollingerMinBandwidthPct > 0) {
                if (indicators.bollingerBandwidthPct() == null) {
                    return new AutoTradeAction(market, "SKIP", "bollinger_unavailable", indicators.currentPrice(), null, null, null, null);
                }
                if (indicators.bollingerBandwidthPct().doubleValue() < bollingerMinBandwidthPct) {
                    return new AutoTradeAction(market, "SKIP", "bollinger_squeeze", indicators.currentPrice(), null, null, null, null);
                }
            }
            if (bollingerMaxPercentB > 0) {
                if (indicators.bollingerPercentB() == null) {
                    return new AutoTradeAction(market, "SKIP", "bollinger_unavailable", indicators.currentPrice(), null, null, null, null);
                }
                if (indicators.bollingerPercentB().doubleValue() > bollingerMaxPercentB) {
                    return new AutoTradeAction(market, "SKIP", "bollinger_overbought", indicators.currentPrice(), null, null, null, null);
                }
            }
        }

        boolean rsiOk = indicators.rsi() != null
                && indicators.rsi().doubleValue() >= tuning.rsiBuyThreshold()
                && (tuning.rsiOverbought() <= 0 || indicators.rsi().doubleValue() <= tuning.rsiOverbought());
        boolean macdOk = indicators.macdHistogram() != null
                && indicators.macdHistogram().compareTo(BigDecimal.ZERO) > 0;
        boolean breakoutOk = indicators.breakoutLevel() != null
                && indicators.currentPrice().compareTo(indicators.breakoutLevel()) > 0;
        int confirmations = (rsiOk ? 1 : 0) + (macdOk ? 1 : 0) + (breakoutOk ? 1 : 0);
        int requiredConfirmations = Math.max(1, Math.min(3, tuning.minConfirmations()));
        if (confirmations < requiredConfirmations) {
            return new AutoTradeAction(market, "SKIP", "no signal", indicators.currentPrice(), null, null, null, null);
        }

        BigDecimal orderFunds = min(cash, marketMaxOrderKrw);
        orderFunds = applyVolatilityTarget(orderFunds, indicators.volatilityPct());
        orderFunds = applyCostBuffer(orderFunds);
        BigDecimal minTotal = resolveMinOrderKrw(market, "BUY");
        if (orderFunds.compareTo(minTotal) < 0) {
            return new AutoTradeAction(market, "SKIP", "insufficient cash", null, null, orderFunds, null, null);
        }

        if (isStopLossGuardActive(market)) {
            return new AutoTradeAction(market, "SKIP", "stop_loss_guard", null, null, orderFunds, null, null);
        }
        if (isReentryCooldown(market)) {
            return new AutoTradeAction(market, "SKIP", "reentry_cooldown", null, null, orderFunds, null, null);
        }
        if (isStopLossCooldown(market)) {
            return new AutoTradeAction(market, "SKIP", "stop_loss_cooldown", null, null, orderFunds, null, null);
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
                buildEntryReason(rsiOk, macdOk, breakoutOk),
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
        recordSellEvent(market, reason, response);

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

    private AutoTradeAction submitSellByPct(
            String market,
            BigDecimal available,
            BigDecimal currentPrice,
            double pct,
            String reason,
            boolean allowFullFallback,
            BigDecimal minTotal
    ) {
        if (available == null || available.compareTo(BigDecimal.ZERO) <= 0) {
            return new AutoTradeAction(market, "SKIP", "no volume", null, available, null, null, null);
        }
        if (pct <= 0) {
            return new AutoTradeAction(market, "SKIP", reason + "_disabled", currentPrice, available, null, null, null);
        }
        if (pct >= 100) {
            return submitSell(market, available, reason);
        }

        BigDecimal fraction = BigDecimal.valueOf(pct).divide(HUNDRED, 8, RoundingMode.HALF_UP);
        BigDecimal volume = available.multiply(fraction);
        if (volume.compareTo(BigDecimal.ZERO) <= 0) {
            return new AutoTradeAction(market, "SKIP", "no volume", null, available, null, null, null);
        }

        BigDecimal resolvedMinTotal = minTotal == null ? minOrderKrw : minTotal;
        if (currentPrice != null && currentPrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal estimated = currentPrice.multiply(volume);
            if (estimated.compareTo(resolvedMinTotal) < 0) {
                BigDecimal fullEstimated = currentPrice.multiply(available);
                if (allowFullFallback && fullEstimated.compareTo(resolvedMinTotal) >= 0) {
                    return submitSell(market, available, reason + "_full");
                }
                return new AutoTradeAction(market, "SKIP", "below min order", currentPrice, volume, estimated, null, null);
            }
        }

        if (hasOpenRequest(market, "SELL")) {
            return new AutoTradeAction(market, "SKIP", "pending", null, volume, null, null, null);
        }
        if (hasRecentOrder(market, "SELL")) {
            return new AutoTradeAction(market, "SKIP", "cooldown", null, volume, null, null, null);
        }

        OrderRequest request = new OrderRequest(market, "SELL", "MARKET", null, volume, null, null);
        OrderResponse response = orderService.create(request);
        recordSellEvent(market, reason, response);

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
                List.of(
                        com.btcautotrader.order.OrderStatus.REQUESTED,
                        com.btcautotrader.order.OrderStatus.PENDING,
                        com.btcautotrader.order.OrderStatus.SUBMITTED
                ),
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

    private BigDecimal applyCostBuffer(BigDecimal funds) {
        if (funds == null) {
            return BigDecimal.ZERO;
        }
        if (tradeCostRate == null || tradeCostRate.compareTo(BigDecimal.ZERO) <= 0) {
            return funds;
        }
        BigDecimal factor = BigDecimal.ONE.subtract(tradeCostRate);
        if (factor.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return funds.multiply(factor);
    }

    private BigDecimal resolveMinOrderKrw(String market, String side) {
        BigDecimal minTotal = minOrderKrw;
        OrderChanceSnapshot snapshot = fetchOrderChanceSnapshot(market);
        if (snapshot != null) {
            boolean isBuy = side != null && side.equalsIgnoreCase("BUY");
            BigDecimal candidate = isBuy ? snapshot.bidMinTotal() : snapshot.askMinTotal();
            if (candidate != null && candidate.compareTo(BigDecimal.ZERO) > 0) {
                minTotal = candidate;
            }
        }
        return minTotal == null ? BigDecimal.ZERO : minTotal;
    }

    private OrderChanceSnapshot fetchOrderChanceSnapshot(String market) {
        String normalized = normalizeMarketKey(market);
        if (normalized == null) {
            return null;
        }
        OffsetDateTime now = OffsetDateTime.now();
        OrderChanceSnapshot cached = orderChanceCache.get(normalized);
        if (cached != null && orderChanceCacheMinutes > 0 && cached.fetchedAt() != null) {
            OffsetDateTime threshold = now.minusMinutes(orderChanceCacheMinutes);
            if (cached.fetchedAt().isAfter(threshold)) {
                return cached;
            }
        }

        try {
            Map<String, Object> response = upbitService.fetchOrderChance(normalized);
            OrderChanceSnapshot snapshot = parseOrderChanceSnapshot(response);
            if (snapshot != null) {
                orderChanceCache.put(normalized, snapshot);
                return snapshot;
            }
        } catch (RuntimeException ignored) {
            // Order chance is a pre-check; fall back to cached or default min order.
        }
        return cached;
    }

    private static OrderChanceSnapshot parseOrderChanceSnapshot(Map<String, Object> response) {
        if (response == null || response.isEmpty()) {
            return null;
        }
        Map<String, Object> bid = asMap(response.get("bid"));
        Map<String, Object> ask = asMap(response.get("ask"));
        BigDecimal bidMinTotal = toDecimal(bid.get("min_total"));
        if (bidMinTotal.compareTo(BigDecimal.ZERO) <= 0) {
            bidMinTotal = null;
        }
        BigDecimal askMinTotal = toDecimal(ask.get("min_total"));
        if (askMinTotal.compareTo(BigDecimal.ZERO) <= 0) {
            askMinTotal = null;
        }
        BigDecimal bidFee = toDecimal(response.get("bid_fee"));
        BigDecimal askFee = toDecimal(response.get("ask_fee"));
        return new OrderChanceSnapshot(bidMinTotal, askMinTotal, bidFee, askFee, OffsetDateTime.now());
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static BigDecimal normalizeRate(BigDecimal rate) {
        if (rate == null) {
            return BigDecimal.ZERO;
        }
        if (rate.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (rate.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE;
        }
        return rate;
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

    private static String normalizeMarket(String market, String fallback) {
        String resolved = market;
        if (resolved == null || resolved.isBlank()) {
            resolved = fallback;
        }
        if (resolved == null || resolved.isBlank()) {
            return "KRW-BTC";
        }
        return resolved.trim().toUpperCase();
    }

    private static String normalizeMarketKey(String market) {
        if (market == null || market.isBlank()) {
            return null;
        }
        return market.trim().toUpperCase();
    }

    private static Map<String, BigDecimal> parseMarketMaxOrderKrwOverrides(String config) {
        Map<String, BigDecimal> overrides = new HashMap<>();
        if (config == null || config.isBlank()) {
            return overrides;
        }
        String[] pairs = config.split(",");
        for (String raw : pairs) {
            String[] entry = splitPair(raw);
            if (entry == null) {
                continue;
            }
            String market = entry[0].trim().toUpperCase();
            BigDecimal amount = toDecimal(entry[1]);
            if (market.isEmpty() || amount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            overrides.put(market, amount);
        }
        return overrides;
    }

    private static Map<String, StrategyProfile> parseMarketProfileOverrides(String config) {
        Map<String, StrategyProfile> overrides = new HashMap<>();
        if (config == null || config.isBlank()) {
            return overrides;
        }
        String[] pairs = config.split(",");
        for (String raw : pairs) {
            String[] entry = splitPair(raw);
            if (entry == null) {
                continue;
            }
            String market = entry[0].trim().toUpperCase();
            StrategyProfile profile = parseProfile(entry[1]);
            if (market.isEmpty() || profile == null) {
                continue;
            }
            overrides.put(market, profile);
        }
        return overrides;
    }

    private static String[] splitPair(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int idx = raw.indexOf(':');
        if (idx < 0) {
            idx = raw.indexOf('=');
        }
        if (idx <= 0 || idx == raw.length() - 1) {
            return null;
        }
        return new String[]{raw.substring(0, idx), raw.substring(idx + 1)};
    }

    private static StrategyProfile parseProfile(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return StrategyProfile.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Map<String, BigDecimal> mergeMarketMaxOrderKrwOverrides(
            Map<String, BigDecimal> propertyOverrides,
            StrategyMarketOverrides runtimeOverrides
    ) {
        Map<String, BigDecimal> merged = new HashMap<>();
        if (propertyOverrides != null) {
            merged.putAll(propertyOverrides);
        }
        if (runtimeOverrides != null && runtimeOverrides.maxOrderKrwByMarket() != null) {
            for (Map.Entry<String, Double> entry : runtimeOverrides.maxOrderKrwByMarket().entrySet()) {
                String market = entry.getKey();
                Double amount = entry.getValue();
                if (market == null || amount == null || amount <= 0) {
                    continue;
                }
                merged.put(market.toUpperCase(), BigDecimal.valueOf(amount));
            }
        }
        return merged;
    }

    private static Map<String, StrategyProfile> mergeMarketProfileOverrides(
            Map<String, StrategyProfile> propertyOverrides,
            StrategyMarketOverrides runtimeOverrides
    ) {
        Map<String, StrategyProfile> merged = new HashMap<>();
        if (propertyOverrides != null) {
            merged.putAll(propertyOverrides);
        }
        if (runtimeOverrides != null && runtimeOverrides.profileByMarket() != null) {
            for (Map.Entry<String, String> entry : runtimeOverrides.profileByMarket().entrySet()) {
                String market = entry.getKey();
                StrategyProfile profile = parseProfile(entry.getValue());
                if (market == null || profile == null) {
                    continue;
                }
                merged.put(market.toUpperCase(), profile);
            }
        }
        return merged;
    }

    private static Map<String, Boolean> mergeMarketTradePausedOverrides(StrategyMarketOverrides runtimeOverrides) {
        Map<String, Boolean> merged = new HashMap<>();
        if (runtimeOverrides != null && runtimeOverrides.tradePausedByMarket() != null) {
            for (Map.Entry<String, Boolean> entry : runtimeOverrides.tradePausedByMarket().entrySet()) {
                String market = entry.getKey();
                Boolean paused = entry.getValue();
                if (market == null || paused == null) {
                    continue;
                }
                merged.put(market.toUpperCase(), paused);
            }
        }
        return merged;
    }

    private StrategyConfig resolveConfigForMarket(
            String market,
            StrategyConfig baseConfig,
            StrategyMarketOverrides runtimeOverrides
    ) {
        if (baseConfig == null || runtimeOverrides == null || runtimeOverrides.ratiosByMarket() == null) {
            return baseConfig;
        }
        StrategyMarketRatios ratios = runtimeOverrides.ratiosByMarket().get(market);
        if (ratios == null) {
            return baseConfig;
        }
        return new StrategyConfig(
                baseConfig.enabled(),
                baseConfig.maxOrderKrw(),
                chooseRatio(ratios.takeProfitPct(), baseConfig.takeProfitPct()),
                chooseRatio(ratios.stopLossPct(), baseConfig.stopLossPct()),
                chooseRatio(ratios.trailingStopPct(), baseConfig.trailingStopPct()),
                chooseRatio(ratios.partialTakeProfitPct(), baseConfig.partialTakeProfitPct()),
                baseConfig.profile(),
                chooseRatio(ratios.stopExitPct(), baseConfig.stopExitPct()),
                chooseRatio(ratios.trendExitPct(), baseConfig.trendExitPct()),
                chooseRatio(ratios.momentumExitPct(), baseConfig.momentumExitPct())
        );
    }

    private static double chooseRatio(Double override, double fallback) {
        if (override == null || Double.isNaN(override) || Double.isInfinite(override)) {
            return fallback;
        }
        return override;
    }

    private StrategyProfile resolveProfileForMarket(
            String market,
            StrategyConfig config,
            Map<String, StrategyProfile> marketProfileByMarket
    ) {
        if (config == null) {
            return StrategyProfile.BALANCED;
        }
        StrategyProfile override = marketProfileByMarket.get(market);
        if (override != null) {
            return override;
        }
        return StrategyProfile.from(config.profile());
    }

    private BigDecimal resolveMarketMaxOrderKrw(
            String market,
            StrategyConfig config,
            Map<String, BigDecimal> marketMaxOrderKrwByMarket
    ) {
        BigDecimal defaultMaxOrderKrw = BigDecimal.valueOf(Math.max(config.maxOrderKrw(), 0.0));
        BigDecimal override = marketMaxOrderKrwByMarket.get(market);
        if (override == null || override.compareTo(BigDecimal.ZERO) <= 0) {
            return defaultMaxOrderKrw;
        }
        return override;
    }

    private static boolean canScaleInAfterSellAction(AutoTradeAction sellAction) {
        if (sellAction == null) {
            return true;
        }
        if (!"SKIP".equalsIgnoreCase(sellAction.action())) {
            return false;
        }
        String reason = sellAction.reason();
        if (reason == null || reason.isBlank()) {
            return true;
        }
        String normalized = reason.toLowerCase(Locale.ROOT);
        if ("pending".equals(normalized) || "cooldown".equals(normalized)) {
            return false;
        }
        if (normalized.startsWith("stop_loss")
                || normalized.startsWith("trailing_stop")
                || normalized.startsWith("momentum_reversal")
                || normalized.startsWith("trend_break")
                || normalized.startsWith("take_profit")) {
            return false;
        }
        return true;
    }

    private MarketSelection selectMarketsForTick(List<String> markets, Map<String, AccountSnapshot> accounts) {
        if (markets == null || markets.isEmpty()) {
            return new MarketSelection(List.of(), Map.of(), Map.of());
        }

        List<String> heldMarkets = new ArrayList<>();
        List<String> flatMarkets = new ArrayList<>();
        for (String market : markets) {
            if (positionTotalForMarket(market, accounts).compareTo(BigDecimal.ZERO) > 0) {
                heldMarkets.add(market);
            } else {
                flatMarkets.add(market);
            }
        }

        Map<String, BigDecimal> momentumScores = relativeMomentumEnabled
                ? computeRelativeMomentumScores(flatMarkets)
                : Map.of();
        FlatSelection flatSelection = relativeMomentumEnabled
                ? selectFlatMarketsByMomentum(flatMarkets, momentumScores)
                : new FlatSelection(rotateMarkets(flatMarkets), new LinkedHashMap<>());

        List<String> selected = new ArrayList<>(heldMarkets);
        Map<String, String> deferredReasonsByMarket = new LinkedHashMap<>(flatSelection.deferredReasonsByMarket());

        int safeLimit = maxMarketsPerTick <= 0 ? Integer.MAX_VALUE : Math.max(1, maxMarketsPerTick);
        int capacityForFlat = Math.max(0, safeLimit - selected.size());
        if (safeLimit == Integer.MAX_VALUE) {
            selected.addAll(flatSelection.selectedCandidates());
        } else {
            List<String> candidates = flatSelection.selectedCandidates();
            for (int i = 0; i < candidates.size(); i++) {
                String market = candidates.get(i);
                if (i < capacityForFlat) {
                    selected.add(market);
                } else {
                    deferredReasonsByMarket.putIfAbsent(market, "tick_rate_limited");
                }
            }
        }

        return new MarketSelection(
                selected,
                Map.copyOf(deferredReasonsByMarket),
                momentumScores.isEmpty() ? Map.of() : Map.copyOf(momentumScores)
        );
    }

    private FlatSelection selectFlatMarketsByMomentum(
            List<String> flatMarkets,
            Map<String, BigDecimal> momentumScores
    ) {
        if (flatMarkets == null || flatMarkets.isEmpty()) {
            return new FlatSelection(List.of(), Map.of());
        }

        List<String> ranked = flatMarkets.stream()
                .sorted((left, right) -> compareMomentumMarket(left, right, momentumScores))
                .toList();

        int topN = relativeMomentumTopN <= 0 ? Integer.MAX_VALUE : Math.max(1, relativeMomentumTopN);
        int selectedCount = 0;
        List<String> selected = new ArrayList<>();
        Map<String, String> deferred = new LinkedHashMap<>();
        for (String market : ranked) {
            BigDecimal score = momentumScores.get(market);
            if (score == null) {
                deferred.put(market, "momentum_unavailable");
                continue;
            }
            if (score.doubleValue() < relativeMomentumMinScorePct) {
                deferred.put(market, "weak_relative_momentum");
                continue;
            }
            if (selectedCount >= topN) {
                deferred.put(market, "not_in_top_momentum");
                continue;
            }
            selected.add(market);
            selectedCount++;
        }
        return new FlatSelection(selected, deferred);
    }

    private static int compareMomentumMarket(
            String left,
            String right,
            Map<String, BigDecimal> momentumScores
    ) {
        BigDecimal leftScore = momentumScores.get(left);
        BigDecimal rightScore = momentumScores.get(right);
        if (leftScore == null && rightScore == null) {
            return left.compareTo(right);
        }
        if (leftScore == null) {
            return 1;
        }
        if (rightScore == null) {
            return -1;
        }
        int scoreCompare = rightScore.compareTo(leftScore);
        if (scoreCompare != 0) {
            return scoreCompare;
        }
        return left.compareTo(right);
    }

    private Map<String, BigDecimal> computeRelativeMomentumScores(List<String> markets) {
        if (!relativeMomentumEnabled || markets == null || markets.isEmpty()) {
            return Map.of();
        }

        int shortLookback = relativeMomentumShortLookback;
        int longLookback = relativeMomentumLongLookback;
        int count = Math.max(shortLookback, longLookback) + 1;
        if (useClosedCandle) {
            // One extra candle is required because the latest still-forming candle is dropped.
            count += 1;
        }

        Map<String, BigDecimal> scores = new HashMap<>();
        OffsetDateTime now = OffsetDateTime.now();
        for (String market : markets) {
            String key = normalizeMarketKey(market);
            if (key == null) {
                continue;
            }
            MomentumSnapshot cached = relativeMomentumCache.get(key);
            if (cached != null && cached.score() != null) {
                if (relativeMomentumCacheMinutes > 0 && cached.fetchedAt() != null) {
                    OffsetDateTime threshold = now.minusMinutes(relativeMomentumCacheMinutes);
                    if (cached.fetchedAt().isAfter(threshold)) {
                        scores.put(market, cached.score());
                        continue;
                    }
                }
            }
            try {
                List<Map<String, Object>> candles = upbitService.fetchMinuteCandles(
                        key,
                        relativeMomentumTimeframeUnit,
                        count
                );
                BigDecimal score = computeRelativeMomentumScore(candles, shortLookback, longLookback);
                if (score != null) {
                    scores.put(market, score);
                    relativeMomentumCache.put(key, new MomentumSnapshot(score, OffsetDateTime.now()));
                } else if (cached != null && cached.score() != null) {
                    scores.put(market, cached.score());
                }
            } catch (RuntimeException ignored) {
                // Momentum score is optional and should not block sell-side safety handling.
                if (cached != null && cached.score() != null) {
                    scores.put(market, cached.score());
                }
            }
        }
        return scores;
    }

    private BigDecimal computeRelativeMomentumScore(
            List<Map<String, Object>> candles,
            int shortLookback,
            int longLookback
    ) {
        List<BigDecimal> closes = extractSortedCloses(candles);
        if (useClosedCandle) {
            dropLast(closes);
        }
        if (closes.isEmpty()) {
            return null;
        }

        BigDecimal shortReturnPct = computeReturnPct(closes, shortLookback);
        BigDecimal longReturnPct = computeReturnPct(closes, longLookback);
        if (shortReturnPct == null || longReturnPct == null) {
            return null;
        }

        return longReturnPct.multiply(RELATIVE_MOMENTUM_LONG_WEIGHT)
                .add(shortReturnPct.multiply(RELATIVE_MOMENTUM_SHORT_WEIGHT));
    }

    private RegimeSnapshot evaluateRegime(String market) {
        String regimeTarget = normalizeMarket(market, regimeMarket);
        if (!regimeFilterEnabled) {
            return RegimeSnapshot.allow("regime_disabled", regimeTarget, null, null, null, null, null);
        }

        int shortWindow = regimeMaShort;
        int longWindow = regimeMaLong;
        int slopeWindow = regimeSlopeLookback;
        int volatilityWindowSafe = regimeVolatilityWindow;

        int count = longWindow;
        if (slopeWindow > 0) {
            count = Math.max(count, longWindow + slopeWindow);
        }
        if (volatilityWindowSafe > 1) {
            count = Math.max(count, volatilityWindowSafe + 1);
        }

        List<Map<String, Object>> candles;
        try {
            candles = upbitService.fetchMinuteCandles(regimeTarget, regimeTimeframeUnit, count);
        } catch (RuntimeException ex) {
            return RegimeSnapshot.block("regime_unavailable", regimeTarget, null, null, null, null, null);
        }

        List<BigDecimal> closes = extractSortedCloses(candles);
        if (useClosedCandle) {
            dropLast(closes);
        }
        if (closes.size() < longWindow) {
            return RegimeSnapshot.block("regime_insufficient_data", regimeTarget, null, null, null, null, null);
        }

        BigDecimal currentPrice = closes.get(closes.size() - 1);
        BigDecimal maShortValue = averageLast(closes, shortWindow);
        BigDecimal maLongValue = averageLast(closes, longWindow);
        if (maShortValue == null || maLongValue == null || maLongValue.compareTo(BigDecimal.ZERO) <= 0) {
            return RegimeSnapshot.block("regime_invalid_trend", regimeTarget, currentPrice, maShortValue, maLongValue, null, null);
        }

        BigDecimal slopePct = null;
        if (slopeWindow > 0) {
            BigDecimal maLongPrev = averageLastWithOffset(closes, longWindow, slopeWindow);
            if (maLongPrev != null && maLongPrev.compareTo(BigDecimal.ZERO) > 0) {
                slopePct = maLongValue.subtract(maLongPrev)
                        .divide(maLongPrev, 8, RoundingMode.HALF_UP)
                        .multiply(HUNDRED);
            } else if (regimeMinMaLongSlopePct > 0) {
                return RegimeSnapshot.block(
                        "regime_missing_slope",
                        regimeTarget,
                        currentPrice,
                        maShortValue,
                        maLongValue,
                        null,
                        null
                );
            }
        }

        BigDecimal volatilityPct = null;
        if (volatilityWindowSafe > 1) {
            volatilityPct = computeVolatilityPct(closes, volatilityWindowSafe);
        }

        if (currentPrice.compareTo(maLongValue) <= 0 || maShortValue.compareTo(maLongValue) <= 0) {
            return RegimeSnapshot.block(
                    "regime_trend_off",
                    regimeTarget,
                    currentPrice,
                    maShortValue,
                    maLongValue,
                    slopePct,
                    volatilityPct
            );
        }
        if (regimeMinMaLongSlopePct > 0
                && slopePct != null
                && slopePct.doubleValue() < regimeMinMaLongSlopePct) {
            return RegimeSnapshot.block(
                    "regime_slope_off",
                    regimeTarget,
                    currentPrice,
                    maShortValue,
                    maLongValue,
                    slopePct,
                    volatilityPct
            );
        }
        if (regimeMaxVolatilityPct != null
                && regimeMaxVolatilityPct.compareTo(BigDecimal.ZERO) > 0
                && volatilityPct != null
                && volatilityPct.compareTo(regimeMaxVolatilityPct) > 0) {
            return RegimeSnapshot.block(
                    "regime_high_vol",
                    regimeTarget,
                    currentPrice,
                    maShortValue,
                    maLongValue,
                    slopePct,
                    volatilityPct
            );
        }

        return RegimeSnapshot.allow(
                "regime_on",
                regimeTarget,
                currentPrice,
                maShortValue,
                maLongValue,
                slopePct,
                volatilityPct
        );
    }

    private List<String> rotateMarkets(List<String> markets) {
        if (markets == null || markets.size() <= 1) {
            return markets == null ? List.of() : new ArrayList<>(markets);
        }
        int start = Math.floorMod(marketCursor.getAndIncrement(), markets.size());
        List<String> ordered = new ArrayList<>(markets.size());
        for (int i = 0; i < markets.size(); i++) {
            ordered.add(markets.get((start + i) % markets.size()));
        }
        return ordered;
    }

    private BigDecimal positionTotalForMarket(String market, Map<String, AccountSnapshot> accounts) {
        String currency = extractCurrency(market);
        if (currency == null || accounts == null) {
            return BigDecimal.ZERO;
        }
        return accounts.getOrDefault(currency, AccountSnapshot.empty()).total();
    }

    private static List<BigDecimal> extractSortedCloses(List<Map<String, Object>> candles) {
        if (candles == null || candles.isEmpty()) {
            return List.of();
        }
        List<BigDecimal> closes = new ArrayList<>();
        for (Map<String, Object> candle : candles) {
            BigDecimal close = toDecimal(candle.get("trade_price"));
            if (close.compareTo(BigDecimal.ZERO) > 0) {
                closes.add(close);
            }
        }
        if (closes.isEmpty()) {
            return List.of();
        }
        reverseInPlace(closes);
        return closes;
    }

    private static BigDecimal computeReturnPct(List<BigDecimal> closes, int lookback) {
        if (closes == null || closes.isEmpty() || lookback <= 0 || closes.size() <= lookback) {
            return null;
        }
        int nowIdx = closes.size() - 1;
        int pastIdx = nowIdx - lookback;
        if (pastIdx < 0) {
            return null;
        }
        BigDecimal now = closes.get(nowIdx);
        BigDecimal past = closes.get(pastIdx);
        if (past == null || past.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return now.subtract(past)
                .divide(past, 8, RoundingMode.HALF_UP)
                .multiply(HUNDRED);
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private MarketIndicators fetchIndicators(String market, SignalTuning tuning) {
        int required = Math.max(maLong, maShort);
        int volWindow = Math.max(0, volatilityWindow);
        int rsiWindow = Math.max(2, rsiPeriod);
        int macdFastWindow = Math.max(2, macdFast);
        int macdSlowWindow = Math.max(macdFastWindow + 1, macdSlow);
        int macdSignalWindow = Math.max(2, macdSignal);
        int adxWindow = Math.max(2, adxPeriod);
        int volumeWindow = Math.max(1, volumeLookback);
        int bollingerWindowSafe = Math.max(0, bollingerWindow);
        int breakoutWindow = Math.max(0, breakoutLookback);
        int trailingWindowSafe = Math.max(0, trailingWindow);
        int slopeLookback = Math.max(0, maLongSlopeLookback);

        int count = required;
        count = Math.max(count, rsiWindow + 1);
        count = Math.max(count, macdSlowWindow + macdSignalWindow);
        count = Math.max(count, adxWindow * 2 + 1);
        count = Math.max(count, volumeWindow + 1);
        if (bollingerWindowSafe > 1) {
            count = Math.max(count, bollingerWindowSafe);
        }
        if (breakoutWindow > 1) {
            count = Math.max(count, breakoutWindow + 1);
        }
        if (trailingWindowSafe > 1) {
            count = Math.max(count, trailingWindowSafe);
        }
        if (slopeLookback > 0) {
            count = Math.max(count, required + slopeLookback);
        }
        if (targetVolPct != null && targetVolPct.compareTo(BigDecimal.ZERO) > 0) {
            count = Math.max(count, volWindow + 1);
        }
        if (required <= 1) {
            return null;
        }
        int requestCount = useClosedCandle ? count + 1 : count;
        List<Map<String, Object>> candles = upbitService.fetchMinuteCandles(market, candleUnitMinutes, requestCount);
        if (candles == null || candles.isEmpty()) {
            return null;
        }

        List<BigDecimal> closes = new ArrayList<>();
        List<BigDecimal> highs = new ArrayList<>();
        List<BigDecimal> lows = new ArrayList<>();
        List<BigDecimal> quoteVolumes = new ArrayList<>();
        for (Map<String, Object> candle : candles) {
            BigDecimal close = toDecimal(candle.get("trade_price"));
            BigDecimal high = toDecimal(candle.get("high_price"));
            BigDecimal low = toDecimal(candle.get("low_price"));
            BigDecimal quoteVolume = toDecimal(candle.get("candle_acc_trade_price"));
            if (close.compareTo(BigDecimal.ZERO) > 0
                    && high.compareTo(BigDecimal.ZERO) > 0
                    && low.compareTo(BigDecimal.ZERO) > 0) {
                closes.add(close);
                highs.add(high);
                lows.add(low);
                quoteVolumes.add(quoteVolume.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : quoteVolume);
            }
        }
        if (closes.size() < required) {
            return null;
        }
        reverseInPlace(closes);
        reverseInPlace(highs);
        reverseInPlace(lows);
        reverseInPlace(quoteVolumes);

        if (useClosedCandle) {
            dropLast(closes);
            dropLast(highs);
            dropLast(lows);
            dropLast(quoteVolumes);
        }
        if (closes.size() < required) {
            return null;
        }

        BigDecimal currentPrice = closes.get(closes.size() - 1);
        BigDecimal maShortValue = averageLast(closes, maShort);
        BigDecimal maLongValue = averageLast(closes, maLong);
        BigDecimal volatilityPct = null;
        if (targetVolPct != null && targetVolPct.compareTo(BigDecimal.ZERO) > 0 && volWindow > 1) {
            volatilityPct = computeVolatilityPct(closes, volWindow);
        }

        BigDecimal rsiValue = computeRsi(closes, rsiWindow);
        BigDecimal macdHistogram = computeMacdHistogram(closes, macdFastWindow, macdSlowWindow, macdSignalWindow);
        BigDecimal adxValue = computeAdx(highs, lows, closes, adxWindow);
        BigDecimal volumeRatio = computeVolumeRatio(quoteVolumes, volumeWindow);
        BollingerSnapshot bollinger = computeBollinger(closes, bollingerWindowSafe, currentPrice);
        BigDecimal maLongSlopePct = null;
        if (slopeLookback > 0) {
            BigDecimal maLongPrev = averageLastWithOffset(closes, maLong, slopeLookback);
            if (maLongPrev != null && maLongPrev.compareTo(BigDecimal.ZERO) > 0 && maLongValue != null) {
                maLongSlopePct = maLongValue.subtract(maLongPrev)
                        .divide(maLongPrev, 8, RoundingMode.HALF_UP)
                        .multiply(HUNDRED);
            }
        }

        BigDecimal breakoutLevel = null;
        if (breakoutWindow > 1 && highs.size() >= breakoutWindow + 1) {
            BigDecimal breakoutHigh = highestHigh(highs, breakoutWindow, true);
            if (breakoutHigh != null) {
                breakoutLevel = breakoutHigh.multiply(percentFactor(tuning.breakoutPct()));
            }
        }

        BigDecimal trailingHigh = null;
        if (trailingWindowSafe > 1 && highs.size() >= trailingWindowSafe) {
            trailingHigh = highestHigh(highs, trailingWindowSafe, false);
        }

        return new MarketIndicators(
                currentPrice,
                maShortValue,
                maLongValue,
                volatilityPct,
                rsiValue,
                macdHistogram,
                adxValue,
                volumeRatio,
                bollinger == null ? null : bollinger.middle(),
                bollinger == null ? null : bollinger.upper(),
                bollinger == null ? null : bollinger.lower(),
                bollinger == null ? null : bollinger.bandwidthPct(),
                bollinger == null ? null : bollinger.percentB(),
                breakoutLevel,
                trailingHigh,
                maLongSlopePct
        );
    }

    private BigDecimal applyVolatilityTarget(BigDecimal funds, BigDecimal volatilityPct) {
        if (funds == null) {
            return BigDecimal.ZERO;
        }
        if (targetVolPct == null || targetVolPct.compareTo(BigDecimal.ZERO) <= 0) {
            return funds;
        }
        if (volatilityPct == null || volatilityPct.compareTo(BigDecimal.ZERO) <= 0) {
            return funds;
        }
        BigDecimal scale = targetVolPct.divide(volatilityPct, 8, RoundingMode.HALF_UP);
        if (scale.compareTo(BigDecimal.ONE) > 0) {
            scale = BigDecimal.ONE;
        }
        return funds.multiply(scale);
    }

    private static BigDecimal averageLast(List<BigDecimal> values, int window) {
        if (values == null || values.isEmpty() || window <= 0 || values.size() < window) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = values.size() - window; i < values.size(); i++) {
            sum = sum.add(values.get(i));
        }
        return sum.divide(BigDecimal.valueOf(window), 8, RoundingMode.HALF_UP);
    }

    private static BigDecimal averageLastWithOffset(List<BigDecimal> values, int window, int offset) {
        if (values == null || values.isEmpty() || window <= 0 || offset < 0) {
            return null;
        }
        int end = values.size() - 1 - offset;
        int start = end - window + 1;
        if (start < 0 || end < 0) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = start; i <= end; i++) {
            sum = sum.add(values.get(i));
        }
        return sum.divide(BigDecimal.valueOf(window), 8, RoundingMode.HALF_UP);
    }

    private static BigDecimal computeVolatilityPct(List<BigDecimal> closes, int window) {
        if (closes.size() < window + 1) {
            return null;
        }
        int start = closes.size() - window - 1;
        double mean = 0.0;
        double[] returns = new double[window];
        int idx = 0;
        for (int i = start + 1; i < closes.size(); i++) {
            BigDecimal prev = closes.get(i - 1);
            BigDecimal curr = closes.get(i);
            if (prev.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            double r = curr.subtract(prev).divide(prev, 12, RoundingMode.HALF_UP).doubleValue();
            returns[idx++] = r;
            mean += r;
        }
        if (idx == 0) {
            return null;
        }
        mean /= idx;
        double variance = 0.0;
        for (int i = 0; i < idx; i++) {
            double diff = returns[i] - mean;
            variance += diff * diff;
        }
        variance /= idx;
        double stdev = Math.sqrt(variance);
        return BigDecimal.valueOf(stdev).multiply(HUNDRED);
    }

    private static BigDecimal computeRsi(List<BigDecimal> closes, int period) {
        if (closes == null || closes.size() < period + 1) {
            return null;
        }
        int start = closes.size() - period - 1;
        double gain = 0.0;
        double loss = 0.0;
        for (int i = start + 1; i < closes.size(); i++) {
            double diff = closes.get(i).subtract(closes.get(i - 1)).doubleValue();
            if (diff >= 0) {
                gain += diff;
            } else {
                loss -= diff;
            }
        }
        double avgGain = gain / period;
        double avgLoss = loss / period;
        if (avgLoss == 0.0) {
            return BigDecimal.valueOf(100.0);
        }
        if (avgGain == 0.0) {
            return BigDecimal.ZERO;
        }
        double rs = avgGain / avgLoss;
        double rsi = 100.0 - (100.0 / (1.0 + rs));
        return BigDecimal.valueOf(rsi);
    }

    private static BigDecimal computeMacdHistogram(List<BigDecimal> closes, int fast, int slow, int signal) {
        if (closes == null || closes.size() < slow + signal) {
            return null;
        }
        List<Double> emaFast = emaSeriesFromDecimal(closes, fast);
        List<Double> emaSlow = emaSeriesFromDecimal(closes, slow);
        if (emaFast.isEmpty() || emaSlow.isEmpty()) {
            return null;
        }
        int size = Math.min(emaFast.size(), emaSlow.size());
        List<Double> macdLine = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            macdLine.add(emaFast.get(i) - emaSlow.get(i));
        }
        List<Double> signalLine = emaSeries(macdLine, signal);
        if (signalLine.isEmpty()) {
            return null;
        }
        double macd = macdLine.get(macdLine.size() - 1);
        double signalValue = signalLine.get(signalLine.size() - 1);
        return BigDecimal.valueOf(macd - signalValue);
    }

    private static BigDecimal computeAdx(List<BigDecimal> highs, List<BigDecimal> lows, List<BigDecimal> closes, int period) {
        if (highs == null || lows == null || closes == null || period < 2) {
            return null;
        }
        int size = Math.min(highs.size(), Math.min(lows.size(), closes.size()));
        if (size < period * 2 + 1) {
            return null;
        }

        double trSmooth = 0.0;
        double plusDmSmooth = 0.0;
        double minusDmSmooth = 0.0;
        for (int i = 1; i <= period; i++) {
            double tr = trueRange(highs.get(i), lows.get(i), closes.get(i - 1));
            double upMove = highs.get(i).subtract(highs.get(i - 1)).doubleValue();
            double downMove = lows.get(i - 1).subtract(lows.get(i)).doubleValue();
            double plusDm = (upMove > downMove && upMove > 0.0) ? upMove : 0.0;
            double minusDm = (downMove > upMove && downMove > 0.0) ? downMove : 0.0;
            trSmooth += tr;
            plusDmSmooth += plusDm;
            minusDmSmooth += minusDm;
        }

        List<Double> dxValues = new ArrayList<>();
        dxValues.add(computeDx(trSmooth, plusDmSmooth, minusDmSmooth));
        for (int i = period + 1; i < size; i++) {
            double tr = trueRange(highs.get(i), lows.get(i), closes.get(i - 1));
            double upMove = highs.get(i).subtract(highs.get(i - 1)).doubleValue();
            double downMove = lows.get(i - 1).subtract(lows.get(i)).doubleValue();
            double plusDm = (upMove > downMove && upMove > 0.0) ? upMove : 0.0;
            double minusDm = (downMove > upMove && downMove > 0.0) ? downMove : 0.0;

            trSmooth = trSmooth - (trSmooth / period) + tr;
            plusDmSmooth = plusDmSmooth - (plusDmSmooth / period) + plusDm;
            minusDmSmooth = minusDmSmooth - (minusDmSmooth / period) + minusDm;
            dxValues.add(computeDx(trSmooth, plusDmSmooth, minusDmSmooth));
        }

        if (dxValues.size() < period) {
            return null;
        }
        double adx = 0.0;
        for (int i = 0; i < period; i++) {
            adx += dxValues.get(i);
        }
        adx /= period;
        for (int i = period; i < dxValues.size(); i++) {
            adx = ((adx * (period - 1)) + dxValues.get(i)) / period;
        }
        return BigDecimal.valueOf(adx);
    }

    private static double trueRange(BigDecimal high, BigDecimal low, BigDecimal prevClose) {
        double h = high.doubleValue();
        double l = low.doubleValue();
        double pc = prevClose.doubleValue();
        double range1 = h - l;
        double range2 = Math.abs(h - pc);
        double range3 = Math.abs(l - pc);
        return Math.max(range1, Math.max(range2, range3));
    }

    private static double computeDx(double trSmooth, double plusDmSmooth, double minusDmSmooth) {
        if (trSmooth <= 0.0) {
            return 0.0;
        }
        double plusDi = 100.0 * (plusDmSmooth / trSmooth);
        double minusDi = 100.0 * (minusDmSmooth / trSmooth);
        double diSum = plusDi + minusDi;
        if (diSum <= 0.0) {
            return 0.0;
        }
        return 100.0 * Math.abs(plusDi - minusDi) / diSum;
    }

    private static BigDecimal computeVolumeRatio(List<BigDecimal> quoteVolumes, int lookback) {
        if (quoteVolumes == null || lookback <= 0 || quoteVolumes.size() < lookback + 1) {
            return null;
        }
        int end = quoteVolumes.size() - 1;
        BigDecimal current = quoteVolumes.get(end);
        if (current == null || current.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal avg = averageRange(quoteVolumes, end - lookback, end - 1);
        if (avg == null || avg.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return current.divide(avg, 8, RoundingMode.HALF_UP);
    }

    private BollingerSnapshot computeBollinger(List<BigDecimal> closes, int window, BigDecimal currentPrice) {
        if (window <= 1 || closes == null || closes.size() < window) {
            return null;
        }
        BigDecimal middle = averageLast(closes, window);
        if (middle == null) {
            return null;
        }
        BigDecimal stdev = computeStdDev(closes, window, middle);
        if (stdev == null) {
            return null;
        }
        BigDecimal deviation = stdev.multiply(BigDecimal.valueOf(bollingerStdDev));
        BigDecimal upper = middle.add(deviation);
        BigDecimal lower = middle.subtract(deviation);
        BigDecimal band = upper.subtract(lower);

        BigDecimal bandwidthPct = null;
        if (middle.compareTo(BigDecimal.ZERO) > 0) {
            bandwidthPct = band.divide(middle, 8, RoundingMode.HALF_UP).multiply(HUNDRED);
        }

        BigDecimal percentB = null;
        if (currentPrice != null && band.compareTo(BigDecimal.ZERO) > 0) {
            percentB = currentPrice.subtract(lower).divide(band, 8, RoundingMode.HALF_UP);
        }

        return new BollingerSnapshot(middle, upper, lower, bandwidthPct, percentB);
    }

    private static BigDecimal computeStdDev(List<BigDecimal> values, int window, BigDecimal mean) {
        if (values == null || mean == null || window <= 1 || values.size() < window) {
            return null;
        }
        double avg = mean.doubleValue();
        double sum = 0.0;
        for (int i = values.size() - window; i < values.size(); i++) {
            double diff = values.get(i).doubleValue() - avg;
            sum += diff * diff;
        }
        double variance = sum / window;
        return BigDecimal.valueOf(Math.sqrt(variance));
    }

    private static List<Double> emaSeriesFromDecimal(List<BigDecimal> values, int period) {
        if (values == null || values.isEmpty() || period <= 0) {
            return List.of();
        }
        double k = 2.0 / (period + 1.0);
        List<Double> ema = new ArrayList<>(values.size());
        double prev = values.get(0).doubleValue();
        ema.add(prev);
        for (int i = 1; i < values.size(); i++) {
            double price = values.get(i).doubleValue();
            prev = price * k + prev * (1.0 - k);
            ema.add(prev);
        }
        return ema;
    }

    private static List<Double> emaSeries(List<Double> values, int period) {
        if (values == null || values.isEmpty() || period <= 0) {
            return List.of();
        }
        double k = 2.0 / (period + 1.0);
        List<Double> ema = new ArrayList<>(values.size());
        double prev = values.get(0);
        ema.add(prev);
        for (int i = 1; i < values.size(); i++) {
            double price = values.get(i);
            prev = price * k + prev * (1.0 - k);
            ema.add(prev);
        }
        return ema;
    }

    private static BigDecimal highestHigh(List<BigDecimal> highs, int window, boolean excludeLast) {
        if (highs == null || highs.isEmpty() || window <= 0) {
            return null;
        }
        int end = highs.size() - 1;
        if (excludeLast) {
            end -= 1;
        }
        if (end < 0) {
            return null;
        }
        int start = Math.max(0, end - window + 1);
        BigDecimal max = null;
        for (int i = start; i <= end; i++) {
            BigDecimal value = highs.get(i);
            if (value == null) {
                continue;
            }
            if (max == null || value.compareTo(max) > 0) {
                max = value;
            }
        }
        return max;
    }

    private static BigDecimal averageRange(List<BigDecimal> values, int start, int end) {
        if (values == null || values.isEmpty() || start < 0 || end < start || end >= values.size()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (int i = start; i <= end; i++) {
            BigDecimal value = values.get(i);
            if (value == null) {
                continue;
            }
            sum = sum.add(value);
            count++;
        }
        if (count == 0) {
            return null;
        }
        return sum.divide(BigDecimal.valueOf(count), 8, RoundingMode.HALF_UP);
    }

    private String buildEntryReason(boolean rsiOk, boolean macdOk, boolean breakoutOk) {
        List<String> parts = new ArrayList<>();
        if (rsiOk) {
            parts.add("rsi");
        }
        if (macdOk) {
            parts.add("macd");
        }
        if (breakoutOk) {
            parts.add("breakout");
        }
        if (parts.isEmpty()) {
            return "trend_entry";
        }
        return "trend_entry:" + String.join("+", parts);
    }

    private AutoTradeAction attemptPartialTakeProfit(
            String market,
            BigDecimal available,
            BigDecimal currentPrice,
            double partialTakeProfitPct,
            BigDecimal minTotal
    ) {
        if (partialTakeProfitPct <= 0 || partialTakeProfitPct >= 100) {
            return null;
        }
        OffsetDateTime now = OffsetDateTime.now();
        BigDecimal fraction = BigDecimal.valueOf(partialTakeProfitPct)
                .divide(HUNDRED, 8, RoundingMode.HALF_UP);
        BigDecimal volume = available.multiply(fraction);
        if (volume.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal estimatedValue = currentPrice.multiply(volume);
        BigDecimal resolvedMinTotal = minTotal == null ? minOrderKrw : minTotal;
        if (estimatedValue.compareTo(resolvedMinTotal) < 0) {
            return null;
        }
        if (hasOpenRequest(market, "SELL")) {
            return new AutoTradeAction(market, "SKIP", "pending", currentPrice, volume, estimatedValue, null, null);
        }
        if (hasRecentOrder(market, "SELL")) {
            return new AutoTradeAction(market, "SKIP", "cooldown", currentPrice, volume, estimatedValue, null, null);
        }
        OrderRequest request = new OrderRequest(market, "SELL", "MARKET", null, volume, null, null);
        OrderResponse response = orderService.create(request);
        if (isAcceptedOrder(response)) {
            lastPartialTakeProfitAt.put(market, now);
        }
        recordSellEvent(market, "take_profit_partial", response);
        return new AutoTradeAction(
                market,
                "SELL",
                "take_profit_partial",
                null,
                volume,
                null,
                response.orderId(),
                response.requestStatus()
        );
    }

    private boolean isStopLossCooldown(String market) {
        if (stopLossCooldownMinutes <= 0) {
            return false;
        }
        OffsetDateTime last = lastStopLossAt.get(market);
        if (last == null) {
            return false;
        }
        return last.isAfter(OffsetDateTime.now().minusMinutes(stopLossCooldownMinutes));
    }

    private boolean isReentryCooldown(String market) {
        if (reentryCooldownMinutes <= 0) {
            return false;
        }
        OffsetDateTime last = lastExitAt.get(market);
        if (last == null) {
            return false;
        }
        return last.isAfter(OffsetDateTime.now().minusMinutes(reentryCooldownMinutes));
    }

    private boolean isStopLossGuardActive(String market) {
        if (stopLossGuardLockMinutes <= 0) {
            return false;
        }
        OffsetDateTime until = stopLossGuardUntilByMarket.get(market);
        if (until == null) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (now.isAfter(until)) {
            stopLossGuardUntilByMarket.remove(market);
            return false;
        }
        return true;
    }

    private void recordSellEvent(String market, String reason, OrderResponse response) {
        if (!isAcceptedOrder(response)) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        lastExitAt.put(market, now);
        if (isStopLikeReason(reason)) {
            lastStopLossAt.put(market, now);
            if (stopLossGuardTriggerCount > 0 && stopLossGuardLookbackMinutes > 0 && stopLossGuardLockMinutes > 0) {
                registerStopLossEvent(market, now);
            }
        }
    }

    private void registerStopLossEvent(String market, OffsetDateTime occurredAt) {
        Deque<OffsetDateTime> events = stopLossEventsByMarket.computeIfAbsent(market, key -> new ArrayDeque<>());
        synchronized (events) {
            OffsetDateTime threshold = occurredAt.minusMinutes(stopLossGuardLookbackMinutes);
            while (!events.isEmpty() && events.peekFirst().isBefore(threshold)) {
                events.pollFirst();
            }
            events.addLast(occurredAt);
            if (events.size() >= stopLossGuardTriggerCount) {
                stopLossGuardUntilByMarket.put(market, occurredAt.plusMinutes(stopLossGuardLockMinutes));
            }
        }
    }

    private static boolean isAcceptedOrder(OrderResponse response) {
        if (response == null || response.requestStatus() == null) {
            return false;
        }
        return !"FAILED".equalsIgnoreCase(response.requestStatus());
    }

    private static boolean isStopLikeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return false;
        }
        return reason.contains("stop_loss")
                || reason.contains("trailing_stop")
                || reason.contains("momentum_reversal");
    }

    private void recordDecision(
            String market,
            AutoTradeAction action,
            StrategyConfig config,
            StrategyProfile profile,
            MarketIndicators indicators,
            SignalTuning tuning,
            RegimeSnapshot regime,
            BigDecimal momentumScorePct,
            BigDecimal marketMaxOrderKrw
    ) {
        if (action == null || tradeDecisionService == null) {
            return;
        }
        try {
            TradeDecisionEntity entity = new TradeDecisionEntity();
            entity.setMarket(market);
            entity.setAction(action.action());
            entity.setReason(action.reason());
            entity.setProfile(profile == null ? (config == null ? null : config.profile()) : profile.name());
            entity.setPrice(action.price() != null ? action.price() : (indicators == null ? null : indicators.currentPrice()));
            entity.setQuantity(action.quantity());
            entity.setFunds(action.funds());
            entity.setOrderId(action.orderId());
            entity.setRequestStatus(action.requestStatus());

            BigDecimal entryTrailingHigh = trailingHighByMarket.get(market);
            if (indicators != null) {
                entity.setMaShort(indicators.maShort());
                entity.setMaLong(indicators.maLong());
                entity.setRsi(indicators.rsi());
                entity.setMacdHistogram(indicators.macdHistogram());
                entity.setBreakoutLevel(indicators.breakoutLevel());
                entity.setMaLongSlopePct(indicators.maLongSlopePct());
                entity.setVolatilityPct(indicators.volatilityPct());
            }
            if (entryTrailingHigh != null) {
                entity.setTrailingHigh(entryTrailingHigh);
            } else if (indicators != null) {
                entity.setTrailingHigh(indicators.trailingHigh());
            }

            Map<String, Object> details = new HashMap<>();
            details.put("timeframeUnit", candleUnitMinutes);
            details.put("maShortWindow", maShort);
            details.put("maLongWindow", maLong);
            details.put("rsiPeriod", rsiPeriod);
            details.put("macdFast", macdFast);
            details.put("macdSlow", macdSlow);
            details.put("macdSignal", macdSignal);
            details.put("adxPeriod", adxPeriod);
            details.put("minAdx", minAdx);
            details.put("volumeLookback", volumeLookback);
            details.put("minVolumeRatio", minVolumeRatio);
            details.put("bollingerWindow", bollingerWindow);
            details.put("bollingerStdDev", bollingerStdDev);
            details.put("bollingerMinBandwidthPct", bollingerMinBandwidthPct);
            details.put("bollingerMaxPercentB", bollingerMaxPercentB);
            details.put("breakoutLookback", breakoutLookback);
            details.put("trailingWindow", trailingWindow);
            details.put("entryTrailingHigh", entryTrailingHigh);
            if (indicators != null) {
                details.put("windowTrailingHigh", indicators.trailingHigh());
            }
            details.put("volatilityWindow", volatilityWindow);
            details.put("targetVolPct", targetVolPct);
            details.put("feeRate", feeRate);
            details.put("slippagePct", slippagePct);
            details.put("tradeCostRate", tradeCostRate);
            details.put("orderChanceCacheMinutes", orderChanceCacheMinutes);
            details.put("reentryCooldownMinutes", reentryCooldownMinutes);
            details.put("stopLossGuardLookbackMinutes", stopLossGuardLookbackMinutes);
            details.put("stopLossGuardTriggerCount", stopLossGuardTriggerCount);
            details.put("stopLossGuardLockMinutes", stopLossGuardLockMinutes);
            details.put("maxMarketsPerTick", maxMarketsPerTick);
            details.put("marketMaxOrderKrw", marketMaxOrderKrw);
            details.put("useClosedCandle", useClosedCandle);
            details.put("regimeFilterEnabled", regimeFilterEnabled);
            details.put("regimeFilterPerMarket", regimeFilterPerMarket);
            details.put("regimeMarket", regime != null ? regime.market() : regimeMarket);
            details.put("regimeTimeframeUnit", regimeTimeframeUnit);
            details.put("relativeMomentumEnabled", relativeMomentumEnabled);
            details.put("relativeMomentumTopN", relativeMomentumTopN);
            details.put("relativeMomentumMinScorePct", relativeMomentumMinScorePct);
            details.put("relativeMomentumCacheMinutes", relativeMomentumCacheMinutes);
            if (config != null) {
                details.put("stopExitPct", config.stopExitPct());
                details.put("trendExitPct", config.trendExitPct());
                details.put("momentumExitPct", config.momentumExitPct());
                details.put("partialTakeProfitPct", config.partialTakeProfitPct());
            }
            if (tuning != null) {
                details.put("rsiBuyThreshold", tuning.rsiBuyThreshold());
                details.put("rsiSellThreshold", tuning.rsiSellThreshold());
                details.put("rsiOverbought", tuning.rsiOverbought());
                details.put("breakoutPct", tuning.breakoutPct());
                details.put("minConfirmations", tuning.minConfirmations());
                details.put("maxExtensionPct", tuning.maxExtensionPct());
                details.put("minMaLongSlopePct", tuning.minMaLongSlopePct());
                details.put("tuningMinAdx", tuning.minAdx());
                details.put("tuningMinVolumeRatio", tuning.minVolumeRatio());
            }
            if (indicators != null) {
                details.put("adx", indicators.adx());
                details.put("volumeRatio", indicators.volumeRatio());
                details.put("bollingerMiddle", indicators.bollingerMiddle());
                details.put("bollingerUpper", indicators.bollingerUpper());
                details.put("bollingerLower", indicators.bollingerLower());
                details.put("bollingerBandwidthPct", indicators.bollingerBandwidthPct());
                details.put("bollingerPercentB", indicators.bollingerPercentB());
            }
            if (regime != null) {
                details.put("regimeAllowEntries", regime.allowEntries());
                details.put("regimeReason", regime.reason());
                details.put("regimePrice", regime.price());
                details.put("regimeMaShort", regime.maShort());
                details.put("regimeMaLong", regime.maLong());
                details.put("regimeMaLongSlopePct", regime.maLongSlopePct());
                details.put("regimeVolatilityPct", regime.volatilityPct());
            }
            if (momentumScorePct != null) {
                details.put("relativeMomentumScorePct", momentumScorePct);
            }
            String marketKey = normalizeMarketKey(market);
            OrderChanceSnapshot orderChance = marketKey == null ? null : orderChanceCache.get(marketKey);
            if (orderChance != null) {
                details.put("orderChanceBidMinTotal", orderChance.bidMinTotal());
                details.put("orderChanceAskMinTotal", orderChance.askMinTotal());
                details.put("orderChanceBidFee", orderChance.bidFee());
                details.put("orderChanceAskFee", orderChance.askFee());
                details.put(
                        "orderChanceFetchedAt",
                        orderChance.fetchedAt() == null ? null : orderChance.fetchedAt().toString()
                );
            }

            tradeDecisionService.record(entity, details);
        } catch (RuntimeException ex) {
            // Do not fail trading due to decision logging issues.
        }
    }

    private boolean canTakePartialProfit(String market, OffsetDateTime now) {
        if (partialTakeProfitCooldownMinutes <= 0) {
            return true;
        }
        OffsetDateTime last = lastPartialTakeProfitAt.get(market);
        if (last == null) {
            return true;
        }
        return last.isBefore(now.minusMinutes(partialTakeProfitCooldownMinutes));
    }

    private static void reverseInPlace(List<BigDecimal> values) {
        for (int i = 0, j = values.size() - 1; i < j; i++, j--) {
            BigDecimal tmp = values.get(i);
            values.set(i, values.get(j));
            values.set(j, tmp);
        }
    }

    private static void dropLast(List<BigDecimal> values) {
        if (values == null || values.size() <= 1) {
            return;
        }
        values.remove(values.size() - 1);
    }

    private record MarketIndicators(
            BigDecimal currentPrice,
            BigDecimal maShort,
            BigDecimal maLong,
            BigDecimal volatilityPct,
            BigDecimal rsi,
            BigDecimal macdHistogram,
            BigDecimal adx,
            BigDecimal volumeRatio,
            BigDecimal bollingerMiddle,
            BigDecimal bollingerUpper,
            BigDecimal bollingerLower,
            BigDecimal bollingerBandwidthPct,
            BigDecimal bollingerPercentB,
            BigDecimal breakoutLevel,
            BigDecimal trailingHigh,
            BigDecimal maLongSlopePct
    ) {
    }

    private record BollingerSnapshot(
            BigDecimal middle,
            BigDecimal upper,
            BigDecimal lower,
            BigDecimal bandwidthPct,
            BigDecimal percentB
    ) {
    }

    private record SignalTuning(
            double rsiBuyThreshold,
            double rsiSellThreshold,
            double rsiOverbought,
            double minAdx,
            double minVolumeRatio,
            double breakoutPct,
            int minConfirmations,
            double maxExtensionPct,
            double minMaLongSlopePct
    ) {
    }

    private record OrderChanceSnapshot(
            BigDecimal bidMinTotal,
            BigDecimal askMinTotal,
            BigDecimal bidFee,
            BigDecimal askFee,
            OffsetDateTime fetchedAt
    ) {
    }

    private record MomentumSnapshot(
            BigDecimal score,
            OffsetDateTime fetchedAt
    ) {
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private boolean isBackoffActive(String key, OffsetDateTime now) {
        BackoffState state = backoffStates.get(key);
        if (state == null) {
            return false;
        }
        OffsetDateTime until = state.until();
        if (until == null || now.isAfter(until)) {
            backoffStates.remove(key);
            return false;
        }
        return true;
    }

    private void recordFailure(String key, OffsetDateTime now) {
        BackoffState previous = backoffStates.get(key);
        int failures = previous == null ? 1 : previous.consecutiveFailures() + 1;
        long delay = failureBackoffBaseSeconds;
        for (int i = 1; i < failures; i++) {
            delay = Math.min(failureBackoffMaxSeconds, delay * 2);
        }
        backoffStates.put(key, new BackoffState(failures, now.plusSeconds(delay)));
    }

    private void resetFailure(String key) {
        backoffStates.remove(key);
    }

    private record AccountSnapshot(BigDecimal balance, BigDecimal locked, BigDecimal avgBuyPrice) {
        BigDecimal total() {
            return balance.add(locked);
        }

        static AccountSnapshot empty() {
            return new AccountSnapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    private record FlatSelection(List<String> selectedCandidates, Map<String, String> deferredReasonsByMarket) {
    }

    private record MarketSelection(
            List<String> selected,
            Map<String, String> deferredReasonsByMarket,
            Map<String, BigDecimal> momentumScorePctByMarket
    ) {
    }

    private record RegimeSnapshot(
            boolean allowEntries,
            String reason,
            String market,
            BigDecimal price,
            BigDecimal maShort,
            BigDecimal maLong,
            BigDecimal maLongSlopePct,
            BigDecimal volatilityPct
    ) {
        static RegimeSnapshot allow(
                String reason,
                String market,
                BigDecimal price,
                BigDecimal maShort,
                BigDecimal maLong,
                BigDecimal maLongSlopePct,
                BigDecimal volatilityPct
        ) {
            return new RegimeSnapshot(true, reason, market, price, maShort, maLong, maLongSlopePct, volatilityPct);
        }

        static RegimeSnapshot block(
                String reason,
                String market,
                BigDecimal price,
                BigDecimal maShort,
                BigDecimal maLong,
                BigDecimal maLongSlopePct,
                BigDecimal volatilityPct
        ) {
            return new RegimeSnapshot(false, reason, market, price, maShort, maLong, maLongSlopePct, volatilityPct);
        }
    }

    private record BackoffState(int consecutiveFailures, OffsetDateTime until) {
    }
}
