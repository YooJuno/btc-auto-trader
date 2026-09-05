package com.btcautotrader.engine;

import com.btcautotrader.order.OrderRepository;
import com.btcautotrader.order.OrderRequest;
import com.btcautotrader.order.OrderResponse;
import com.btcautotrader.order.OrderService;
import com.btcautotrader.auth.TradingAccessService;
import com.btcautotrader.strategy.StrategyConfig;
import com.btcautotrader.strategy.StrategyMarketOverrides;
import com.btcautotrader.strategy.StrategyMarketRatios;
import com.btcautotrader.strategy.StrategyProfile;
import com.btcautotrader.strategy.StrategyService;
import com.btcautotrader.tenant.TenantContext;
import com.btcautotrader.tenant.TenantDatabaseProvisioningService;
import com.btcautotrader.upbit.UpbitAuthNetworkStatusResolver;
import com.btcautotrader.upbit.UpbitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class AutoTradeService {
    private static final Logger log = LoggerFactory.getLogger(AutoTradeService.class);
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final double MAX_TRAILING_ARM_PCT = 1.2;
    private static final String SYSTEM_KEY = "SYSTEM";
    private static final String DEFAULT_TENANT_KEY = "__system__";
    private static final String TENANT_KEY_SEPARATOR = "::";

    private final UpbitService upbitService;
    private final OrderService orderService;
    private final StrategyService strategyService;
    private final EngineService engineService;
    private final TenantDatabaseProvisioningService tenantDatabaseProvisioningService;
    private final TradingAccessService tradingAccessService;
    private final OrderRepository orderRepository;
    private final TradeDecisionRepository tradeDecisionRepository;
    private final TradeDecisionService tradeDecisionService;
    private final UniverseSelectionService universeSelectionService;
    /**
     * Entry-model registry. resolveSignalModel used to ignore its argument and return one hardcoded
     * instance, so the pluggability hook existed in name only.
     */
    private final Map<String, TradeSignalModel> signalModels = Stream
            .of(new UnifiedTrendSignalModel(), new VolatilityContractionBreakoutModel())
            .collect(Collectors.toUnmodifiableMap(TradeSignalModel::name, model -> model));

    private final BigDecimal minOrderKrw;
    private final BigDecimal feeRate;
    private final BigDecimal slippagePct;
    private final BigDecimal tradeCostRate;
    private final long cooldownSeconds;
    private final long pendingWindowMinutes;
    private final long failureBackoffBaseSeconds;
    private final long failureBackoffMaxSeconds;
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
    private final double squeezeMaxBandwidthPct;
    private final String defaultSignalModel;
    private final int breakoutLookback;
    private final int breakdownLookback;
    private final double breakoutPct;
    private final double maxExtensionPct;
    private final int maLongSlopeLookback;
    private final int minConfirmations;
    private final int trailingWindow;
    private final int atrPeriod;
    private final double atrStopLossMultiplier;
    private final double atrTrailingStopMultiplier;
    private final double atrTrailingArmMultiplier;
    private final boolean atrRiskSizingEnabled;
    private final boolean atrExitThresholdsEnabled;
    private final long partialTakeProfitCooldownMinutes;
    private final long stopLossCooldownMinutes;
    private final long reentryCooldownMinutes;
    private final long stopLossGuardLookbackMinutes;
    private final int stopLossGuardTriggerCount;
    private final long stopLossGuardLockMinutes;
    private final double dailyLossLimitPct;
    private final int volatilityWindow;
    private final BigDecimal targetVolPct;
    private final boolean useClosedCandle;
    private final boolean regimeFilterEnabled;
    private final int regimeTimeframeUnit;
    private final int regimeMaShort;
    private final int regimeMaLong;
    private final int regimeSlopeLookback;
    private final double regimeMinMaLongSlopePct;
    private final int regimeVolatilityWindow;
    private final BigDecimal regimeMaxVolatilityPct;
    private final boolean regimeParameterSwitchEnabled;
    private final double regimeRiskOnSlopePct;
    private final double regimeRiskOnMaxVolatilityPct;
    private final double regimeRiskOnSizeMultiplier;
    private final double regimeRiskOnTakeProfitMultiplier;
    private final double regimeRiskOnStopLossMultiplier;
    private final double regimeRiskOnTrailingStopMultiplier;
    private final double regimeRiskOnRsiBuyAdjust;
    private final double regimeCautionSizeMultiplier;
    private final double regimeCautionTakeProfitMultiplier;
    private final double regimeCautionStopLossMultiplier;
    private final double regimeCautionTrailingStopMultiplier;
    private final double regimeCautionRsiBuyAdjust;
    private final boolean htfConfirmEnabled;
    private final int htfConfirmUnitMinutes;
    private final int htfConfirmMaShort;
    private final int htfConfirmMaLong;
    private final int htfConfirmSlopeLookback;
    private final double htfConfirmMinMaLongSlopePct;
    private final long htfConfirmCacheMinutes;
    private final long orderChanceCacheMinutes;
    private final int stateRestoreLimit;

    private final Map<String, AtomicBoolean> runningByTenant = new ConcurrentHashMap<>();
    private final Set<String> restoredStateTenants = ConcurrentHashMap.newKeySet();
    private final Map<String, BackoffState> backoffStates = new ConcurrentHashMap<>();
    private final Map<String, OffsetDateTime> lastPartialTakeProfitAt = new ConcurrentHashMap<>();
    private final Map<String, OffsetDateTime> lastStopLossAt = new ConcurrentHashMap<>();
    private final Map<String, OffsetDateTime> lastExitAt = new ConcurrentHashMap<>();
    private final Map<String, Deque<OffsetDateTime>> stopLossEventsByMarket = new ConcurrentHashMap<>();
    private final Map<String, OffsetDateTime> stopLossGuardUntilByMarket = new ConcurrentHashMap<>();
    private final Map<String, DailyLossBaseline> dailyLossBaselinesByTenant = new ConcurrentHashMap<>();
    // The tick's market overrides, per tenant, so entry-model resolution is reachable from handleBuy and
    // recordDecision without threading the overrides through 15 call sites. Keyed by tenant because
    // tenants tick concurrently on the scheduler pool.
    private final Map<String, StrategyMarketOverrides> tickOverridesByTenant = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> trailingHighByMarket = new ConcurrentHashMap<>();
    private final Map<String, OffsetDateTime> lastEntryAtByMarket = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> entryAtrPctByMarket = new ConcurrentHashMap<>();
    private final Map<String, OrderChanceSnapshot> orderChanceCache = new ConcurrentHashMap<>();
    private final Map<String, HtfTrendSnapshot> htfTrendCache = new ConcurrentHashMap<>();

    public AutoTradeService(
            UpbitService upbitService,
            OrderService orderService,
            StrategyService strategyService,
            EngineService engineService,
            TenantDatabaseProvisioningService tenantDatabaseProvisioningService,
            TradingAccessService tradingAccessService,
            OrderRepository orderRepository,
            TradeDecisionRepository tradeDecisionRepository,
            TradeDecisionService tradeDecisionService,
            UniverseSelectionService universeSelectionService,
            @Value("${trading.min-krw:5000}") BigDecimal minOrderKrw,
            @Value("${trading.fee-rate:0.0005}") BigDecimal feeRate,
            @Value("${trading.slippage-pct:0.001}") BigDecimal slippagePct,
            @Value("${engine.order-cooldown-seconds:30}") long cooldownSeconds,
            @Value("${orders.pending-window-minutes:30}") long pendingWindowMinutes,
            @Value("${engine.failure-backoff-base-seconds:5}") long failureBackoffBaseSeconds,
            @Value("${engine.failure-backoff-max-seconds:300}") long failureBackoffMaxSeconds,
            @Value("${signal.timeframe-unit:3}") int candleUnitMinutes,
            @Value("${signal.ma-short:5}") int maShort,
            @Value("${signal.ma-long:55}") int maLong,
            @Value("${signal.rsi-period:14}") int rsiPeriod,
            @Value("${signal.rsi-buy-threshold:53}") double rsiBuyThreshold,
            @Value("${signal.rsi-sell-threshold:45}") double rsiSellThreshold,
            @Value("${signal.rsi-overbought:70}") double rsiOverbought,
            @Value("${signal.macd-fast:12}") int macdFast,
            @Value("${signal.macd-slow:26}") int macdSlow,
            @Value("${signal.macd-signal:9}") int macdSignal,
            @Value("${signal.adx-period:14}") int adxPeriod,
            @Value("${signal.min-adx:8}") double minAdx,
            @Value("${signal.volume-lookback:20}") int volumeLookback,
            @Value("${signal.min-volume-ratio:0.4}") double minVolumeRatio,
            @Value("${signal.bollinger.window:20}") int bollingerWindow,
            @Value("${signal.bollinger.stddev:2.0}") double bollingerStdDev,
            @Value("${signal.squeeze.max-bandwidth-pct:2.5}") double squeezeMaxBandwidthPct,
            @Value("${signal.model:trend_breakout}") String defaultSignalModel,
            @Value("${signal.breakout-lookback:20}") int breakoutLookback,
            @Value("${signal.breakdown-lookback:10}") int breakdownLookback,
            @Value("${signal.breakout-pct:0.05}") double breakoutPct,
            @Value("${signal.max-extension-pct:1.5}") double maxExtensionPct,
            @Value("${signal.ma-long-slope-lookback:5}") int maLongSlopeLookback,
            @Value("${signal.min-confirmations:2}") int minConfirmations,
            @Value("${risk.trailing-window:20}") int trailingWindow,
            @Value("${risk.atr-period:20}") int atrPeriod,
            @Value("${risk.atr-stop-loss-multiplier:2.6}") double atrStopLossMultiplier,
            @Value("${risk.atr-trailing-stop-multiplier:1.8}") double atrTrailingStopMultiplier,
            @Value("${risk.atr-trailing-arm-multiplier:1.5}") double atrTrailingArmMultiplier,
            @Value("${risk.atr-risk-sizing-enabled:true}") boolean atrRiskSizingEnabled,
            @Value("${risk.atr-exit-thresholds-enabled:true}") boolean atrExitThresholdsEnabled,
            @Value("${risk.partial-take-profit-cooldown-minutes:120}") long partialTakeProfitCooldownMinutes,
            @Value("${risk.stop-loss-cooldown-minutes:30}") long stopLossCooldownMinutes,
            @Value("${risk.reentry-cooldown-minutes:15}") long reentryCooldownMinutes,
            @Value("${risk.stop-loss-guard-lookback-minutes:180}") long stopLossGuardLookbackMinutes,
            @Value("${risk.stop-loss-guard-trigger-count:3}") int stopLossGuardTriggerCount,
            @Value("${risk.stop-loss-guard-lock-minutes:180}") long stopLossGuardLockMinutes,
            @Value("${risk.daily-loss-limit-pct:0}") double dailyLossLimitPct,
            @Value("${risk.volatility-window:30}") int volatilityWindow,
            @Value("${risk.target-vol-pct:0.5}") BigDecimal targetVolPct,
            @Value("${signal.use-closed-candle:true}") boolean useClosedCandle,
            @Value("${regime.filter.enabled:true}") boolean regimeFilterEnabled,
            @Value("${regime.filter.timeframe-unit:15}") int regimeTimeframeUnit,
            @Value("${regime.filter.ma-short:30}") int regimeMaShort,
            @Value("${regime.filter.ma-long:90}") int regimeMaLong,
            @Value("${regime.filter.ma-long-slope-lookback:5}") int regimeSlopeLookback,
            @Value("${regime.filter.min-ma-long-slope-pct:0.0}") double regimeMinMaLongSlopePct,
            @Value("${regime.filter.volatility-window:48}") int regimeVolatilityWindow,
            @Value("${regime.filter.max-volatility-pct:2.2}") BigDecimal regimeMaxVolatilityPct,
            @Value("${regime.switch.enabled:true}") boolean regimeParameterSwitchEnabled,
            @Value("${regime.switch.risk-on-slope-pct:0.12}") double regimeRiskOnSlopePct,
            @Value("${regime.switch.risk-on-max-volatility-pct:0.8}") double regimeRiskOnMaxVolatilityPct,
            @Value("${regime.switch.risk-on-size-multiplier:1.15}") double regimeRiskOnSizeMultiplier,
            @Value("${regime.switch.risk-on-take-profit-multiplier:1.1}") double regimeRiskOnTakeProfitMultiplier,
            @Value("${regime.switch.risk-on-stop-loss-multiplier:1.05}") double regimeRiskOnStopLossMultiplier,
            @Value("${regime.switch.risk-on-trailing-stop-multiplier:1.1}") double regimeRiskOnTrailingStopMultiplier,
            @Value("${regime.switch.risk-on-rsi-buy-adjust:-1.0}") double regimeRiskOnRsiBuyAdjust,
            @Value("${regime.switch.caution-size-multiplier:0.8}") double regimeCautionSizeMultiplier,
            @Value("${regime.switch.caution-take-profit-multiplier:0.95}") double regimeCautionTakeProfitMultiplier,
            @Value("${regime.switch.caution-stop-loss-multiplier:0.9}") double regimeCautionStopLossMultiplier,
            @Value("${regime.switch.caution-trailing-stop-multiplier:0.9}") double regimeCautionTrailingStopMultiplier,
            @Value("${regime.switch.caution-rsi-buy-adjust:1.5}") double regimeCautionRsiBuyAdjust,
            @Value("${signal.htf-confirm.enabled:true}") boolean htfConfirmEnabled,
            @Value("${signal.htf-confirm.unit:15}") int htfConfirmUnitMinutes,
            @Value("${signal.htf-confirm.ma-short:20}") int htfConfirmMaShort,
            @Value("${signal.htf-confirm.ma-long:50}") int htfConfirmMaLong,
            @Value("${signal.htf-confirm.slope-lookback:3}") int htfConfirmSlopeLookback,
            @Value("${signal.htf-confirm.min-ma-long-slope-pct:0.0}") double htfConfirmMinMaLongSlopePct,
            @Value("${signal.htf-confirm.cache-minutes:3}") long htfConfirmCacheMinutes,
            @Value("${orders.chance-cache-minutes:5}") long orderChanceCacheMinutes,
            @Value("${engine.state-restore-limit:500}") int stateRestoreLimit
    ) {
        this.upbitService = upbitService;
        this.orderService = orderService;
        this.strategyService = strategyService;
        this.engineService = engineService;
        this.tenantDatabaseProvisioningService = tenantDatabaseProvisioningService;
        this.tradingAccessService = tradingAccessService;
        this.orderRepository = orderRepository;
        this.tradeDecisionRepository = tradeDecisionRepository;
        this.tradeDecisionService = tradeDecisionService;
        this.universeSelectionService = universeSelectionService;
        this.minOrderKrw = minOrderKrw;
        this.feeRate = normalizeRate(feeRate);
        this.slippagePct = normalizeRate(slippagePct);
        BigDecimal combinedRate = this.feeRate.add(this.slippagePct);
        this.tradeCostRate = combinedRate.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : combinedRate;
        this.cooldownSeconds = cooldownSeconds;
        this.pendingWindowMinutes = pendingWindowMinutes;
        this.failureBackoffBaseSeconds = failureBackoffBaseSeconds;
        this.failureBackoffMaxSeconds = failureBackoffMaxSeconds;
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
        this.squeezeMaxBandwidthPct = squeezeMaxBandwidthPct;
        this.defaultSignalModel = defaultSignalModel == null || defaultSignalModel.isBlank()
                ? UnifiedTrendSignalModel.NAME
                : defaultSignalModel.trim();
        this.breakoutLookback = breakoutLookback;
        this.breakdownLookback = Math.max(0, Math.min(breakdownLookback, 200));
        this.breakoutPct = breakoutPct;
        this.maxExtensionPct = maxExtensionPct;
        this.maLongSlopeLookback = maLongSlopeLookback;
        this.minConfirmations = minConfirmations;
        this.trailingWindow = trailingWindow;
        this.atrPeriod = Math.max(2, Math.min(atrPeriod, 200));
        this.atrStopLossMultiplier = clamp(atrStopLossMultiplier, 0.1, 10.0);
        this.atrTrailingStopMultiplier = clamp(atrTrailingStopMultiplier, 0.1, 12.0);
        this.atrTrailingArmMultiplier = clamp(atrTrailingArmMultiplier, 0.1, 10.0);
        this.atrRiskSizingEnabled = atrRiskSizingEnabled;
        this.atrExitThresholdsEnabled = atrExitThresholdsEnabled;
        this.partialTakeProfitCooldownMinutes = partialTakeProfitCooldownMinutes;
        this.stopLossCooldownMinutes = stopLossCooldownMinutes;
        this.reentryCooldownMinutes = reentryCooldownMinutes;
        this.stopLossGuardLookbackMinutes = stopLossGuardLookbackMinutes;
        this.stopLossGuardTriggerCount = stopLossGuardTriggerCount;
        this.stopLossGuardLockMinutes = stopLossGuardLockMinutes;
        this.dailyLossLimitPct = Math.max(0.0, dailyLossLimitPct);
        this.volatilityWindow = volatilityWindow;
        this.targetVolPct = targetVolPct;
        this.useClosedCandle = useClosedCandle;
        this.regimeFilterEnabled = regimeFilterEnabled;
        this.regimeTimeframeUnit = Math.max(1, regimeTimeframeUnit);
        this.regimeMaShort = Math.max(2, Math.min(regimeMaShort, 199));
        this.regimeMaLong = Math.max(this.regimeMaShort + 1, Math.min(regimeMaLong, 200));
        this.regimeSlopeLookback = Math.max(0, Math.min(regimeSlopeLookback, 60));
        this.regimeMinMaLongSlopePct = regimeMinMaLongSlopePct;
        this.regimeVolatilityWindow = Math.max(2, Math.min(regimeVolatilityWindow, 200));
        this.regimeMaxVolatilityPct = regimeMaxVolatilityPct;
        this.regimeParameterSwitchEnabled = regimeParameterSwitchEnabled;
        this.regimeRiskOnSlopePct = regimeRiskOnSlopePct;
        this.regimeRiskOnMaxVolatilityPct = Math.max(0.0, regimeRiskOnMaxVolatilityPct);
        this.regimeRiskOnSizeMultiplier = clamp(regimeRiskOnSizeMultiplier, 0.1, 3.0);
        this.regimeRiskOnTakeProfitMultiplier = clamp(regimeRiskOnTakeProfitMultiplier, 0.5, 3.0);
        this.regimeRiskOnStopLossMultiplier = clamp(regimeRiskOnStopLossMultiplier, 0.5, 3.0);
        this.regimeRiskOnTrailingStopMultiplier = clamp(regimeRiskOnTrailingStopMultiplier, 0.5, 3.0);
        this.regimeRiskOnRsiBuyAdjust = clamp(regimeRiskOnRsiBuyAdjust, -10.0, 10.0);
        this.regimeCautionSizeMultiplier = clamp(regimeCautionSizeMultiplier, 0.1, 2.0);
        this.regimeCautionTakeProfitMultiplier = clamp(regimeCautionTakeProfitMultiplier, 0.5, 3.0);
        this.regimeCautionStopLossMultiplier = clamp(regimeCautionStopLossMultiplier, 0.5, 3.0);
        this.regimeCautionTrailingStopMultiplier = clamp(regimeCautionTrailingStopMultiplier, 0.5, 3.0);
        this.regimeCautionRsiBuyAdjust = clamp(regimeCautionRsiBuyAdjust, -10.0, 10.0);
        this.htfConfirmEnabled = htfConfirmEnabled;
        this.htfConfirmUnitMinutes = Math.max(1, htfConfirmUnitMinutes);
        this.htfConfirmMaShort = Math.max(2, Math.min(htfConfirmMaShort, 199));
        this.htfConfirmMaLong = Math.max(this.htfConfirmMaShort + 1, Math.min(htfConfirmMaLong, 200));
        this.htfConfirmSlopeLookback = Math.max(0, Math.min(htfConfirmSlopeLookback, 60));
        this.htfConfirmMinMaLongSlopePct = htfConfirmMinMaLongSlopePct;
        this.htfConfirmCacheMinutes = Math.max(0, htfConfirmCacheMinutes);
        this.orderChanceCacheMinutes = Math.max(0, orderChanceCacheMinutes);
        this.stateRestoreLimit = Math.max(0, stateRestoreLimit);
    }

    private void restoreExitStateIfNeeded() {
        String tenantKey = currentTenantKey();
        if (!restoredStateTenants.add(tenantKey)) {
            return;
        }
        try {
            restoreExitStateForCurrentTenant();
        restoreDailyLossBaselineForCurrentTenant();
        restoreOpenPositionStateForCurrentTenant();
        } catch (RuntimeException ex) {
            // Retry on next tick if hydration fails due to transient DB errors.
            restoredStateTenants.remove(tenantKey);
            throw ex;
        }
    }

    private void restoreExitStateForCurrentTenant() {
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
            String market = tenantScopedMarketKey(decision.getMarket());
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

    /**
     * Rebuilds today's equity baseline for the daily-loss guard.
     *
     * The baseline lived only in memory, so a restart re-seeded it from the CURRENT (already drawn down)
     * equity and cleared the circuit breaker — a bot that had hit its daily limit resumed trading, and a
     * crash loop could bypass the limit indefinitely. It is written into every decision's details, so the
     * first decision recorded today carries the correct value.
     */
    /**
     * Rebuilds an open position's trailing high, entry time and entry ATR.
     *
     * All three lived only in memory, so a restart mid-trade re-seeded the trailing high at roughly the
     * current price — loosening the stop on a position that had already run up and pulled back — and
     * resized the ATR-derived stop to whatever volatility happened to be now.
     *
     * Only decisions STRICTLY AFTER the latest BUY are trusted. recordDecision falls back to
     * indicators.trailingHigh() when nothing is tracked, and that is the candle-window high including
     * bars before entry; arming a trail from a peak the position never participated in would force an
     * instant exit, which handleSell_ignoresPreEntryHighWhenArmingTrailingStop exists to prevent.
     */
    private void restoreOpenPositionStateForCurrentTenant() {
        if (tradeDecisionRepository == null || stateRestoreLimit <= 0) {
            return;
        }
        List<TradeDecisionEntity> recent = tradeDecisionRepository.findByActionIn(
                List.of("BUY", "SELL", "SKIP", "ERROR"),
                PageRequest.of(0, stateRestoreLimit, Sort.by(Sort.Direction.DESC, "executedAt"))
        ).getContent();

        // Descending, so the first BUY seen for a market is its latest entry.
        Map<String, OffsetDateTime> latestBuyAt = new HashMap<>();
        for (TradeDecisionEntity decision : recent) {
            if (decision == null || decision.getExecutedAt() == null
                    || !"BUY".equalsIgnoreCase(decision.getAction())) {
                continue;
            }
            String key = tenantScopedMarketKey(decision.getMarket());
            if (key == null) {
                continue;
            }
            if (latestBuyAt.putIfAbsent(key, decision.getExecutedAt()) == null) {
                lastEntryAtByMarket.putIfAbsent(key, decision.getExecutedAt());
                BigDecimal entryAtr = readDecimalDetail(decision, "entryAtrPct");
                if (entryAtr != null && entryAtr.compareTo(BigDecimal.ZERO) > 0) {
                    entryAtrPctByMarket.putIfAbsent(key, entryAtr);
                }
            }
        }

        for (TradeDecisionEntity decision : recent) {
            if (decision == null || decision.getTrailingHigh() == null || decision.getExecutedAt() == null) {
                continue;
            }
            String key = tenantScopedMarketKey(decision.getMarket());
            OffsetDateTime entryAt = key == null ? null : latestBuyAt.get(key);
            if (entryAt == null || !decision.getExecutedAt().isAfter(entryAt)) {
                continue;
            }
            if (decision.getTrailingHigh().compareTo(BigDecimal.ZERO) > 0) {
                // Descending order, so the first match is the most recent running maximum.
                trailingHighByMarket.putIfAbsent(key, decision.getTrailingHigh());
            }
        }
    }

    private BigDecimal readDecimalDetail(TradeDecisionEntity decision, String field) {
        if (decision == null || decision.getDetails() == null || decision.getDetails().isBlank()) {
            return null;
        }
        try {
            Object value = tradeDecisionService.parseDetails(decision.getDetails()).get(field);
            return value == null ? null : new BigDecimal(String.valueOf(value));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private void restoreDailyLossBaselineForCurrentTenant() {
        if (tradeDecisionRepository == null || dailyLossLimitPct <= 0) {
            return;
        }
        String tenantKey = currentTenantKey();
        if (dailyLossBaselinesByTenant.containsKey(tenantKey)) {
            return;
        }

        LocalDate today = LocalDate.now();
        OffsetDateTime dayStart = today.atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
        List<TradeDecisionEntity> todays = tradeDecisionRepository
                .findByActionInAndExecutedAtGreaterThanEqualAndExecutedAtLessThanOrderByExecutedAtAsc(
                        List.of("BUY", "SELL", "SKIP", "ERROR"),
                        dayStart,
                        dayStart.plusDays(1)
                );

        for (TradeDecisionEntity decision : todays) {
            BigDecimal baseline = readDailyLossBaseline(decision, today);
            if (baseline != null) {
                dailyLossBaselinesByTenant.put(tenantKey, new DailyLossBaseline(today, baseline));
                log.info("Restored daily-loss baseline {} for tenant {}", baseline, tenantKey);
                return;
            }
        }
    }

    private BigDecimal readDailyLossBaseline(TradeDecisionEntity decision, LocalDate today) {
        if (decision == null || decision.getDetails() == null || decision.getDetails().isBlank()) {
            return null;
        }
        try {
            Map<String, Object> parsed = tradeDecisionService.parseDetails(decision.getDetails());
            Object date = parsed.get("dailyLossBaselineDate");
            Object value = parsed.get("dailyLossBaselineKrw");
            if (date == null || value == null || !today.toString().equals(String.valueOf(date))) {
                return null;
            }
            BigDecimal baseline = new BigDecimal(String.valueOf(value));
            return baseline.compareTo(BigDecimal.ZERO) > 0 ? baseline : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    @Scheduled(fixedDelayString = "${engine.tick-ms:5000}")
    public void scheduledTick() {
        List<String> tenants = tenantDatabaseProvisioningService.listKnownTenantDatabases();
        for (String tenantDatabase : tenants) {
            try {
                TenantContext.runWithTenantDatabase(tenantDatabase, () -> {
                    if (!engineService.isRunning()) {
                        logSchedulerSkip(tenantDatabase, "engine_off", null, List.of());
                        return;
                    }
                    TradingAccessService.AutomatedTradingAccess access =
                            tradingAccessService.evaluateAutomatedTradingAccessForCurrentTenant();
                    if (!access.allowed()) {
                        String resolvedTenant = access.tenantDatabase() == null || access.tenantDatabase().isBlank()
                                ? tenantDatabase
                                : access.tenantDatabase();
                        logSchedulerSkip(resolvedTenant, access.reason(), access.userId(), access.candidateUserIds());
                        return;
                    }
                    runOnce(access.userId());
                });
            } catch (RuntimeException ex) {
                // Keep ticking other tenants even when one tenant fails.
                log.warn("Auto-trade tick skipped for tenant {}: {}", tenantDatabase, ex.getMessage());
            }
        }
    }

    public AutoTradeResult runOnce() {
        return runOnce(null);
    }

    public AutoTradeResult runOnce(Long userId) {
        AtomicBoolean running = runningFlag();
        if (!running.compareAndSet(false, true)) {
            return new AutoTradeResult(OffsetDateTime.now().toString(), List.of());
        }

        try {
            restoreExitStateIfNeeded();
            OffsetDateTime now = OffsetDateTime.now();
            if (isBackoffActive(SYSTEM_KEY, now)) {
                AutoTradeAction action = new AutoTradeAction(SYSTEM_KEY, "SKIP", "backoff", null, null, null, null, null);
                recordDecision(SYSTEM_KEY, action, null, null, null, null, null, null);
                return new AutoTradeResult(now.toString(), List.of(action));
            }

            StrategyConfig config = strategyService.getConfig();
            if (!config.enabled()) {
                return new AutoTradeResult(now.toString(), List.of());
            }
            List<String> markets = strategyService.configuredMarkets(userId);
            if (markets == null || markets.isEmpty()) {
                return new AutoTradeResult(now.toString(), List.of());
            }
            StrategyMarketOverrides runtimeOverrides = strategyService.getMarketOverridesSnapshot(userId);
            tickOverridesByTenant.put(currentTenantKey(), runtimeOverrides);

            Map<String, AccountSnapshot> accounts;
            try {
                accounts = loadAccounts();
                resetFailure(SYSTEM_KEY);
            } catch (RuntimeException ex) {
                recordFailure(SYSTEM_KEY, now);
                String reason = resolveSystemErrorReason(ex);
                if ("api_auth_ip_block".equals(reason)) {
                    log.warn(
                            "Auto-trade account load blocked tenant={} reason={} message={}",
                            currentTenantDatabase(),
                            reason,
                            truncate(safeErrorMessage(ex), 200)
                    );
                }
                AutoTradeAction action = new AutoTradeAction(
                        SYSTEM_KEY,
                        "ERROR",
                        reason,
                        null,
                        null,
                        null,
                        null,
                        null
                );
                recordDecision(SYSTEM_KEY, action, null, null, null, null, null, null);
                return new AutoTradeResult(now.toString(), List.of(action));
            }
            // Cross-sectional momentum selection, when enabled, decides which markets may take NEW
            // positions. Held markets are unioned back in so a name that drops out of the universe keeps
            // its stop-loss evaluated instead of being orphaned.
            if (universeSelectionService.isEnabled()) {
                markets = universeSelectionService.resolveTradableMarkets(markets, heldMarkets(accounts));
                if (markets.isEmpty()) {
                    AutoTradeAction action = new AutoTradeAction(
                            SYSTEM_KEY, "SKIP", "universe_empty", null, null, null, null, null);
                    recordDecision(SYSTEM_KEY, action, config, StrategyProfile.from(config.profile()), null, null, null, null);
                    return new AutoTradeResult(now.toString(), List.of(action));
                }
            }

            Map<String, RegimeSnapshot> regimeByMarket = new HashMap<>();
            DailyLossStatus dailyLossStatus = evaluateDailyLossStatus(accounts);
            BigDecimal totalAssetKrw = dailyLossStatus.currentAssetKrw() != null
                    && dailyLossStatus.currentAssetKrw().compareTo(BigDecimal.ZERO) > 0
                    ? dailyLossStatus.currentAssetKrw()
                    : estimateTotalAssetKrw(accounts);
            BigDecimal remainingCashBudget = accounts.getOrDefault("KRW", AccountSnapshot.empty()).balance();

            List<AutoTradeAction> actions = new ArrayList<>();
            if (dailyLossStatus.active()) {
                AutoTradeAction action = new AutoTradeAction(
                        SYSTEM_KEY,
                        "SKIP",
                        dailyLossStatus.reason(),
                        null,
                        null,
                        null,
                        null,
                        null
                );
                actions.add(action);
                recordDecision(SYSTEM_KEY, action, config, StrategyProfile.from(config.profile()), null, null, null, null);
            }
            for (String market : markets) {
                RegimeSnapshot regime = regimeByMarket.computeIfAbsent(market, this::evaluateRegime);
                StrategyConfig baseMarketConfig = resolveConfigForMarket(market, config, runtimeOverrides);
                StrategyProfile profile = resolveProfileForMarket(market, baseMarketConfig, runtimeOverrides);
                SignalTuning baseTuning = resolveSignalTuning(profile);
                RegimeAdjustedContext adjustedContext = resolveRegimeAdjustedContext(baseMarketConfig, baseTuning, regime);
                StrategyConfig marketConfig = adjustedContext.config();
                SignalTuning tuning = adjustedContext.tuning();
                BigDecimal positionSizeMultiplier = adjustedContext.positionSizeMultiplier();
                String regimeMode = adjustedContext.mode();
                BigDecimal marketMaxOrderKrw = resolveMarketMaxOrderKrw(market, marketConfig, runtimeOverrides);
                boolean tradePaused = isTradePaused(market, runtimeOverrides);

                // Backoff gates ENTRIES only. It used to short-circuit the market before the position was
                // even inspected, so a handful of transient Upbit errors (the delay escalates to 300s) left
                // an open position with no stop-loss evaluation for up to five minutes.
                boolean backoffActive = isBackoffActive(market, now);

                MarketIndicators indicators = null;
                try {
                    String currency = extractCurrency(market);
                    if (currency == null) {
                        AutoTradeAction action = new AutoTradeAction(market, "SKIP", "invalid market", null, null, null, null, null);
                        actions.add(action);
                        recordDecision(market, action, marketConfig, profile, null, tuning, regime, marketMaxOrderKrw);
                        resetFailure(market);
                        continue;
                    }

                    AccountSnapshot position = accounts.getOrDefault(currency, AccountSnapshot.empty());
                    BigDecimal total = position.total();

                    if (total.compareTo(BigDecimal.ZERO) <= 0) {
                        String tenantMarketKey = tenantScopedMarketKey(market);
                        if (tenantMarketKey != null) {
                            lastPartialTakeProfitAt.remove(tenantMarketKey);
                            trailingHighByMarket.remove(tenantMarketKey);
                            lastEntryAtByMarket.remove(tenantMarketKey);
                            entryAtrPctByMarket.remove(tenantMarketKey);
                        }
                        if (backoffActive) {
                            AutoTradeAction action = new AutoTradeAction(
                                    market,
                                    "SKIP",
                                    "backoff",
                                    null,
                                    null,
                                    null,
                                    null,
                                    null
                            );
                            actions.add(action);
                            recordDecision(market, action, marketConfig, profile, null, tuning, regime, marketMaxOrderKrw);
                            continue;
                        }
                        if (dailyLossStatus.active()) {
                            AutoTradeAction action = new AutoTradeAction(
                                    market,
                                    "SKIP",
                                    dailyLossStatus.reason(),
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
                                    marketMaxOrderKrw
                            );
                            resetFailure(market);
                            continue;
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
                                    marketMaxOrderKrw
                            );
                            resetFailure(market);
                            continue;
                        }
                    }

                    indicators = fetchIndicators(market, tuning);

                    if (total.compareTo(BigDecimal.ZERO) > 0) {
                        AutoTradeAction sellAction = handleSell(
                                market,
                                position,
                                marketConfig,
                                indicators,
                                tuning,
                                profile
                        );

                        if (sellAction != null) {
                            actions.add(sellAction);
                            recordDecision(
                                    market,
                                    sellAction,
                                    marketConfig,
                                    profile,
                                    indicators,
                                    tuning,
                                    regime,
                                    marketMaxOrderKrw
                            );
                        }
                        resetFailure(market);
                        continue;
                    }

                    AutoTradeAction action = handleBuy(
                            market,
                            remainingCashBudget,
                            marketConfig,
                            indicators,
                            tuning,
                            marketMaxOrderKrw,
                            BigDecimal.ZERO,
                            profile,
                            totalAssetKrw,
                            dailyLossStatus,
                            positionSizeMultiplier,
                            regimeMode
                    );
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
                                marketMaxOrderKrw
                        );
                        if ("BUY".equalsIgnoreCase(action.action()) && action.funds() != null) {
                            remainingCashBudget = remainingCashBudget.subtract(action.funds());
                            if (remainingCashBudget.compareTo(BigDecimal.ZERO) < 0) {
                                remainingCashBudget = BigDecimal.ZERO;
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
                            marketMaxOrderKrw
                    );
                }
            }
            return new AutoTradeResult(now.toString(), actions);
        } finally {
            running.set(false);
        }
    }

    /**
     * Emergency liquidation: market-sell every non-KRW balance that has a tradeable KRW pair.
     *
     * Stopping the engine only stops new decisions - it leaves open positions with nothing watching the
     * stop-loss. This is the "get me out" control that was missing entirely. It deliberately ignores the
     * per-market order cooldown (an emergency should not be rate-limited by a comfort setting) but still
     * refuses to stack a second SELL on top of one already in flight.
     */
    public AutoTradeResult liquidateAll(String reason) {
        String exitReason = reason == null || reason.isBlank() ? "panic_exit" : reason.trim();
        OffsetDateTime now = OffsetDateTime.now();
        List<AutoTradeAction> actions = new ArrayList<>();

        Map<String, AccountSnapshot> accounts;
        try {
            accounts = loadAccounts();
        } catch (RuntimeException ex) {
            AutoTradeAction action = new AutoTradeAction(
                    SYSTEM_KEY, "ERROR", resolveSystemErrorReason(ex), null, null, null, null, null);
            recordDecision(SYSTEM_KEY, action, null, null, null, null, null, null);
            return new AutoTradeResult(now.toString(), List.of(action));
        }

        for (Map.Entry<String, AccountSnapshot> entry : accounts.entrySet()) {
            String currency = entry.getKey();
            if (currency == null || "KRW".equalsIgnoreCase(currency)) {
                continue;
            }
            BigDecimal available = entry.getValue().balance();
            if (available == null || available.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            String market = "KRW-" + currency.toUpperCase();
            AutoTradeAction action;
            try {
                BigDecimal currentPrice = fetchCurrentPrice(market);
                if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
                    action = new AutoTradeAction(market, "SKIP", "price unavailable", null, available, null, null, null);
                } else if (currentPrice.multiply(available).compareTo(resolveMinOrderKrw(market, "SELL")) < 0) {
                    action = new AutoTradeAction(market, "SKIP", "below min order", currentPrice, available, null, null, null);
                } else if (hasOpenRequest(market, "SELL")) {
                    action = new AutoTradeAction(market, "SKIP", "pending", currentPrice, available, null, null, null);
                } else {
                    OrderRequest request = new OrderRequest(market, "SELL", "MARKET", null, available, null, null);
                    OrderResponse response = orderService.create(request);
                    recordSellEvent(market, exitReason, response);
                    clearMarketRuntimeState(market);
                    action = new AutoTradeAction(
                            market, "SELL", exitReason, currentPrice, available, null,
                            response.orderId(), response.requestStatus());
                }
            } catch (RuntimeException ex) {
                action = new AutoTradeAction(
                        market, "ERROR", truncate(ex.getMessage(), 200), null, available, null, null, null);
            }
            actions.add(action);
            recordDecision(market, action, null, null, null, null, null, null);
        }

        return new AutoTradeResult(now.toString(), actions);
    }

    private void clearMarketRuntimeState(String market) {
        String key = tenantScopedMarketKey(market);
        if (key == null) {
            return;
        }
        trailingHighByMarket.remove(key);
        lastPartialTakeProfitAt.remove(key);
        lastEntryAtByMarket.remove(key);
        entryAtrPctByMarket.remove(key);
    }

    /** Markets with a non-zero balance right now, as KRW pair codes. */
    private List<String> heldMarkets(Map<String, AccountSnapshot> accounts) {
        List<String> held = new ArrayList<>();
        if (accounts == null) {
            return held;
        }
        for (Map.Entry<String, AccountSnapshot> entry : accounts.entrySet()) {
            String currency = entry.getKey();
            if (currency == null || "KRW".equalsIgnoreCase(currency)) {
                continue;
            }
            AccountSnapshot snapshot = entry.getValue();
            if (snapshot != null && snapshot.total().compareTo(BigDecimal.ZERO) > 0) {
                held.add("KRW-" + currency.toUpperCase());
            }
        }
        return held;
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
                chooseRatio(ratios.momentumExitPct(), baseConfig.momentumExitPct()),
                baseConfig.riskPerTradePct()
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
            StrategyMarketOverrides runtimeOverrides
    ) {
        if (runtimeOverrides != null && runtimeOverrides.profileByMarket() != null) {
            String override = runtimeOverrides.profileByMarket().get(market);
            if (override != null && !override.isBlank()) {
                return StrategyProfile.from(override);
            }
        }
        return config == null ? StrategyProfile.BALANCED : StrategyProfile.from(config.profile());
    }

    private BigDecimal resolveMarketMaxOrderKrw(
            String market,
            StrategyConfig config,
            StrategyMarketOverrides runtimeOverrides
    ) {
        BigDecimal fallback = config == null
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(Math.max(config.maxOrderKrw(), 0.0));
        if (runtimeOverrides == null || runtimeOverrides.maxOrderKrwByMarket() == null) {
            return fallback;
        }
        Double override = runtimeOverrides.maxOrderKrwByMarket().get(market);
        if (override == null || Double.isNaN(override) || Double.isInfinite(override) || override <= 0.0) {
            return fallback;
        }
        return BigDecimal.valueOf(override);
    }

    private boolean isTradePaused(String market, StrategyMarketOverrides runtimeOverrides) {
        return runtimeOverrides != null
                && runtimeOverrides.tradePausedByMarket() != null
                && Boolean.TRUE.equals(runtimeOverrides.tradePausedByMarket().get(market));
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
        // 0 disables the overextension filter entirely. It must stay reachable: requiring
        // price > 20-bar high AND price <= MA_long x (1 + maxExtension) are contradictory demands, and
        // clamping the floor to 0.2 made the filter impossible to switch off.
        maxExtension = maxExtension <= 0 ? 0.0 : clamp(maxExtension, 0.2, 5.0);
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
            BigDecimal latestHigh
    ) {
        String marketKey = tenantScopedMarketKey(market);
        if (marketKey == null) {
            return null;
        }
        // Only grow the trailing high from prices we have actually observed since entry.
        BigDecimal candidate = maxPositive(avgBuyPrice, currentPrice, latestHigh);
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

    /**
     * Upbit takes at most 8 decimal places for volume. available.multiply(fraction) carries the balance's
     * scale plus the fraction's, so a partial exit produced up to 16 and the order was rejected on
     * format. Rounds DOWN so a rounding step can never try to sell more than is held.
     */
    private static BigDecimal normalizeVolume(BigDecimal volume) {
        if (volume == null) {
            return null;
        }
        return volume.setScale(8, RoundingMode.DOWN);
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
            SignalTuning tuning,
            StrategyProfile profile
    ) {
        BigDecimal available = position.balance();
        if (available.compareTo(BigDecimal.ZERO) <= 0) {
            return new AutoTradeAction(market, "SKIP", "no available balance", null, null, null, null, null);
        }

        BigDecimal avgBuyPrice = position.avgBuyPrice();
        if (avgBuyPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return new AutoTradeAction(market, "SKIP", "avg_buy_price missing", null, available, null, null, null);
        }

        boolean hasPostEntryClosedCandle = hasPostEntryClosedCandle(market, indicators);
        BigDecimal currentPrice = fetchCurrentPrice(market);
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            currentPrice = indicators == null ? null : indicators.currentPrice();
        }
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return new AutoTradeAction(market, "SKIP", "price unavailable", null, available, null, null, null);
        }

        BigDecimal minTotal = resolveMinOrderKrw(market, "SELL");
        BigDecimal estimatedValue = currentPrice.multiply(available);
        if (estimatedValue.compareTo(minTotal) < 0) {
            return new AutoTradeAction(market, "SKIP", "below min order", currentPrice, available, estimatedValue, null, null);
        }

        BigDecimal stopLossPct = resolveConfiguredStopLossPct(market, config, indicators);
        BigDecimal trailingStopPct = resolveConfiguredTrailingStopPct(market, config, indicators);
        BigDecimal trailingArmPct = resolveConfiguredTrailingArmPct(market, config, indicators);

        BigDecimal stopLossThreshold = avgBuyPrice.multiply(percentFactor(-decimalToDouble(stopLossPct, config.stopLossPct())));
        BigDecimal trailingStopThreshold = null;
        BigDecimal trailingArmThreshold = avgBuyPrice.multiply(percentFactor(decimalToDouble(
                trailingArmPct,
                resolveTrailingArmPct(config)
        )));
        BigDecimal entryTrailingHigh = updateTrailingHigh(
                market,
                avgBuyPrice,
                currentPrice,
                hasPostEntryClosedCandle && indicators != null ? indicators.latestHigh() : null
        );
        if (trailingStopPct != null
                && trailingStopPct.compareTo(BigDecimal.ZERO) > 0
                && entryTrailingHigh != null
                && entryTrailingHigh.compareTo(trailingArmThreshold) >= 0) {
            trailingStopThreshold = entryTrailingHigh.multiply(percentFactor(-trailingStopPct.doubleValue()));
        }

        // Protective exits always liquidate. stopExitPct is a position fraction, and a non-positive value
        // used to make submitSellByPct return "<reason>_disabled" — silently turning the stop-loss off for
        // the market. A stop that can be switched off by a sizing field is not a stop.
        double protectiveExitPct = config.stopExitPct() > 0 ? config.stopExitPct() : 100.0;

        if (currentPrice.compareTo(stopLossThreshold) <= 0) {
            return submitSellByPct(market, available, currentPrice, protectiveExitPct, "stop_loss", true, minTotal);
        }
        if (trailingStopThreshold != null && currentPrice.compareTo(trailingStopThreshold) <= 0) {
            return submitSellByPct(market, available, currentPrice, protectiveExitPct, "trailing_stop", true, minTotal);
        }

        // Take-profit: bank a slice at the configured target and let the remainder ride the trailing stop.
        // takeProfitPct previously only fed the trailing-arm calculation, where MAX_TRAILING_ARM_PCT capped
        // it at 1.2 — so any value above 1.2% was inert.
        if (config.takeProfitPct() > 0) {
            BigDecimal takeProfitThreshold = avgBuyPrice.multiply(percentFactor(config.takeProfitPct()));
            if (currentPrice.compareTo(takeProfitThreshold) >= 0
                    && canTakePartialProfit(market, OffsetDateTime.now())) {
                AutoTradeAction partial = attemptPartialTakeProfit(
                        market,
                        available,
                        currentPrice,
                        config.partialTakeProfitPct(),
                        minTotal
                );
                if (partial != null) {
                    return partial;
                }
            }
        }

        if (indicators != null
                && indicators.breakdownLevel() != null
                && currentPrice.compareTo(indicators.breakdownLevel()) <= 0) {
            return submitSellByPct(market, available, currentPrice, 100.0, "donchian_exit", false, minTotal);
        }

        // Trend break: price closed back under the slow MA that justified the entry.
        if (config.trendExitPct() > 0
                && indicators != null
                && indicators.maLong() != null
                && currentPrice.compareTo(indicators.maLong()) < 0) {
            return submitSellByPct(market, available, currentPrice, config.trendExitPct(), "trend_break", false, minTotal);
        }

        // Momentum reversal: RSI rolled under its sell threshold and MACD histogram turned negative.
        if (config.momentumExitPct() > 0
                && indicators != null
                && indicators.rsi() != null
                && indicators.macdHistogram() != null
                && indicators.rsi().doubleValue() < tuning.rsiSellThreshold()
                && indicators.macdHistogram().compareTo(BigDecimal.ZERO) < 0) {
            return submitSellByPct(
                    market,
                    available,
                    currentPrice,
                    config.momentumExitPct(),
                    "momentum_reversal",
                    false,
                    minTotal
            );
        }

        return new AutoTradeAction(market, "SKIP", "no signal", currentPrice, available, null, null, null);
    }

    private AutoTradeAction handleBuy(
            String market,
            BigDecimal cash,
            StrategyConfig config,
            MarketIndicators indicators,
            SignalTuning tuning,
            BigDecimal marketMaxOrderKrw,
            BigDecimal currentPositionValueKrw,
            StrategyProfile profile,
            BigDecimal totalAssetKrw,
            DailyLossStatus dailyLossStatus,
            BigDecimal regimeSizeMultiplier,
            String regimeMode
    ) {
        if (indicators == null || indicators.maShort() == null || indicators.maLong() == null || indicators.currentPrice() == null) {
            return new AutoTradeAction(market, "SKIP", "insufficient candles", null, null, null, null, null);
        }
        HtfTrendSnapshot htfTrend = evaluateHigherTimeframeTrend(market);
        if (htfTrend != null && !htfTrend.allowEntries()) {
            return new AutoTradeAction(
                    market,
                    "SKIP",
                    "htf_filter:" + htfTrend.reason(),
                    indicators.currentPrice(),
                    null,
                    null,
                    null,
                    null
            );
        }
        BigDecimal orderFunds = min(cash, marketMaxOrderKrw);
        orderFunds = applyDynamicPositionSizing(
                orderFunds,
                config,
                totalAssetKrw,
                market,
                indicators,
                indicators.volatilityPct(),
                dailyLossStatus,
                regimeSizeMultiplier
        );
        orderFunds = applyCostBuffer(orderFunds);
        // Every other sizing stage only shrinks, but the regime multiplier (default risk-on 1.20) can grow
        // the order past the cap it started from. Re-apply the hard limits so the user's per-market cap and
        // the available KRW balance always have the last word.
        orderFunds = min(orderFunds, min(cash, marketMaxOrderKrw));
        if (marketMaxOrderKrw != null
                && marketMaxOrderKrw.compareTo(BigDecimal.ZERO) > 0
                && currentPositionValueKrw != null
                && currentPositionValueKrw.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal remainingBudget = marketMaxOrderKrw.subtract(currentPositionValueKrw);
            if (remainingBudget.compareTo(BigDecimal.ZERO) <= 0) {
                return new AutoTradeAction(market, "SKIP", "market_buy_cap_reached", null, null, orderFunds, null, null);
            }
            if (orderFunds.compareTo(remainingBudget) > 0) {
                orderFunds = remainingBudget;
            }
        }
        BigDecimal minTotal = resolveMinOrderKrw(market, "BUY");
        if (orderFunds.compareTo(minTotal) < 0) {
            return new AutoTradeAction(market, "SKIP", "insufficient cash", null, null, orderFunds, null, null);
        }
        BuySignalDecision buySignalDecision = resolveSignalModelForCurrentTenant(market).evaluateBuy(new BuySignalContext(
                indicators,
                tuning,
                bollingerWindow > 1 ? squeezeMaxBandwidthPct : 0.0
        ));
        if (!buySignalDecision.proceed()) {
            return new AutoTradeAction(
                    market,
                    "SKIP",
                    buySignalDecision.reason(),
                    indicators.currentPrice(),
                    null,
                    buySignalDecision.includeOrderFundsInSkip() ? orderFunds : null,
                    null,
                    null
            );
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
        recordEntryEvent(market, response);
        recordEntryAtrPct(market, indicators, response);

        return new AutoTradeAction(
                market,
                "BUY",
                appendRegimeMode(buySignalDecision.reason(), regimeMode),
                null,
                null,
                orderFunds,
                response.orderId(),
                response.requestStatus()
        );
    }

    private BigDecimal applyDynamicPositionSizing(
            BigDecimal funds,
            StrategyConfig config,
            BigDecimal totalAssetKrw,
            String market,
            MarketIndicators indicators,
            BigDecimal volatilityPct,
            DailyLossStatus dailyLossStatus,
            BigDecimal regimeSizeMultiplier
    ) {
        if (funds == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal sizedFunds = funds.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : funds;

        if (config != null
                && totalAssetKrw != null
                && totalAssetKrw.compareTo(BigDecimal.ZERO) > 0
                && config.riskPerTradePct() > 0) {
            BigDecimal riskBudget = totalAssetKrw.multiply(BigDecimal.valueOf(config.riskPerTradePct()))
                    .divide(HUNDRED, 8, RoundingMode.HALF_UP);
            if (riskBudget.compareTo(BigDecimal.ZERO) > 0 && sizedFunds.compareTo(riskBudget) > 0) {
                BigDecimal riskScale = riskBudget.divide(sizedFunds, 8, RoundingMode.HALF_UP);
                if (riskScale.compareTo(BigDecimal.valueOf(0.5)) < 0) {
                    riskScale = BigDecimal.valueOf(0.5);
                }
                sizedFunds = sizedFunds.multiply(riskScale);
            }
        }

        if (atrRiskSizingEnabled
                && config != null
                && totalAssetKrw != null
                && totalAssetKrw.compareTo(BigDecimal.ZERO) > 0
                && indicators != null) {
            BigDecimal atrStopLossPct = resolveAtrStopLossPct(market, config, indicators);
            if (atrStopLossPct != null && atrStopLossPct.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal riskBudget = totalAssetKrw.multiply(BigDecimal.valueOf(config.riskPerTradePct()))
                        .divide(HUNDRED, 8, RoundingMode.HALF_UP);
                BigDecimal stopLossFraction = atrStopLossPct.divide(HUNDRED, 8, RoundingMode.HALF_UP);
                if (riskBudget.compareTo(BigDecimal.ZERO) > 0 && stopLossFraction.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal cappedFunds = riskBudget.divide(stopLossFraction, 8, RoundingMode.HALF_UP);
                    if (cappedFunds.compareTo(BigDecimal.ZERO) > 0 && sizedFunds.compareTo(cappedFunds) > 0) {
                        sizedFunds = cappedFunds;
                    }
                }
            }
        }

        sizedFunds = applyVolatilityTarget(sizedFunds, volatilityPct);

        if (dailyLossStatus != null
                && dailyLossStatus.drawdownPct() != null
                && dailyLossStatus.drawdownPct().compareTo(BigDecimal.ZERO) > 0
                && dailyLossStatus.limitPct() > 0) {
            BigDecimal limitPct = BigDecimal.valueOf(dailyLossStatus.limitPct());
            if (limitPct.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal drawdownRatio = dailyLossStatus.drawdownPct()
                        .divide(limitPct, 8, RoundingMode.HALF_UP);
                if (drawdownRatio.compareTo(BigDecimal.ZERO) < 0) {
                    drawdownRatio = BigDecimal.ZERO;
                }
                if (drawdownRatio.compareTo(BigDecimal.valueOf(2.0)) > 0) {
                    drawdownRatio = BigDecimal.valueOf(2.0);
                }
                BigDecimal drawdownScale = BigDecimal.ONE.subtract(drawdownRatio.multiply(BigDecimal.valueOf(0.5)));
                if (drawdownScale.compareTo(BigDecimal.valueOf(0.35)) < 0) {
                    drawdownScale = BigDecimal.valueOf(0.35);
                }
                sizedFunds = sizedFunds.multiply(drawdownScale);
            }
        }

        if (regimeSizeMultiplier != null && regimeSizeMultiplier.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal boundedRegimeScale = BigDecimal.valueOf(
                    clamp(regimeSizeMultiplier.doubleValue(), 0.2, 2.0)
            );
            sizedFunds = sizedFunds.multiply(boundedRegimeScale);
        }

        if (sizedFunds.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return sizedFunds;
    }

    private RegimeAdjustedContext resolveRegimeAdjustedContext(
            StrategyConfig config,
            SignalTuning tuning,
            RegimeSnapshot regime
    ) {
        if (config == null || tuning == null || regime == null || !regime.allowEntries() || !regimeParameterSwitchEnabled) {
            return RegimeAdjustedContext.base(config, tuning);
        }

        boolean slopeStrong = regime.maLongSlopePct() != null
                && regime.maLongSlopePct().doubleValue() >= regimeRiskOnSlopePct;
        boolean volatilitySafe = regime.volatilityPct() == null
                || regime.volatilityPct().doubleValue() <= regimeRiskOnMaxVolatilityPct;
        boolean riskOn = slopeStrong && volatilitySafe;

        double takeProfitMultiplier = riskOn ? regimeRiskOnTakeProfitMultiplier : regimeCautionTakeProfitMultiplier;
        double stopLossMultiplier = riskOn ? regimeRiskOnStopLossMultiplier : regimeCautionStopLossMultiplier;
        double trailingStopMultiplier = riskOn ? regimeRiskOnTrailingStopMultiplier : regimeCautionTrailingStopMultiplier;
        double rsiBuyAdjust = riskOn ? regimeRiskOnRsiBuyAdjust : regimeCautionRsiBuyAdjust;
        BigDecimal positionSizeMultiplier = BigDecimal.valueOf(
                riskOn ? regimeRiskOnSizeMultiplier : regimeCautionSizeMultiplier
        );

        StrategyConfig adjustedConfig = new StrategyConfig(
                config.enabled(),
                config.maxOrderKrw(),
                applyRatioMultiplier(config.takeProfitPct(), takeProfitMultiplier),
                applyRatioMultiplier(config.stopLossPct(), stopLossMultiplier),
                applyRatioMultiplier(config.trailingStopPct(), trailingStopMultiplier),
                config.partialTakeProfitPct(),
                config.profile(),
                config.stopExitPct(),
                config.trendExitPct(),
                config.momentumExitPct(),
                config.riskPerTradePct()
        );

        SignalTuning adjustedTuning = new SignalTuning(
                clamp(tuning.rsiBuyThreshold() + rsiBuyAdjust, 40.0, 80.0),
                tuning.rsiSellThreshold(),
                tuning.rsiOverbought(),
                tuning.minAdx(),
                tuning.minVolumeRatio(),
                tuning.breakoutPct(),
                tuning.minConfirmations(),
                tuning.maxExtensionPct(),
                tuning.minMaLongSlopePct()
        );

        return new RegimeAdjustedContext(
                adjustedConfig,
                adjustedTuning,
                positionSizeMultiplier,
                riskOn ? "regime_risk_on" : "regime_caution"
        );
    }

    private HtfTrendSnapshot evaluateHigherTimeframeTrend(String market) {
        String normalizedMarket = normalizeMarketKey(market);
        if (!htfConfirmEnabled) {
            return HtfTrendSnapshot.allow("htf_disabled", normalizedMarket, null, null, null, null, OffsetDateTime.now());
        }
        if (normalizedMarket == null) {
            return HtfTrendSnapshot.allow("htf_invalid_market", null, null, null, null, null, OffsetDateTime.now());
        }
        String cacheKey = tenantScopedKey("HTF" + TENANT_KEY_SEPARATOR + normalizedMarket);
        OffsetDateTime now = OffsetDateTime.now();
        HtfTrendSnapshot cached = htfTrendCache.get(cacheKey);
        if (cached != null && htfConfirmCacheMinutes > 0 && cached.fetchedAt() != null) {
            OffsetDateTime threshold = now.minusMinutes(htfConfirmCacheMinutes);
            if (cached.fetchedAt().isAfter(threshold)) {
                return cached;
            }
        }

        int requestCount = htfConfirmMaLong;
        if (htfConfirmSlopeLookback > 0) {
            requestCount = Math.max(requestCount, htfConfirmMaLong + htfConfirmSlopeLookback);
        }
        if (useClosedCandle) {
            requestCount += 1;
        }

        try {
            List<Map<String, Object>> candles = upbitService.fetchMinuteCandles(normalizedMarket, htfConfirmUnitMinutes, requestCount);
            List<BigDecimal> closes = extractSortedCloses(candles);
            if (useClosedCandle) {
                dropLast(closes);
            }
            if (closes.size() < htfConfirmMaLong) {
                return cacheHtfTrend(cacheKey, HtfTrendSnapshot.allow(
                        "htf_insufficient_data",
                        normalizedMarket,
                        closes.isEmpty() ? null : closes.get(closes.size() - 1),
                        null,
                        null,
                        null,
                        now
                ));
            }

            BigDecimal currentPrice = closes.get(closes.size() - 1);
            BigDecimal maShortValue = averageLast(closes, htfConfirmMaShort);
            BigDecimal maLongValue = averageLast(closes, htfConfirmMaLong);
            if (maShortValue == null || maLongValue == null || maLongValue.compareTo(BigDecimal.ZERO) <= 0) {
                return cacheHtfTrend(cacheKey, HtfTrendSnapshot.allow(
                        "htf_invalid_trend",
                        normalizedMarket,
                        currentPrice,
                        maShortValue,
                        maLongValue,
                        null,
                        now
                ));
            }

            BigDecimal slopePct = null;
            if (htfConfirmSlopeLookback > 0) {
                BigDecimal maLongPrev = averageLastWithOffset(closes, htfConfirmMaLong, htfConfirmSlopeLookback);
                if (maLongPrev != null && maLongPrev.compareTo(BigDecimal.ZERO) > 0) {
                    slopePct = maLongValue.subtract(maLongPrev)
                            .divide(maLongPrev, 8, RoundingMode.HALF_UP)
                            .multiply(HUNDRED);
                }
            }

            if (currentPrice.compareTo(maLongValue) <= 0 || maShortValue.compareTo(maLongValue) <= 0) {
                return cacheHtfTrend(cacheKey, HtfTrendSnapshot.block(
                        "htf_trend_off",
                        normalizedMarket,
                        currentPrice,
                        maShortValue,
                        maLongValue,
                        slopePct,
                        now
                ));
            }
            if (htfConfirmMinMaLongSlopePct > 0
                    && slopePct != null
                    && slopePct.doubleValue() < htfConfirmMinMaLongSlopePct) {
                return cacheHtfTrend(cacheKey, HtfTrendSnapshot.block(
                        "htf_slope_off",
                        normalizedMarket,
                        currentPrice,
                        maShortValue,
                        maLongValue,
                        slopePct,
                        now
                ));
            }

            return cacheHtfTrend(cacheKey, HtfTrendSnapshot.allow(
                    "htf_on",
                    normalizedMarket,
                    currentPrice,
                    maShortValue,
                    maLongValue,
                    slopePct,
                    now
            ));
        } catch (RuntimeException ex) {
            return cacheHtfTrend(cacheKey, HtfTrendSnapshot.allow(
                    "htf_unavailable",
                    normalizedMarket,
                    null,
                    null,
                    null,
                    null,
                    now
            ));
        }
    }

    private HtfTrendSnapshot cacheHtfTrend(String cacheKey, HtfTrendSnapshot snapshot) {
        if (cacheKey == null || snapshot == null) {
            return snapshot;
        }
        htfTrendCache.put(cacheKey, snapshot);
        return snapshot;
    }

    private static String appendRegimeMode(String reason, String regimeMode) {
        if (reason == null || reason.isBlank()) {
            return reason;
        }
        if (regimeMode == null || regimeMode.isBlank() || "regime_base".equalsIgnoreCase(regimeMode)) {
            return reason;
        }
        return reason + ":" + regimeMode;
    }

    private static double applyRatioMultiplier(double value, double multiplier) {
        if (value <= 0) {
            return value;
        }
        return value * multiplier;
    }

    /**
     * Per-market entry model, falling back to the signal.model default.
     *
     * A multi-market bot wants different models on different markets — a large-cap in a steady trend and
     * a thin alt breaking out of a squeeze are not the same problem — so the choice is a per-market
     * override rather than one global switch.
     */
    private TradeSignalModel resolveSignalModelForCurrentTenant(String market) {
        return resolveSignalModel(market, tickOverridesByTenant.get(currentTenantKey()));
    }

    private TradeSignalModel resolveSignalModel(String market, StrategyMarketOverrides runtimeOverrides) {
        String requested = null;
        if (market != null && runtimeOverrides != null && runtimeOverrides.signalModelByMarket() != null) {
            requested = runtimeOverrides.signalModelByMarket().get(market);
        }
        if (requested == null || requested.isBlank()) {
            requested = defaultSignalModel;
        }
        TradeSignalModel model = signalModels.get(requested);
        if (model == null) {
            log.warn("Unknown signal model '{}', falling back to {}", requested, UnifiedTrendSignalModel.NAME);
            return signalModels.get(UnifiedTrendSignalModel.NAME);
        }
        return model;
    }

    private void recordEntryEvent(String market, OrderResponse response) {
        if (!isAcceptedOrder(response)) {
            return;
        }
        String key = tenantScopedMarketKey(market);
        if (key == null) {
            return;
        }
        lastEntryAtByMarket.put(key, OffsetDateTime.now());
    }

    private void recordEntryAtrPct(String market, MarketIndicators indicators, OrderResponse response) {
        if (!isAcceptedOrder(response) || indicators == null || indicators.atrPct() == null) {
            return;
        }
        String key = tenantScopedMarketKey(market);
        if (key == null) {
            return;
        }
        entryAtrPctByMarket.put(key, indicators.atrPct());
    }

    private AutoTradeAction submitSell(String market, BigDecimal rawVolume, String reason) {
        BigDecimal volume = normalizeVolume(rawVolume);
        if (volume == null || volume.compareTo(BigDecimal.ZERO) <= 0) {
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
        BigDecimal volume = normalizeVolume(available.multiply(fraction));
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

    private boolean hasPostEntryClosedCandle(String market, MarketIndicators indicators) {
        String marketKey = tenantScopedMarketKey(market);
        if (marketKey == null) {
            return true;
        }
        OffsetDateTime entryAt = lastEntryAtByMarket.get(marketKey);
        if (entryAt == null) {
            return true;
        }
        OffsetDateTime latestClosedAt = indicators == null ? null : indicators.latestClosedAt();
        return latestClosedAt != null && latestClosedAt.isAfter(entryAt);
    }

    private static BigDecimal percentFactor(double percent) {
        BigDecimal pct = BigDecimal.valueOf(percent).divide(HUNDRED, 8, RoundingMode.HALF_UP);
        return BigDecimal.ONE.add(pct);
    }

    private static double resolveTrailingArmPct(StrategyConfig config) {
        if (config == null) {
            return MAX_TRAILING_ARM_PCT;
        }
        double cappedTakeProfit = config.takeProfitPct() > 0
                ? Math.min(config.takeProfitPct(), MAX_TRAILING_ARM_PCT)
                : MAX_TRAILING_ARM_PCT;
        return Math.max(config.trailingStopPct(), cappedTakeProfit);
    }

    private static double decimalToDouble(BigDecimal value, double fallback) {
        return value == null ? fallback : value.doubleValue();
    }

    private BigDecimal resolveConfiguredStopLossPct(String market, StrategyConfig config, MarketIndicators indicators) {
        if (config == null) {
            return BigDecimal.ZERO;
        }
        return resolveAtrBackedPct(
                market,
                indicators,
                atrStopLossMultiplier,
                BigDecimal.valueOf(config.stopLossPct()),
                BigDecimal.valueOf(0.2),
                BigDecimal.valueOf(25.0)
        );
    }

    private BigDecimal resolveConfiguredTrailingStopPct(String market, StrategyConfig config, MarketIndicators indicators) {
        if (config == null) {
            return BigDecimal.ZERO;
        }
        return resolveAtrBackedPct(
                market,
                indicators,
                atrTrailingStopMultiplier,
                BigDecimal.valueOf(config.trailingStopPct()),
                BigDecimal.valueOf(0.2),
                BigDecimal.valueOf(30.0)
        );
    }

    /**
     * The trailing stop must only arm once it can actually lock in a gain.
     *
     * With arm = 1.5xATR and trail = 1.8xATR (the previous defaults) the stop armed at
     * entry x (1 + 1.5xATR) and immediately sat at high x (1 - 1.8xATR), i.e. ~0.3xATR BELOW entry —
     * so the most common outcome ("ran up, came back") was a guaranteed loss, and there was no path
     * to banking a winner at all. Enforce arm >= trail + round-trip cost in code so a config change
     * cannot reintroduce the inversion.
     */
    private BigDecimal resolveConfiguredTrailingArmPct(String market, StrategyConfig config, MarketIndicators indicators) {
        if (config == null) {
            return BigDecimal.valueOf(MAX_TRAILING_ARM_PCT);
        }
        BigDecimal armPct = resolveAtrBackedPct(
                market,
                indicators,
                atrTrailingArmMultiplier,
                BigDecimal.valueOf(resolveTrailingArmPct(config)),
                BigDecimal.valueOf(0.2),
                BigDecimal.valueOf(20.0)
        );
        BigDecimal trailPct = resolveConfiguredTrailingStopPct(market, config, indicators);
        if (armPct == null || trailPct == null || trailPct.compareTo(BigDecimal.ZERO) <= 0) {
            return armPct;
        }
        BigDecimal roundTripCostPct = tradeCostRate.multiply(BigDecimal.valueOf(2)).multiply(HUNDRED);
        BigDecimal minimumArmPct = trailPct.add(roundTripCostPct);
        return armPct.compareTo(minimumArmPct) < 0 ? minimumArmPct : armPct;
    }

    private BigDecimal resolveAtrStopLossPct(String market, StrategyConfig config, MarketIndicators indicators) {
        return resolveConfiguredStopLossPct(market, config, indicators);
    }

    /**
     * ATR-derived exit threshold, falling back to the configured percentage.
     *
     * When ATR is available this REPLACES the user's configured stop-loss / trailing percentages, and
     * ATR is available essentially always — so the per-market 손절 % / 트레일링 % fields in the settings
     * UI had no effect and no flag existed to restore them (atr-risk-sizing-enabled gates position
     * sizing, not these thresholds). risk.atr-exit-thresholds-enabled makes that a choice.
     */
    private BigDecimal resolveAtrBackedPct(
            String market,
            MarketIndicators indicators,
            double multiplier,
            BigDecimal fallback,
            BigDecimal minPct,
            BigDecimal maxPct
    ) {
        if (!atrExitThresholdsEnabled) {
            return fallback;
        }
        BigDecimal atrPct = resolveAtrPctForMarket(market, indicators);
        if (atrPct == null || atrPct.compareTo(BigDecimal.ZERO) <= 0 || multiplier <= 0) {
            return fallback;
        }
        BigDecimal computed = atrPct.multiply(BigDecimal.valueOf(multiplier));
        if (computed.compareTo(minPct) < 0) {
            computed = minPct;
        }
        if (computed.compareTo(maxPct) > 0) {
            computed = maxPct;
        }
        return computed;
    }

    private BigDecimal resolveAtrPctForMarket(String market, MarketIndicators indicators) {
        String key = tenantScopedMarketKey(market);
        if (key != null) {
            BigDecimal stored = entryAtrPctByMarket.get(key);
            if (stored != null && stored.compareTo(BigDecimal.ZERO) > 0) {
                return stored;
            }
        }
        return indicators == null ? null : indicators.atrPct();
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

    private DailyLossStatus evaluateDailyLossStatus(Map<String, AccountSnapshot> accounts) {
        if (dailyLossLimitPct <= 0) {
            return DailyLossStatus.disabled();
        }

        BigDecimal currentTotalAssetKrw = estimateTotalAssetKrw(accounts);
        if (currentTotalAssetKrw.compareTo(BigDecimal.ZERO) <= 0) {
            return DailyLossStatus.disabled();
        }

        String tenantKey = currentTenantKey();
        LocalDate today = OffsetDateTime.now().toLocalDate();
        DailyLossBaseline baseline = dailyLossBaselinesByTenant.get(tenantKey);
        if (baseline == null || !today.equals(baseline.date())) {
            dailyLossBaselinesByTenant.put(tenantKey, new DailyLossBaseline(today, currentTotalAssetKrw));
            return DailyLossStatus.inactive(currentTotalAssetKrw);
        }

        BigDecimal baselineAsset = baseline.totalAssetKrw();
        if (baselineAsset == null || baselineAsset.compareTo(BigDecimal.ZERO) <= 0) {
            dailyLossBaselinesByTenant.put(tenantKey, new DailyLossBaseline(today, currentTotalAssetKrw));
            return DailyLossStatus.inactive(currentTotalAssetKrw);
        }

        BigDecimal drawdownPct = baselineAsset
                .subtract(currentTotalAssetKrw)
                .divide(baselineAsset, 8, RoundingMode.HALF_UP)
                .multiply(HUNDRED);
        if (drawdownPct.compareTo(BigDecimal.ZERO) < 0) {
            drawdownPct = BigDecimal.ZERO;
        }

        boolean active = drawdownPct.compareTo(BigDecimal.valueOf(dailyLossLimitPct)) >= 0;
        return new DailyLossStatus(active, drawdownPct, baselineAsset, currentTotalAssetKrw, dailyLossLimitPct);
    }

    private BigDecimal estimateTotalAssetKrw(Map<String, AccountSnapshot> accounts) {
        if (accounts == null || accounts.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = BigDecimal.ZERO;
        AccountSnapshot krwAccount = accounts.get("KRW");
        if (krwAccount != null) {
            total = total.add(krwAccount.total());
        }

        Map<String, AccountSnapshot> holdingsByMarket = new LinkedHashMap<>();
        for (Map.Entry<String, AccountSnapshot> entry : accounts.entrySet()) {
            String currency = entry.getKey();
            AccountSnapshot snapshot = entry.getValue();
            if (currency == null || snapshot == null || "KRW".equalsIgnoreCase(currency)) {
                continue;
            }
            if (snapshot.total().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String market = "KRW-" + currency.toUpperCase(Locale.ROOT);
            holdingsByMarket.put(market, snapshot);
        }

        if (holdingsByMarket.isEmpty()) {
            return total;
        }

        Map<String, Map<String, Object>> tickersByMarket;
        try {
            tickersByMarket = upbitService.fetchTickers(new ArrayList<>(new LinkedHashSet<>(holdingsByMarket.keySet())));
        } catch (RuntimeException ignored) {
            tickersByMarket = Map.of();
        }

        for (Map.Entry<String, AccountSnapshot> holding : holdingsByMarket.entrySet()) {
            String market = holding.getKey();
            AccountSnapshot snapshot = holding.getValue();
            BigDecimal quantity = snapshot.total();
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal price = BigDecimal.ZERO;
            Map<String, Object> ticker = tickersByMarket.get(market);
            if (ticker != null) {
                price = toDecimal(ticker.get("trade_price"));
            }
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                price = snapshot.avgBuyPrice();
            }
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            total = total.add(price.multiply(quantity));
        }

        return total;
    }

    private OrderChanceSnapshot fetchOrderChanceSnapshot(String market) {
        String normalizedMarket = normalizeMarketKey(market);
        String cacheKey = tenantScopedMarketKey(market);
        if (normalizedMarket == null || cacheKey == null) {
            return null;
        }
        OffsetDateTime now = OffsetDateTime.now();
        OrderChanceSnapshot cached = orderChanceCache.get(cacheKey);
        if (cached != null && orderChanceCacheMinutes > 0 && cached.fetchedAt() != null) {
            OffsetDateTime threshold = now.minusMinutes(orderChanceCacheMinutes);
            if (cached.fetchedAt().isAfter(threshold)) {
                return cached;
            }
        }

        try {
            Map<String, Object> response = upbitService.fetchOrderChance(normalizedMarket);
            OrderChanceSnapshot snapshot = parseOrderChanceSnapshot(response);
            if (snapshot != null) {
                orderChanceCache.put(cacheKey, snapshot);
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

    private static String normalizeMarketKey(String market) {
        if (market == null || market.isBlank()) {
            return null;
        }
        return market.trim().toUpperCase();
    }

    private AtomicBoolean runningFlag() {
        return runningByTenant.computeIfAbsent(currentTenantKey(), key -> new AtomicBoolean(false));
    }

    private String currentTenantKey() {
        String tenantDatabase = TenantContext.getTenantDatabase();
        if (tenantDatabase == null || tenantDatabase.isBlank()) {
            return DEFAULT_TENANT_KEY;
        }
        return tenantDatabase.trim();
    }

    private String tenantScopedKey(String key) {
        if (key == null || key.isBlank()) {
            return currentTenantKey();
        }
        return currentTenantKey() + TENANT_KEY_SEPARATOR + key;
    }

    private String tenantScopedMarketKey(String market) {
        String normalized = normalizeMarketKey(market);
        if (normalized == null) {
            return null;
        }
        return tenantScopedKey(normalized);
    }

    private RegimeSnapshot evaluateRegime(String market) {
        String regimeTarget = normalizeMarketKey(market);
        if (regimeTarget == null) {
            return RegimeSnapshot.block("regime_invalid_market", null, null, null, null, null, null);
        }
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

        int requestCount = useClosedCandle ? count + 1 : count;

        List<Map<String, Object>> candles;
        try {
            candles = upbitService.fetchMinuteCandles(regimeTarget, regimeTimeframeUnit, requestCount);
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

    private static BigDecimal estimatePositionValueKrw(AccountSnapshot position, BigDecimal currentPrice) {
        if (position == null || currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal qty = position.total();
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return qty.multiply(currentPrice);
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
        int breakdownWindow = Math.max(0, breakdownLookback);
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
        if (breakdownWindow > 1) {
            count = Math.max(count, breakdownWindow + 1);
        }
        if (trailingWindowSafe > 1) {
            count = Math.max(count, trailingWindowSafe);
        }
        if (atrRiskSizingEnabled) {
            count = Math.max(count, atrPeriod + 1);
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
        List<OffsetDateTime> closedAts = new ArrayList<>();
        for (Map<String, Object> candle : candles) {
            BigDecimal close = toDecimal(candle.get("trade_price"));
            BigDecimal high = toDecimal(candle.get("high_price"));
            BigDecimal low = toDecimal(candle.get("low_price"));
            BigDecimal quoteVolume = toDecimal(candle.get("candle_acc_trade_price"));
            OffsetDateTime closedAt = parseCandleClosedAt(candle.get("candle_date_time_utc"));
            if (close.compareTo(BigDecimal.ZERO) > 0
                    && high.compareTo(BigDecimal.ZERO) > 0
                    && low.compareTo(BigDecimal.ZERO) > 0) {
                closes.add(close);
                highs.add(high);
                lows.add(low);
                quoteVolumes.add(quoteVolume.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : quoteVolume);
                closedAts.add(closedAt);
            }
        }
        if (closes.size() < required) {
            return null;
        }
        reverseInPlace(closes);
        reverseInPlace(highs);
        reverseInPlace(lows);
        reverseInPlace(quoteVolumes);
        reverseInPlace(closedAts);

        if (useClosedCandle) {
            dropLast(closes);
            dropLast(highs);
            dropLast(lows);
            dropLast(quoteVolumes);
            dropLast(closedAts);
        }
        if (closes.size() < required) {
            return null;
        }

        BigDecimal currentPrice = closes.get(closes.size() - 1);
        BigDecimal latestHigh = highs.get(highs.size() - 1);
        OffsetDateTime latestClosedAt = closedAts.isEmpty() ? null : closedAts.get(closedAts.size() - 1);
        BigDecimal maShortValue = averageLast(closes, maShort);
        BigDecimal maLongValue = averageLast(closes, maLong);
        BigDecimal volatilityPct = null;
        if (targetVolPct != null && targetVolPct.compareTo(BigDecimal.ZERO) > 0 && volWindow > 1) {
            volatilityPct = computeVolatilityPct(closes, volWindow);
        }

        BigDecimal rsiValue = computeRsi(closes, rsiWindow);
        BigDecimal macdHistogram = computeMacdHistogram(closes, macdFastWindow, macdSlowWindow, macdSignalWindow);
        BigDecimal adxValue = computeAdx(highs, lows, closes, adxWindow);
        BigDecimal atrPct = computeAtrPct(highs, lows, closes, atrPeriod, currentPrice);
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

        BigDecimal breakdownLevel = null;
        if (breakdownWindow > 1 && lows.size() >= breakdownWindow + 1) {
            breakdownLevel = lowestLow(lows, breakdownWindow, true);
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
                atrPct,
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
                breakdownLevel,
                trailingHigh,
                maLongSlopePct,
                latestHigh,
                latestClosedAt
        );
    }

    private OffsetDateTime parseCandleClosedAt(Object value) {
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.plusMinutes(candleUnitMinutes);
        }
        String text = asString(value);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text).plusMinutes(candleUnitMinutes);
        } catch (DateTimeParseException ignored) {
            // Upbit minute candle timestamps are usually provided without an offset.
        }
        try {
            return LocalDateTime.parse(text).atOffset(ZoneOffset.UTC).plusMinutes(candleUnitMinutes);
        } catch (DateTimeParseException ex) {
            return null;
        }
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

    private static BigDecimal lowestLow(List<BigDecimal> lows, int window, boolean excludeLast) {
        if (lows == null || lows.isEmpty() || window <= 0) {
            return null;
        }
        int end = lows.size() - 1;
        if (excludeLast) {
            end -= 1;
        }
        if (end < 0) {
            return null;
        }
        int start = Math.max(0, end - window + 1);
        BigDecimal min = null;
        for (int i = start; i <= end; i++) {
            BigDecimal value = lows.get(i);
            if (value == null) {
                continue;
            }
            if (min == null || value.compareTo(min) < 0) {
                min = value;
            }
        }
        return min;
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

    private static BigDecimal computeAtrPct(
            List<BigDecimal> highs,
            List<BigDecimal> lows,
            List<BigDecimal> closes,
            int period,
            BigDecimal currentPrice
    ) {
        if (highs == null
                || lows == null
                || closes == null
                || period <= 0
                || currentPrice == null
                || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        if (highs.size() != lows.size() || lows.size() != closes.size() || closes.size() < period + 1) {
            return null;
        }

        int start = closes.size() - period;
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (int i = start; i < closes.size(); i++) {
            BigDecimal high = highs.get(i);
            BigDecimal low = lows.get(i);
            BigDecimal previousClose = closes.get(i - 1);
            if (high == null || low == null || previousClose == null) {
                continue;
            }
            BigDecimal range = high.subtract(low).abs();
            BigDecimal highGap = high.subtract(previousClose).abs();
            BigDecimal lowGap = low.subtract(previousClose).abs();
            BigDecimal tr = range.max(highGap).max(lowGap);
            sum = sum.add(tr);
            count++;
        }
        if (count == 0) {
            return null;
        }
        BigDecimal atr = sum.divide(BigDecimal.valueOf(count), 8, RoundingMode.HALF_UP);
        return atr.divide(currentPrice, 8, RoundingMode.HALF_UP).multiply(HUNDRED);
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
        BigDecimal volume = normalizeVolume(available.multiply(fraction));
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
            String key = tenantScopedMarketKey(market);
            if (key != null) {
                lastPartialTakeProfitAt.put(key, now);
            }
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
        String key = tenantScopedMarketKey(market);
        OffsetDateTime last = key == null ? null : lastStopLossAt.get(key);
        if (last == null) {
            return false;
        }
        return last.isAfter(OffsetDateTime.now().minusMinutes(stopLossCooldownMinutes));
    }

    private boolean isReentryCooldown(String market) {
        if (reentryCooldownMinutes <= 0) {
            return false;
        }
        String key = tenantScopedMarketKey(market);
        OffsetDateTime last = key == null ? null : lastExitAt.get(key);
        if (last == null) {
            return false;
        }
        return last.isAfter(OffsetDateTime.now().minusMinutes(reentryCooldownMinutes));
    }

    private boolean isStopLossGuardActive(String market) {
        if (stopLossGuardLockMinutes <= 0) {
            return false;
        }
        String key = tenantScopedMarketKey(market);
        OffsetDateTime until = key == null ? null : stopLossGuardUntilByMarket.get(key);
        if (until == null) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (now.isAfter(until)) {
            stopLossGuardUntilByMarket.remove(key);
            return false;
        }
        return true;
    }

    private void recordSellEvent(String market, String reason, OrderResponse response) {
        if (!isAcceptedOrder(response)) {
            return;
        }
        String key = tenantScopedMarketKey(market);
        if (key == null) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        lastExitAt.put(key, now);
        if (isStopLikeReason(reason)) {
            lastStopLossAt.put(key, now);
            if (stopLossGuardTriggerCount > 0 && stopLossGuardLookbackMinutes > 0 && stopLossGuardLockMinutes > 0) {
                registerStopLossEvent(key, now);
            }
        }
    }

    private void registerStopLossEvent(String marketKey, OffsetDateTime occurredAt) {
        if (marketKey == null) {
            return;
        }
        Deque<OffsetDateTime> events = stopLossEventsByMarket.computeIfAbsent(marketKey, key -> new ArrayDeque<>());
        synchronized (events) {
            OffsetDateTime threshold = occurredAt.minusMinutes(stopLossGuardLookbackMinutes);
            while (!events.isEmpty() && events.peekFirst().isBefore(threshold)) {
                events.pollFirst();
            }
            events.addLast(occurredAt);
            if (events.size() >= stopLossGuardTriggerCount) {
                stopLossGuardUntilByMarket.put(marketKey, occurredAt.plusMinutes(stopLossGuardLockMinutes));
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

            String trailingKey = tenantScopedMarketKey(market);
            BigDecimal entryTrailingHigh = trailingKey == null ? null : trailingHighByMarket.get(trailingKey);
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
            details.put("signalModel", resolveSignalModelForCurrentTenant(market).name());
            if (trailingKey != null) {
                // Persisted so a restart restores the stop geometry an open position was opened with.
                BigDecimal storedEntryAtrPct = entryAtrPctByMarket.get(trailingKey);
                if (storedEntryAtrPct != null) {
                    details.put("entryAtrPct", storedEntryAtrPct);
                }
            }
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
            details.put("breakoutLookback", breakoutLookback);
            details.put("breakdownLookback", breakdownLookback);
            details.put("trailingWindow", trailingWindow);
            details.put("atrPeriod", atrPeriod);
            details.put("atrStopLossMultiplier", atrStopLossMultiplier);
            details.put("atrTrailingStopMultiplier", atrTrailingStopMultiplier);
            details.put("atrTrailingArmMultiplier", atrTrailingArmMultiplier);
            details.put("atrRiskSizingEnabled", atrRiskSizingEnabled);
            details.put("atrExitThresholdsEnabled", atrExitThresholdsEnabled);
            details.put("entryTrailingHigh", entryTrailingHigh);
            if (indicators != null) {
                details.put("windowTrailingHigh", indicators.trailingHigh());
                details.put("atrPct", indicators.atrPct());
                details.put("breakdownLevel", indicators.breakdownLevel());
            }
            details.put("volatilityWindow", volatilityWindow);
            details.put("targetVolPct", targetVolPct);
            details.put("feeRate", feeRate);
            details.put("slippagePct", slippagePct);
            details.put("tradeCostRate", tradeCostRate);
            // Persist the day's equity baseline so the daily-loss circuit breaker survives a restart.
            DailyLossBaseline persistedBaseline = dailyLossBaselinesByTenant.get(currentTenantKey());
            if (persistedBaseline != null && persistedBaseline.totalAssetKrw() != null) {
                details.put("dailyLossBaselineDate", persistedBaseline.date().toString());
                details.put("dailyLossBaselineKrw", persistedBaseline.totalAssetKrw());
            }
            details.put("orderChanceCacheMinutes", orderChanceCacheMinutes);
            details.put("reentryCooldownMinutes", reentryCooldownMinutes);
            details.put("stopLossGuardLookbackMinutes", stopLossGuardLookbackMinutes);
            details.put("stopLossGuardTriggerCount", stopLossGuardTriggerCount);
            details.put("stopLossGuardLockMinutes", stopLossGuardLockMinutes);
            details.put("marketMaxOrderKrw", marketMaxOrderKrw);
            details.put("useClosedCandle", useClosedCandle);
            details.put("regimeFilterEnabled", regimeFilterEnabled);
            details.put("regimeMarket", regime != null ? regime.market() : normalizeMarketKey(market));
            details.put("regimeTimeframeUnit", regimeTimeframeUnit);
            if (config != null) {
                details.put("stopExitPct", config.stopExitPct());
                details.put("trendExitPct", config.trendExitPct());
                details.put("momentumExitPct", config.momentumExitPct());
                details.put("partialTakeProfitPct", config.partialTakeProfitPct());
                details.put("riskPerTradePct", config.riskPerTradePct());
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
            String marketKey = tenantScopedMarketKey(market);
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
        String key = tenantScopedMarketKey(market);
        OffsetDateTime last = key == null ? null : lastPartialTakeProfitAt.get(key);
        if (last == null) {
            return true;
        }
        return last.isBefore(now.minusMinutes(partialTakeProfitCooldownMinutes));
    }

    private static <T> void reverseInPlace(List<T> values) {
        for (int i = 0, j = values.size() - 1; i < j; i++, j--) {
            T tmp = values.get(i);
            values.set(i, values.get(j));
            values.set(j, tmp);
        }
    }

    private static <T> void dropLast(List<T> values) {
        if (values == null || values.size() <= 1) {
            return;
        }
        values.remove(values.size() - 1);
    }

    private record BollingerSnapshot(
            BigDecimal middle,
            BigDecimal upper,
            BigDecimal lower,
            BigDecimal bandwidthPct,
            BigDecimal percentB
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

    private record DailyLossBaseline(LocalDate date, BigDecimal totalAssetKrw) {
    }

    private record DailyLossStatus(
            boolean active,
            BigDecimal drawdownPct,
            BigDecimal baselineAssetKrw,
            BigDecimal currentAssetKrw,
            double limitPct
    ) {
        static DailyLossStatus disabled() {
            return new DailyLossStatus(false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0.0);
        }

        static DailyLossStatus inactive(BigDecimal currentAssetKrw) {
            BigDecimal safeCurrent = currentAssetKrw == null ? BigDecimal.ZERO : currentAssetKrw;
            return new DailyLossStatus(false, BigDecimal.ZERO, safeCurrent, safeCurrent, 0.0);
        }

        String reason() {
            BigDecimal safeDrawdown = drawdownPct == null ? BigDecimal.ZERO : drawdownPct.max(BigDecimal.ZERO);
            BigDecimal roundedDrawdown = safeDrawdown.setScale(2, RoundingMode.HALF_UP);
            BigDecimal roundedLimit = BigDecimal.valueOf(Math.max(0.0, limitPct)).setScale(2, RoundingMode.HALF_UP);
            return "daily_loss_guard:" + roundedDrawdown + ">=" + roundedLimit;
        }
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private void logSchedulerSkip(String tenantDatabase, String reason, Long userId, List<Long> candidateUserIds) {
        String tenant = tenantDatabase == null || tenantDatabase.isBlank() ? DEFAULT_TENANT_KEY : tenantDatabase;
        String resolvedReason = reason == null || reason.isBlank() ? "unknown" : reason;
        List<Long> candidates = candidateUserIds == null ? List.of() : candidateUserIds;
        if ("multiple_candidates".equals(resolvedReason)) {
            log.error(
                    "Auto-trade scheduler skip tenant={} reason={} userId={} candidateUserIds={}",
                    tenant,
                    resolvedReason,
                    userId,
                    candidates
            );
            return;
        }
        log.info(
                "Auto-trade scheduler skip tenant={} reason={} userId={} candidateUserIds={}",
                tenant,
                resolvedReason,
                userId,
                candidates
        );
    }

    private static String resolveSystemErrorReason(RuntimeException ex) {
        if (UpbitAuthNetworkStatusResolver.isIpNotWhitelisted(ex)) {
            return "api_auth_ip_block";
        }
        return truncate(safeErrorMessage(ex), 200);
    }

    private static String safeErrorMessage(Throwable ex) {
        if (ex == null) {
            return "unknown_error";
        }
        String message = ex.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        return ex.getClass().getSimpleName();
    }

    private static String currentTenantDatabase() {
        String tenant = TenantContext.getTenantDatabase();
        if (tenant == null || tenant.isBlank()) {
            return DEFAULT_TENANT_KEY;
        }
        return tenant.trim();
    }

    private boolean isBackoffActive(String key, OffsetDateTime now) {
        String scopedKey = tenantScopedKey(key);
        BackoffState state = backoffStates.get(scopedKey);
        if (state == null) {
            return false;
        }
        OffsetDateTime until = state.until();
        if (until == null || now.isAfter(until)) {
            backoffStates.remove(scopedKey);
            return false;
        }
        return true;
    }

    private void recordFailure(String key, OffsetDateTime now) {
        String scopedKey = tenantScopedKey(key);
        BackoffState previous = backoffStates.get(scopedKey);
        int failures = previous == null ? 1 : previous.consecutiveFailures() + 1;
        long delay = failureBackoffBaseSeconds;
        for (int i = 1; i < failures; i++) {
            delay = Math.min(failureBackoffMaxSeconds, delay * 2);
        }
        backoffStates.put(scopedKey, new BackoffState(failures, now.plusSeconds(delay)));
    }

    private void resetFailure(String key) {
        backoffStates.remove(tenantScopedKey(key));
    }

    private record AccountSnapshot(BigDecimal balance, BigDecimal locked, BigDecimal avgBuyPrice) {
        BigDecimal total() {
            return balance.add(locked);
        }

        static AccountSnapshot empty() {
            return new AccountSnapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    private record RegimeAdjustedContext(
            StrategyConfig config,
            SignalTuning tuning,
            BigDecimal positionSizeMultiplier,
            String mode
    ) {
        static RegimeAdjustedContext base(StrategyConfig config, SignalTuning tuning) {
            return new RegimeAdjustedContext(
                    config,
                    tuning,
                    BigDecimal.ONE,
                    "regime_base"
            );
        }
    }

    private record HtfTrendSnapshot(
            boolean allowEntries,
            String reason,
            String market,
            BigDecimal price,
            BigDecimal maShort,
            BigDecimal maLong,
            BigDecimal maLongSlopePct,
            OffsetDateTime fetchedAt
    ) {
        static HtfTrendSnapshot allow(
                String reason,
                String market,
                BigDecimal price,
                BigDecimal maShort,
                BigDecimal maLong,
                BigDecimal maLongSlopePct,
                OffsetDateTime fetchedAt
        ) {
            return new HtfTrendSnapshot(true, reason, market, price, maShort, maLong, maLongSlopePct, fetchedAt);
        }

        static HtfTrendSnapshot block(
                String reason,
                String market,
                BigDecimal price,
                BigDecimal maShort,
                BigDecimal maLong,
                BigDecimal maLongSlopePct,
                OffsetDateTime fetchedAt
        ) {
            return new HtfTrendSnapshot(false, reason, market, price, maShort, maLong, maLongSlopePct, fetchedAt);
        }
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
