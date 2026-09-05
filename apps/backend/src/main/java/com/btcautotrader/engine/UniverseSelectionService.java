package com.btcautotrader.engine;

import com.btcautotrader.upbit.UpbitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cross-sectional momentum universe selection.
 *
 * The engine traded a fixed, hand-typed market list. Cross-sectional momentum — rank the tradeable
 * universe by trailing return and hold only the leaders — is the best-documented edge available to a
 * long-only retail spot account, and it was entirely absent.
 *
 * This selects WHICH markets the engine is allowed to enter. The per-market trend/breakout logic still
 * decides WHEN. That combination (momentum screen, trend entry) is deliberate: a pure periodic rebalance
 * would need a different execution model than this tick engine, and bolting one on would be worse than
 * doing it properly later.
 *
 * Design notes:
 *  - Liquidity first, via ONE batched ticker call, so the expensive daily-candle fetch only runs for the
 *    handful of markets that clear the floor rather than all ~200 KRW pairs.
 *  - The most recent {@code skipDays} are excluded from the return window. Short-horizon crypto returns
 *    mean-revert, so including the last week is what turns a momentum screen into a reversal screen.
 *  - A hard risk-off gate: when the reference asset is below its long MA, the universe is EMPTY. Holding
 *    the strongest alt in a falling market is how cross-sectional momentum loses money.
 *  - Disabled by default. Enabling it changes which markets get traded, which is not something to switch
 *    on behind a user's back.
 */
@Service
public class UniverseSelectionService {
    private static final Logger log = LoggerFactory.getLogger(UniverseSelectionService.class);

    private final UpbitService upbitService;

    private final boolean enabled;
    private final String quoteCurrency;
    private final BigDecimal minDailyValueKrw;
    private final int lookbackDays;
    private final int skipDays;
    private final int topK;
    private final long refreshMinutes;
    private final String riskOffMarket;
    private final int riskOffMaDays;
    private final int maxCandidates;

    private final AtomicReference<UniverseSnapshot> cache = new AtomicReference<>(null);

