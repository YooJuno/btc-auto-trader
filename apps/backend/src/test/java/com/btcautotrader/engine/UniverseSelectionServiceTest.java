package com.btcautotrader.engine;

import com.btcautotrader.upbit.UpbitService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UniverseSelectionServiceTest {
    private static final int LOOKBACK = 30;
    private static final int SKIP = 7;
    private static final int REQUIRED = SKIP + LOOKBACK + 1;

    @Mock
    private UpbitService upbitService;

    private UniverseSelectionService service(boolean enabled, int topK) {
        return new UniverseSelectionService(
                upbitService,
                enabled,
                "KRW",
                1_000_000_000d,
                LOOKBACK,
                SKIP,
                topK,
                1440L,
                "KRW-BTC",
                100,
                40
        );
    }

    @Test
    void disabledSelectionLeavesTheConfiguredListUntouched() {
        List<String> configured = List.of("KRW-BTC", "KRW-ETH");

        assertThat(service(false, 5).selectUniverse(configured)).isEqualTo(configured);
    }

    @Test
    void ranksByTrailingReturnAndKeepsOnlyTheTopK() {
        stubRiskOn();
        stubMarkets("KRW-AAA", "KRW-BBB", "KRW-CCC");
        stubLiquidity(Map.of(
                "KRW-AAA", "9000000000",
                "KRW-BBB", "9000000000",
                "KRW-CCC", "9000000000"
        ));
        // Return measured from closes[0] to closes[lookback], i.e. excluding the skip window.
        stubDailyCandles("KRW-AAA", momentumSeries(100, 120));
        stubDailyCandles("KRW-BBB", momentumSeries(100, 180));
        stubDailyCandles("KRW-CCC", momentumSeries(100, 150));

        List<String> universe = service(true, 2).selectUniverse(List.of("KRW-BTC"));

        assertThat(universe).containsExactly("KRW-BBB", "KRW-CCC");
    }

    @Test
    void excludesMarketsBelowTheLiquidityFloor() {
        stubRiskOn();
        stubMarkets("KRW-AAA", "KRW-THIN");
        stubLiquidity(Map.of(
                "KRW-AAA", "9000000000",
                "KRW-THIN", "12000000"
        ));
        stubDailyCandles("KRW-AAA", momentumSeries(100, 150));
        stubDailyCandles("KRW-THIN", momentumSeries(100, 900));

        assertThat(service(true, 5).selectUniverse(List.of())).containsExactly("KRW-AAA");
    }

    @Test
    void excludesNegativeMomentum() {
        stubRiskOn();
        stubMarkets("KRW-UP", "KRW-DOWN");
        stubLiquidity(Map.of("KRW-UP", "9000000000", "KRW-DOWN", "9000000000"));
        stubDailyCandles("KRW-UP", momentumSeries(100, 130));
        stubDailyCandles("KRW-DOWN", momentumSeries(100, 70));

        assertThat(service(true, 5).selectUniverse(List.of())).containsExactly("KRW-UP");
    }

    @Test
    void riskOffReferenceAssetEmptiesTheUniverse() {
        // Reference asset below its long MA: holding the strongest alt in a falling market is how a
        // cross-sectional momentum screen loses money.
        when(upbitService.fetchDayCandles(eq("KRW-BTC"), anyInt())).thenReturn(newestFirst(flat(100, 100, 40)));

        assertThat(service(true, 5).selectUniverse(List.of("KRW-ETH"))).isEmpty();
    }

    @Test
    void heldMarketsSurviveEvenWhenTheyDropOutOfTheUniverse() {
        // The safety property. A held name that stops ranking must stay in scope or its stop-loss is
        // never evaluated again and the position is orphaned.
        stubRiskOn();
        stubMarkets("KRW-AAA");
        stubLiquidity(Map.of("KRW-AAA", "9000000000"));
        stubDailyCandles("KRW-AAA", momentumSeries(100, 150));

        List<String> tradable = service(true, 5)
                .resolveTradableMarkets(List.of(), List.of("KRW-DROPPED"));

        assertThat(tradable).containsExactlyInAnyOrder("KRW-AAA", "KRW-DROPPED");
    }

    @Test
    void heldMarketsSurviveARiskOffUniverse() {
        when(upbitService.fetchDayCandles(eq("KRW-BTC"), anyInt())).thenReturn(newestFirst(flat(100, 100, 40)));

        assertThat(service(true, 5).resolveTradableMarkets(List.of(), List.of("KRW-ETH")))
                .containsExactly("KRW-ETH");
    }

    @Test
    void momentumIgnoresTheSkipWindow() {
        // closes[0] = 100, closes[LOOKBACK] = 200, and the final SKIP days crash to 1. The measured
        // return must be +100%, not the crash, because short-horizon crypto returns mean-revert.
        List<BigDecimal> closes = new ArrayList<>();
        for (int i = 0; i <= LOOKBACK; i++) {
            closes.add(BigDecimal.valueOf(100 + i * (100.0 / LOOKBACK)));
        }
        for (int i = 0; i < SKIP; i++) {
            closes.add(BigDecimal.ONE);
        }

        BigDecimal momentum = service(true, 5).momentumPct(newestFirst(closes), REQUIRED);

        assertThat(momentum).isNotNull();
        assertThat(momentum.doubleValue()).isCloseTo(100.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void insufficientHistoryYieldsNoMomentum() {
        assertThat(service(true, 5).momentumPct(newestFirst(flat(100, 5, 5)), REQUIRED)).isNull();
    }

    // --- helpers ---

    private void stubRiskOn() {
        // Latest close far above the 100-day mean.
        when(upbitService.fetchDayCandles(eq("KRW-BTC"), anyInt())).thenReturn(newestFirst(flat(100, 100, 400)));
    }

    private void stubMarkets(String... markets) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String market : markets) {
            Map<String, Object> row = new HashMap<>();
            row.put("market", market);
            row.put("market_warning", "NONE");
            rows.add(row);
        }
        when(upbitService.fetchMarkets()).thenReturn(rows);
    }

    private void stubLiquidity(Map<String, String> valueByMarket) {
        Map<String, Map<String, Object>> tickers = new HashMap<>();
        valueByMarket.forEach((market, value) -> {
            Map<String, Object> ticker = new HashMap<>();
            ticker.put("acc_trade_price_24h", value);
            tickers.put(market, ticker);
        });
        when(upbitService.fetchTickers(anyList())).thenReturn(tickers);
    }

    private void stubDailyCandles(String market, List<Map<String, Object>> candles) {
        when(upbitService.fetchDayCandles(eq(market), anyInt())).thenReturn(candles);
    }

    /** Series whose close rises from {@code from} to {@code to} across the lookback, then flatlines. */
    private static List<Map<String, Object>> momentumSeries(double from, double to) {
        List<BigDecimal> closes = new ArrayList<>();
        for (int i = 0; i <= LOOKBACK; i++) {
            closes.add(BigDecimal.valueOf(from + (to - from) * i / LOOKBACK));
        }
        for (int i = 0; i < SKIP; i++) {
            closes.add(BigDecimal.valueOf(to));
        }
        return newestFirst(closes);
    }

    private static List<BigDecimal> flat(double value, int count, double latest) {
        List<BigDecimal> closes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            closes.add(BigDecimal.valueOf(value));
        }
        closes.add(BigDecimal.valueOf(latest));
        return closes;
    }

    /** Upbit returns candles newest-first; the fixtures above are written oldest-first. */
    private static List<Map<String, Object>> newestFirst(List<BigDecimal> closesOldestFirst) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BigDecimal close : closesOldestFirst) {
            Map<String, Object> row = new HashMap<>();
            row.put("trade_price", close.toPlainString());
            rows.add(row);
        }
        Collections.reverse(rows);
        return rows;
    }
}
