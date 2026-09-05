package com.btcautotrader.engine;

import com.btcautotrader.auth.TradingAccessService;
import com.btcautotrader.order.OrderRepository;
import com.btcautotrader.order.OrderResponse;
import com.btcautotrader.order.OrderService;
import com.btcautotrader.strategy.StrategyConfig;
import com.btcautotrader.strategy.StrategyMarketOverrides;
import com.btcautotrader.strategy.StrategyProfile;
import com.btcautotrader.strategy.StrategyService;
import com.btcautotrader.tenant.TenantContext;
import com.btcautotrader.tenant.TenantDatabaseProvisioningService;
import com.btcautotrader.upbit.UpbitService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutoTradeServiceTest {
    private static final StrategyConfig TEST_CONFIG = new StrategyConfig(
            true,
            30000.0,
            50.0,
            10.0,
            0.0,
            0.0,
            "BALANCED",
            100.0,
            100.0,
            100.0,
            0.0
    );

    @Mock
    private UpbitService upbitService;

    @Mock
    private OrderService orderService;

    @Mock
    private StrategyService strategyService;

    @Mock
    private EngineService engineService;

    @Mock
    private TenantDatabaseProvisioningService tenantDatabaseProvisioningService;

    @Mock
    private TradingAccessService tradingAccessService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TradeDecisionRepository tradeDecisionRepository;

    @Mock
    private TradeDecisionService tradeDecisionService;

    private AutoTradeService service;

    @BeforeEach
    void setUp() {
        service = new AutoTradeService(
                upbitService,
                orderService,
                strategyService,
                engineService,
                tenantDatabaseProvisioningService,
                tradingAccessService,
                orderRepository,
                tradeDecisionRepository,
                tradeDecisionService,
                new BigDecimal("5000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0L,
                0L,
                5L,
                300L,
                1,
                2,
                3,
                2,
                50.0,
                40.0,
                80.0,
                2,
                3,
                2,
                2,
                0.0,
                1,
                0.0,
                0,
                2.0,
                0.0,
                0.0,
                2,
                10,
                0.0,
                5.0,
                0,
                1,
                0,
                14,
                2.0,
                2.5,
                1.0,
                false,
                0L,
                0L,
                0L,
                0L,
                0,
                0L,
                0.0,
                0,
                BigDecimal.ZERO,
                false,
                false,
                15,
                30,
                90,
                5,
                0.0,
                48,
                BigDecimal.ZERO,
                false,
                0.12,
                0.8,
                1.15,
                1.1,
                1.05,
                1.1,
                -1.0,
                0.8,
                0.95,
                0.9,
                0.9,
                1.5,
                false,
                60,
                20,
                50,
                3,
                0.0,
                0L,
                0L,
                0
        );

        lenient().when(strategyService.getConfig()).thenReturn(TEST_CONFIG);
        lenient().when(strategyService.configuredMarkets(null)).thenReturn(List.of("KRW-BTC"));
        lenient().when(strategyService.getMarketOverridesSnapshot(any())).thenReturn(
                new StrategyMarketOverrides(Map.of(), Map.of(), Map.of(), Map.of())
        );
        lenient().when(upbitService.fetchMinuteCandles(eq("KRW-BTC"), eq(1), anyInt())).thenReturn(bullishCandles());
        lenient().when(upbitService.fetchOrderChance("KRW-BTC")).thenReturn(Map.of());
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void runOnce_doesNotScaleInWhenPositionAlreadyExists() {
        when(upbitService.fetchAccounts()).thenReturn(accountsWithBtcPosition());
        when(upbitService.fetchTickers(anyList())).thenReturn(Map.of());

        AutoTradeResult result = service.runOnce();

        assertThat(result.actions())
                .singleElement()
                .satisfies(action -> {
                    AutoTradeAction tradeAction = (AutoTradeAction) action;
                    assertThat(tradeAction.market()).isEqualTo("KRW-BTC");
                    assertThat(tradeAction.action()).isEqualTo("SKIP");
                    assertThat(tradeAction.reason()).isEqualTo("no signal");
                });
        verify(orderService, never()).create(any());
    }

    @Test
    void runOnce_tradesAcrossMultipleConfiguredMarketsWithoutOverspendingCash() {
        when(strategyService.configuredMarkets(7L)).thenReturn(List.of("KRW-BTC", "KRW-ETH"));
        when(upbitService.fetchAccounts()).thenReturn(accountsWithKrwOnly("30000"));
        when(upbitService.fetchMinuteCandles(eq("KRW-ETH"), eq(1), anyInt())).thenReturn(bullishCandles());
        when(upbitService.fetchOrderChance("KRW-ETH")).thenReturn(Map.of());
        when(orderService.create(any())).thenReturn(orderResponse("btc-order", "KRW-BTC"));

        AutoTradeResult result = service.runOnce(7L);

        assertThat(result.actions())
                .hasSize(2);
        assertThat(result.actions().get(0).market()).isEqualTo("KRW-BTC");
        assertThat(result.actions().get(0).action()).isEqualTo("BUY");
        assertThat(result.actions().get(1).market()).isEqualTo("KRW-ETH");
        assertThat(result.actions().get(1).action()).isEqualTo("SKIP");
        assertThat(result.actions().get(1).reason()).isEqualTo("insufficient cash");
        verify(orderService, times(1)).create(any());
    }

    @Test
    void runOnce_skipsPausedMarketWithoutOpeningPosition() {
        when(strategyService.configuredMarkets(7L)).thenReturn(List.of("KRW-BTC"));
        when(strategyService.getMarketOverridesSnapshot(7L)).thenReturn(
                new StrategyMarketOverrides(
                        Map.of(),
                        Map.of(),
                        Map.of("KRW-BTC", true),
                        Map.of()
                )
        );
        when(upbitService.fetchAccounts()).thenReturn(accountsWithKrwOnly("100000"));

        AutoTradeResult result = service.runOnce(7L);

        assertThat(result.actions())
                .singleElement()
                .satisfies(action -> {
                    AutoTradeAction tradeAction = (AutoTradeAction) action;
                    assertThat(tradeAction.market()).isEqualTo("KRW-BTC");
                    assertThat(tradeAction.action()).isEqualTo("SKIP");
                    assertThat(tradeAction.reason()).isEqualTo("market_paused");
                });
        verify(orderService, never()).create(any());
    }

    @Test
    void handleSell_skipsTrailingStopBeforeProfitBufferIsReached() throws Exception {
        MarketIndicators indicators = new MarketIndicators(
                new BigDecimal("100.30"),
                new BigDecimal("100.60"),
                new BigDecimal("99.00"),
                null,
                null,
                new BigDecimal("60"),
                new BigDecimal("1.0"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new BigDecimal("101.00"),
                null,
                new BigDecimal("100.30"),
                OffsetDateTime.now().minusMinutes(1)
        );

        AutoTradeAction action = invokeHandleSell(
                "KRW-BTC",
                accountSnapshot("100", "0", "100"),
                balancedConfig(),
                indicators
        );

        assertThat(action.action()).isEqualTo("SKIP");
        assertThat(action.reason()).isEqualTo("no signal");
        verify(orderService, never()).create(any());
    }

    @Test
    void handleSell_ignoresPreEntryHighWhenArmingTrailingStop() throws Exception {
        seedLastEntryAt("KRW-BTC", OffsetDateTime.now().minusSeconds(10));

        MarketIndicators indicators = new MarketIndicators(
                new BigDecimal("100.60"),
                new BigDecimal("100.90"),
                new BigDecimal("99.00"),
                null,
                null,
                new BigDecimal("60"),
                new BigDecimal("1.0"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new BigDecimal("102.00"),
                null,
                new BigDecimal("102.00"),
                OffsetDateTime.now().minusMinutes(1)
        );

        AutoTradeAction action = invokeHandleSell(
                "KRW-BTC",
                accountSnapshot("100", "0", "100"),
                balancedConfig(),
                indicators
        );

        assertThat(action.action()).isEqualTo("SKIP");
        assertThat(action.reason()).isEqualTo("no signal");
        verify(orderService, never()).create(any());
    }

    @Test
    void handleSell_triggersTrailingStopAfterPostEntryHighIsObserved() throws Exception {
        when(orderService.create(any())).thenReturn(orderResponse("sell-order", "KRW-BTC"));
        seedLastEntryAt("KRW-BTC", OffsetDateTime.now().minusMinutes(2));

        MarketIndicators indicators = new MarketIndicators(
                new BigDecimal("100.60"),
                new BigDecimal("100.90"),
                new BigDecimal("99.00"),
                null,
                null,
                new BigDecimal("60"),
                new BigDecimal("1.0"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new BigDecimal("101.30"),
                null,
                new BigDecimal("101.30"),
                OffsetDateTime.now().minusSeconds(30)
        );

        AutoTradeAction action = invokeHandleSell(
                "KRW-BTC",
                accountSnapshot("100", "0", "100"),
                balancedConfig(),
                indicators
        );

        assertThat(action.action()).isEqualTo("SELL");
        assertThat(action.reason()).isEqualTo("trailing_stop");
        verify(orderService).create(any());
    }

    @Test
    void trailingArmIsNeverNarrowerThanTheTrailingStop() throws Exception {
        // This service is constructed with arm multiplier 1.0 and trail multiplier 2.5 - the inverted
        // geometry that shipped. Arming at 1.0xATR while trailing 2.5xATR puts the stop 1.5xATR BELOW
        // entry the instant it arms, so a position that runs up and comes back can only ever lose.
        MarketIndicators indicators = indicatorsWithAtrPct(new BigDecimal("1.0"));

        BigDecimal armPct = invokePctResolver("resolveConfiguredTrailingArmPct", indicators);
        BigDecimal trailPct = invokePctResolver("resolveConfiguredTrailingStopPct", indicators);

        assertThat(armPct).isGreaterThanOrEqualTo(trailPct);
    }

    @Test
    void handleSell_stopLossStillFiresWhenStopExitPctIsZero() throws Exception {
        lenient().when(upbitService.fetchOrderChance("KRW-BTC")).thenReturn(Map.of());
        when(orderService.create(any())).thenReturn(orderResponse("sell-order", "KRW-BTC"));
        seedLastEntryAt("KRW-BTC", OffsetDateTime.now().minusMinutes(5));

        // stopExitPct is a position FRACTION that shares a grid and a near-identical Korean label with the
        // stop-loss price threshold. At 0 it used to make submitSellByPct return "stop_loss_disabled",
        // silently disarming the stop for that market.
        StrategyConfig zeroStopExit = new StrategyConfig(
                true, 30000.0, 1.44, 1.024, 0.675, 35.0, "BALANCED", 0.0, 40.0, 25.0, 0.7);

        AutoTradeAction action = invokeHandleSell(
                "KRW-BTC",
                accountSnapshot("100", "0", "100"),
                zeroStopExit,
                indicatorsAtPrice(new BigDecimal("90.00"))
        );

        assertThat(action.action()).isEqualTo("SELL");
        assertThat(action.reason()).isEqualTo("stop_loss");
    }

    private BigDecimal invokePctResolver(String methodName, MarketIndicators indicators) throws Exception {
        Method method = AutoTradeService.class.getDeclaredMethod(
                methodName, String.class, StrategyConfig.class, MarketIndicators.class);
        method.setAccessible(true);
        return (BigDecimal) method.invoke(service, "KRW-BTC", balancedConfig(), indicators);
    }

    private static MarketIndicators indicatorsWithAtrPct(BigDecimal atrPct) {
        return new MarketIndicators(
                new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("99.00"),
                null, atrPct, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, OffsetDateTime.now().minusMinutes(1));
    }

    private static MarketIndicators indicatorsAtPrice(BigDecimal price) {
        return new MarketIndicators(
                price, new BigDecimal("100.00"), new BigDecimal("99.00"),
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, price, OffsetDateTime.now().minusMinutes(1));
    }

    private AutoTradeAction invokeHandleSell(
            String market,
            Object position,
            StrategyConfig config,
            MarketIndicators indicators
    ) throws Exception {
        Class<?> accountSnapshotClass = Class.forName(AutoTradeService.class.getName() + "$AccountSnapshot");
        Method method = AutoTradeService.class.getDeclaredMethod(
                "handleSell",
                String.class,
                accountSnapshotClass,
                StrategyConfig.class,
                MarketIndicators.class,
                SignalTuning.class,
                StrategyProfile.class
        );
        method.setAccessible(true);
        return (AutoTradeAction) method.invoke(
                service,
                market,
                position,
                config,
                indicators,
                new SignalTuning(53.0, 47.0, 68.0, 18.0, 0.9, 0.5, 2, 1.0, 0.0),
                StrategyProfile.BALANCED
        );
    }

    private static Object accountSnapshot(String balance, String locked, String avgBuyPrice) throws Exception {
        Class<?> accountSnapshotClass = Class.forName(AutoTradeService.class.getName() + "$AccountSnapshot");
        Constructor<?> constructor = accountSnapshotClass.getDeclaredConstructor(
                BigDecimal.class,
                BigDecimal.class,
                BigDecimal.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(
                new BigDecimal(balance),
                new BigDecimal(locked),
                new BigDecimal(avgBuyPrice)
        );
    }

    private static StrategyConfig balancedConfig() {
        return new StrategyConfig(
                true,
                30000.0,
                1.44,
                1.024,
                0.675,
                35.0,
                "BALANCED",
                100.0,
                40.0,
                25.0,
                0.7
        );
    }

    private static List<Map<String, Object>> accountsWithBtcPosition() {
        return List.of(
                account("KRW", "100000", "0", "0"),
                account("BTC", "0.1", "0", "90000")
        );
    }

    private static List<Map<String, Object>> accountsWithKrwOnly(String balance) {
        return List.of(account("KRW", balance, "0", "0"));
    }

    private static Map<String, Object> account(String currency, String balance, String locked, String avgBuyPrice) {
        return Map.of(
                "currency", currency,
                "balance", balance,
                "locked", locked,
                "avg_buy_price", avgBuyPrice
        );
    }

    private static List<Map<String, Object>> bullishCandles() {
        return List.of(
                candle("106500", "107000", "104000", "12000000"),
                candle("104000", "105000", "103000", "11000000"),
                candle("103000", "104000", "102000", "10000000"),
                candle("102000", "103000", "101000", "9000000"),
                candle("101000", "102000", "100000", "8000000")
        );
    }

    private static Map<String, Object> candle(String tradePrice, String highPrice, String lowPrice, String quoteVolume) {
        return Map.of(
                "trade_price", tradePrice,
                "high_price", highPrice,
                "low_price", lowPrice,
                "candle_acc_trade_price", quoteVolume
        );
    }

    private static OrderResponse orderResponse(String orderId, String market) {
        return new OrderResponse(
                orderId,
                "done",
                "accepted",
                null,
                null,
                market,
                "BUY",
                "MARKET",
                null,
                null,
                new BigDecimal("30000"),
                null
        );
    }

    @SuppressWarnings("unchecked")
    private void seedLastEntryAt(String market, OffsetDateTime entryAt) throws Exception {
        Field field = AutoTradeService.class.getDeclaredField("lastEntryAtByMarket");
        field.setAccessible(true);
        Map<String, OffsetDateTime> byMarket = (Map<String, OffsetDateTime>) field.get(service);
        byMarket.put("__system__::" + market, entryAt);
    }
}