    public UniverseSelectionService(
            UpbitService upbitService,
            @Value("${signal.universe.enabled:false}") boolean enabled,
            @Value("${signal.universe.quote:KRW}") String quoteCurrency,
            @Value("${signal.universe.min-daily-value-krw:5000000000}") double minDailyValueKrw,
            @Value("${signal.universe.lookback-days:30}") int lookbackDays,
            @Value("${signal.universe.skip-days:7}") int skipDays,
            @Value("${signal.universe.top-k:5}") int topK,
            @Value("${signal.universe.refresh-minutes:1440}") long refreshMinutes,
            @Value("${signal.universe.risk-off-market:KRW-BTC}") String riskOffMarket,
            @Value("${signal.universe.risk-off-ma-days:100}") int riskOffMaDays,
            @Value("${signal.universe.max-candidates:40}") int maxCandidates
    ) {
        this.upbitService = upbitService;
        this.enabled = enabled;
        this.quoteCurrency = quoteCurrency == null || quoteCurrency.isBlank()
                ? "KRW"
                : quoteCurrency.trim().toUpperCase(Locale.ROOT);
        this.minDailyValueKrw = BigDecimal.valueOf(Math.max(0, minDailyValueKrw));
        this.lookbackDays = Math.max(2, lookbackDays);
        this.skipDays = Math.max(0, skipDays);
        this.topK = Math.max(1, topK);
        this.refreshMinutes = Math.max(1, refreshMinutes);
        this.riskOffMarket = riskOffMarket == null || riskOffMarket.isBlank()
                ? "KRW-BTC"
                : riskOffMarket.trim().toUpperCase(Locale.ROOT);
        this.riskOffMaDays = Math.max(2, riskOffMaDays);
        this.maxCandidates = Math.max(1, maxCandidates);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Markets the engine may open NEW positions in.
     *
     * Callers must union this with markets they already hold — see
     * {@link #resolveTradableMarkets(List, java.util.Collection)}. Dropping a held market from the list
     * outright would leave that position with nothing evaluating its stop-loss.
     */
    public List<String> selectUniverse(List<String> configuredMarkets) {
        if (!enabled) {
            return configuredMarkets == null ? List.of() : configuredMarkets;
        }

        // Read-only: never compute here. Ranking costs ~43 rate-limited Upbit calls (>= 5s, and more with
        // real latency), and this runs inside the trading tick, which holds the per-tenant lock. Blocking
        // it would stop stop-loss evaluation for the duration - the same failure mode as a backoff that
        // gates exits. The refresh runs on its own schedule instead.
        UniverseSnapshot cached = cache.get();
        if (cached != null) {
            return cached.markets();
        }
        // No snapshot yet (startup, or every refresh so far has failed): fall back to the configured list
        // rather than silently trading nothing.
        return configuredMarkets == null ? List.of() : configuredMarkets;
    }

    /**
     * Refreshes the ranking off the trading path.
     *
     * Runs frequently but only does work once a snapshot has aged past {@code refreshMinutes}, so the
     * expensive fetch happens at most once per window regardless of how often this fires.
     */
    @Scheduled(fixedDelayString = "${signal.universe.refresh-check-ms:60000}")
    public void refreshUniverseIfStale() {
        if (!enabled) {
            return;
        }
        UniverseSnapshot cached = cache.get();
        if (cached != null && !cached.isStale(refreshMinutes)) {
            return;
        }

        try {
            UniverseSnapshot snapshot = computeUniverse();
            cache.set(snapshot);
            log.info(
                    "Universe refreshed: {} markets {} (risk-off={})",
                    snapshot.markets().size(),
                    snapshot.markets(),
                    snapshot.riskOff()
            );
        } catch (RuntimeException ex) {
            // Keep the previous snapshot. A stale ranking is far better than an empty one, and the next
            // tick retries in a minute.
            log.warn("Universe refresh failed, keeping the previous snapshot: {}", ex.getMessage());
        }
    }

    /**
     * The full set of markets the engine should evaluate this tick: the selected universe plus anything
     * currently held. Held markets stay in scope so their exits keep running even after they drop out.
     */
    public List<String> resolveTradableMarkets(List<String> configuredMarkets, java.util.Collection<String> heldMarkets) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(selectUniverse(configuredMarkets));
        if (heldMarkets != null) {
            for (String held : heldMarkets) {
                if (held != null && !held.isBlank()) {
                    merged.add(held.trim().toUpperCase(Locale.ROOT));
                }
            }
        }
        return List.copyOf(merged);
    }

    private UniverseSnapshot computeUniverse() {
        if (isRiskOff()) {
            // Empty universe: no new entries anywhere. Held positions are re-added by the caller so their
            // exits still run.
            return new UniverseSnapshot(List.of(), OffsetDateTime.now(), true);
        }

        List<String> candidates = liquidCandidates();
        if (candidates.isEmpty()) {
            return new UniverseSnapshot(List.of(), OffsetDateTime.now(), false);
        }

        int required = skipDays + lookbackDays + 1;
        List<Ranked> ranked = new ArrayList<>();
        for (String market : candidates) {
            try {
                List<Map<String, Object>> candles = upbitService.fetchDayCandles(market, required);
                BigDecimal momentum = momentumPct(candles, required);
                if (momentum != null && momentum.compareTo(BigDecimal.ZERO) > 0) {
                    // Only positive absolute momentum. Holding the "least bad" name in a broad decline is
                    // how a relative-strength screen turns into a slow bleed.
                    ranked.add(new Ranked(market, momentum));
                }
            } catch (RuntimeException ex) {
                log.debug("Skipping {} in universe ranking: {}", market, ex.getMessage());
            }
        }

        ranked.sort(Comparator.comparing(Ranked::momentumPct).reversed());
        List<String> selected = ranked.stream()
                .limit(topK)
                .map(Ranked::market)
                .toList();

        return new UniverseSnapshot(selected, OffsetDateTime.now(), false);
    }

    /** One batched ticker call ranks every quote-currency market by 24h traded value. */
    private List<String> liquidCandidates() {
        List<Map<String, Object>> allMarkets = upbitService.fetchMarkets();
        List<String> codes = new ArrayList<>();
        for (Map<String, Object> item : allMarkets) {
            String market = asString(item.get("market"));
            if (market == null || !market.startsWith(quoteCurrency + "-")) {
                continue;
            }
            // Upbit flags 유의종목 here; those are exactly the names a momentum screen would otherwise
            // rank highly right before they are delisted.
            String warning = asString(item.get("market_warning"));
            if (warning != null && !"NONE".equalsIgnoreCase(warning)) {
                continue;
            }
            codes.add(market);
        }
        if (codes.isEmpty()) {
            return List.of();
        }

        Map<String, Map<String, Object>> tickers = upbitService.fetchTickers(codes);
        List<Ranked> byValue = new ArrayList<>();
        tickers.forEach((market, ticker) -> {
            BigDecimal value = toDecimal(ticker.get("acc_trade_price_24h"));
            if (value != null && value.compareTo(minDailyValueKrw) >= 0) {
                byValue.add(new Ranked(market, value));
            }
        });

        byValue.sort(Comparator.comparing(Ranked::momentumPct).reversed());
        return byValue.stream().limit(maxCandidates).map(Ranked::market).toList();
    }

    /** Reference asset below its long-horizon MA means no new risk anywhere in the universe. */
    private boolean isRiskOff() {
        List<Map<String, Object>> candles = upbitService.fetchDayCandles(riskOffMarket, riskOffMaDays + 1);
        List<BigDecimal> closes = closesOldestFirst(candles);
        if (closes.size() < riskOffMaDays) {
            log.warn("Risk-off gate has insufficient history for {}, treating as risk-off", riskOffMarket);
            return true;
        }
        BigDecimal latest = closes.get(closes.size() - 1);
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = closes.size() - riskOffMaDays; i < closes.size(); i++) {
            sum = sum.add(closes.get(i));
        }
        BigDecimal ma = sum.divide(BigDecimal.valueOf(riskOffMaDays), 8, RoundingMode.HALF_UP);
        return latest.compareTo(ma) < 0;
    }

