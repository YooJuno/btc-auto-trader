package com.btcautotrader.paper;

import com.btcautotrader.upbit.UpbitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaperTradingServiceTest {
    private static final BigDecimal FEE = new BigDecimal("0.0005");
    private static final BigDecimal SLIPPAGE = new BigDecimal("0.001");

    @Mock
    private UpbitService upbitService;

    @Mock
    private PaperAccountRepository repository;

    private final Map<String, PaperAccountEntity> store = new HashMap<>();

    private PaperTradingService service;

    @BeforeEach
    void setUp() {
        store.clear();
        when(repository.findById(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(0, String.class))));
        when(repository.existsById(anyString()))
                .thenAnswer(inv -> store.containsKey(inv.getArgument(0, String.class)));
        when(repository.findAll()).thenAnswer(inv -> List.copyOf(store.values()));
        when(repository.save(any(PaperAccountEntity.class))).thenAnswer(inv -> {
            PaperAccountEntity entity = inv.getArgument(0);
            store.put(entity.getCurrency(), entity);
            return entity;
        });
        when(repository.saveAll(any())).thenAnswer(inv -> {
            Iterable<PaperAccountEntity> entities = inv.getArgument(0);
            entities.forEach(entity -> store.put(entity.getCurrency(), entity));
            return entities;
        });

        service = new PaperTradingService(
                repository, upbitService, "PAPER", new BigDecimal("1000000"), FEE, SLIPPAGE);
    }

    private void priceIs(String price) {
        when(upbitService.fetchTicker(anyString())).thenReturn(Map.of("trade_price", price));
    }

    @Test
    void marketBuySpendsTheFullAmountAndPaysSlippage() {
        priceIs("100");

        PaperTradingService.PaperFill fill =
                service.execute("KRW-BTC", "BUY", "MARKET", null, null, new BigDecimal("100000"));

        assertThat(fill.filled()).isTrue();
        // A buy crosses the spread: 100 * (1 + 0.001).
        assertThat(fill.price()).isEqualByComparingTo(new BigDecimal("100.1"));
        // Fee comes out of the funds, so only the remainder buys coin.
        assertThat(fill.fee()).isEqualByComparingTo(new BigDecimal("50.0"));
        assertThat(store.get("KRW").getBalance()).isEqualByComparingTo(new BigDecimal("900000"));
        assertThat(store.get("BTC").getBalance()).isEqualByComparingTo(
                new BigDecimal("99950").divide(new BigDecimal("100.1"), 8, java.math.RoundingMode.DOWN));
    }

    @Test
    void averageBuyPriceExcludesFees() {
        // Upbit's avg_buy_price is fee-exclusive and the stop-loss compares against it. A fee-inclusive
        // cost basis here would put every paper stop at a different level than the live one.
        priceIs("100");

        service.execute("KRW-BTC", "BUY", "MARKET", null, null, new BigDecimal("100000"));

        assertThat(store.get("BTC").getAvgBuyPrice()).isEqualByComparingTo(new BigDecimal("100.1"));
    }

    @Test
    void marketSellCreditsProceedsNetOfFeeAndSlippage() {
        priceIs("100");
        service.execute("KRW-BTC", "BUY", "MARKET", null, null, new BigDecimal("100000"));
        BigDecimal held = store.get("BTC").getBalance();

        priceIs("200");
        PaperTradingService.PaperFill fill = service.execute("KRW-BTC", "SELL", "MARKET", null, held, null);

        assertThat(fill.filled()).isTrue();
        // A sell crosses the other way: 200 * (1 - 0.001).
        assertThat(fill.price()).isEqualByComparingTo(new BigDecimal("199.8"));
        BigDecimal gross = held.multiply(new BigDecimal("199.8"));
        assertThat(fill.funds()).isEqualByComparingTo(gross.subtract(gross.multiply(FEE)));
        assertThat(store.get("BTC").getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(store.get("BTC").getAvgBuyPrice()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void rejectsABuyItCannotAfford() {
        // Recorded as a rejection rather than skipped: silently dropping unaffordable orders would make
        // a paper run look better than the strategy is.
        priceIs("100");

        PaperTradingService.PaperFill fill =
                service.execute("KRW-BTC", "BUY", "MARKET", null, null, new BigDecimal("5000000"));

        assertThat(fill.filled()).isFalse();
        assertThat(fill.reason()).isEqualTo("insufficient funds");
        assertThat(store.get("KRW").getBalance()).isEqualByComparingTo(new BigDecimal("1000000"));
    }

    @Test
    void rejectsASellOfMoreThanIsHeld() {
        priceIs("100");

        PaperTradingService.PaperFill fill =
                service.execute("KRW-BTC", "SELL", "MARKET", null, new BigDecimal("1"), null);

        assertThat(fill.filled()).isFalse();
        assertThat(fill.reason()).isEqualTo("insufficient volume");
    }

    @Test
    void limitOrdersFillOnlyWhenTheMarketIsThroughThem() {
        priceIs("100");

        // Bid below the market cannot fill without an order-book simulation, so it is refused rather
        // than optimistically filled.
        assertThat(service.execute("KRW-BTC", "BUY", "LIMIT", new BigDecimal("90"), BigDecimal.ONE, null).reason())
                .isEqualTo("paper_limit_not_marketable");
        // Bid above the market is immediately marketable.
        assertThat(service.execute("KRW-BTC", "BUY", "LIMIT", new BigDecimal("110"), BigDecimal.ONE, null).filled())
                .isTrue();
    }

    @Test
    void accountsSnapshotMatchesTheUpbitRowShape() {
        // The engine and PortfolioService read these rows with no branch, so the keys must match exactly.
        List<Map<String, Object>> accounts = service.accountsSnapshot();

        assertThat(accounts).hasSize(1);
        assertThat(accounts.get(0)).containsOnlyKeys("currency", "balance", "locked", "avg_buy_price");
        assertThat(accounts.get(0).get("currency")).isEqualTo("KRW");
        assertThat(new BigDecimal((String) accounts.get(0).get("balance")))
                .isEqualByComparingTo(new BigDecimal("1000000"));
    }

    @Test
    void liveModeReportsItself() {
        PaperTradingService live = new PaperTradingService(
                repository, upbitService, "LIVE", new BigDecimal("1000000"), FEE, SLIPPAGE);

        assertThat(live.isPaperMode()).isFalse();
        assertThat(service.isPaperMode()).isTrue();
    }
}