    /** Return over the lookback window, ending {@code skipDays} ago. Null when history is insufficient. */
    BigDecimal momentumPct(List<Map<String, Object>> candles, int required) {
        List<BigDecimal> closes = closesOldestFirst(candles);
        if (closes.size() < required) {
            return null;
        }
        int lastIndex = closes.size() - 1;
        int recentIndex = lastIndex - skipDays;
        int pastIndex = recentIndex - lookbackDays;
        if (pastIndex < 0) {
            return null;
        }
        BigDecimal recent = closes.get(recentIndex);
        BigDecimal past = closes.get(pastIndex);
        if (past.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return recent.subtract(past)
                .divide(past, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private static List<BigDecimal> closesOldestFirst(List<Map<String, Object>> candles) {
        List<BigDecimal> closes = new ArrayList<>();
        if (candles == null) {
            return closes;
        }
        for (Map<String, Object> candle : candles) {
            BigDecimal close = toDecimal(candle.get("trade_price"));
            if (close != null && close.compareTo(BigDecimal.ZERO) > 0) {
                closes.add(close);
            }
        }
        // Upbit returns newest-first.
        java.util.Collections.reverse(closes);
        return closes;
    }

    private static BigDecimal toDecimal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record Ranked(String market, BigDecimal momentumPct) {
    }

    private record UniverseSnapshot(List<String> markets, OffsetDateTime computedAt, boolean riskOff) {
        boolean isStale(long refreshMinutes) {
            return computedAt == null
                    || Duration.between(computedAt, OffsetDateTime.now()).toMinutes() >= refreshMinutes;
        }
    }
}
