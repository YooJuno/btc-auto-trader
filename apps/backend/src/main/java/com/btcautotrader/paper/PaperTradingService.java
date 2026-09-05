package com.btcautotrader.paper;

import com.btcautotrader.upbit.UpbitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Simulated execution against real market data.
 *
 * The point of paper mode here is rehearsal, not simulation for its own sake: the strategy was
 * substantially rewritten (exit geometry, entry filters, timeframe, universe selection) and there was no
 * way to watch the new behaviour without risking money. So the seam is deliberately narrow — only
 * account state and order execution are replaced. Signals, indicators, sizing, cooldowns, the regime
 * gate and every exit run the identical code path they run live, against the identical live candles.
 * Anything broader would be testing a different program than the one that trades.
 *
 * Costs are modelled with the same fee and slippage rates the engine already budgets with, so a paper
 * fill is priced the way the engine assumed it would be when it decided to trade.
 */
@Service
public class PaperTradingService {
    private static final Logger log = LoggerFactory.getLogger(PaperTradingService.class);
    private static final String QUOTE_CURRENCY = "KRW";
    private static final int SCALE = 18;

    private final PaperAccountRepository paperAccountRepository;
    private final UpbitService upbitService;
    private final boolean paperMode;
    private final BigDecimal initialKrw;
    private final BigDecimal feeRate;
    private final BigDecimal slippagePct;

    public PaperTradingService(
            PaperAccountRepository paperAccountRepository,
            UpbitService upbitService,
            @Value("${trading.mode:LIVE}") String tradingMode,
            @Value("${trading.paper.initial-krw:1000000}") BigDecimal initialKrw,
            @Value("${trading.fee-rate:0.0005}") BigDecimal feeRate,
            @Value("${trading.slippage-pct:0.001}") BigDecimal slippagePct
    ) {
        this.paperAccountRepository = paperAccountRepository;
        this.upbitService = upbitService;
        this.paperMode = "PAPER".equalsIgnoreCase(tradingMode == null ? "" : tradingMode.trim());
        this.initialKrw = initialKrw == null || initialKrw.compareTo(BigDecimal.ZERO) <= 0
                ? new BigDecimal("1000000")
                : initialKrw;
        this.feeRate = feeRate == null ? BigDecimal.ZERO : feeRate.abs();
        this.slippagePct = slippagePct == null ? BigDecimal.ZERO : slippagePct.abs();
    }

    public boolean isPaperMode() {
        return paperMode;
    }

    public BigDecimal initialKrw() {
        return initialKrw;
    }

    /** Accounts in exactly the shape UpbitService.fetchAccounts returns, so callers need no branch. */
    @Transactional
    public List<Map<String, Object>> accountsSnapshot() {
        seedIfEmpty();
        List<Map<String, Object>> accounts = new ArrayList<>();
        for (PaperAccountEntity entity : paperAccountRepository.findAll()) {
            if (entity == null || entity.getBalance() == null) {
                continue;
            }
            if (entity.getBalance().compareTo(BigDecimal.ZERO) <= 0
                    && !QUOTE_CURRENCY.equalsIgnoreCase(entity.getCurrency())) {
                continue;
            }
            Map<String, Object> row = new HashMap<>();
            row.put("currency", entity.getCurrency());
            row.put("balance", entity.getBalance().toPlainString());
            row.put("locked", entity.getLocked() == null ? "0" : entity.getLocked().toPlainString());
            row.put("avg_buy_price", entity.getAvgBuyPrice() == null ? "0" : entity.getAvgBuyPrice().toPlainString());
            accounts.add(row);
        }
        return accounts;
    }

    /**
     * Fills an order against the current ticker.
     *
     * Returns a failure rather than throwing so the caller can record a rejected order exactly as it
     * would record an exchange rejection — a paper run that silently skipped unaffordable orders would
     * overstate the strategy.
     */
    @Transactional
    public PaperFill execute(String market, String side, String ordType, BigDecimal price, BigDecimal volume, BigDecimal funds) {
        seedIfEmpty();

        String currency = extractCurrency(market);
        if (currency == null) {
            return PaperFill.rejected("invalid market");
        }
        BigDecimal marketPrice = currentPrice(market);
        if (marketPrice == null || marketPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return PaperFill.rejected("price unavailable");
        }

        boolean buy = "BUY".equalsIgnoreCase(side);
        BigDecimal executionPrice = resolveExecutionPrice(marketPrice, ordType, price, buy);
        if (executionPrice == null) {
            // A resting limit order needs an order-book simulation to model honestly. Rejecting is the
            // truthful answer; pretending it filled would flatter the results.
            return PaperFill.rejected("paper_limit_not_marketable");
        }

        return buy
                ? fillBuy(currency, executionPrice, volume, funds, ordType)
                : fillSell(currency, executionPrice, volume);
    }

    private PaperFill fillBuy(String currency, BigDecimal executionPrice, BigDecimal volume, BigDecimal funds, String ordType) {
        BigDecimal spend = funds;
        if (spend == null || spend.compareTo(BigDecimal.ZERO) <= 0) {
            if (volume == null || volume.compareTo(BigDecimal.ZERO) <= 0) {
                return PaperFill.rejected("no funds");
            }
            spend = volume.multiply(executionPrice);
        }

        PaperAccountEntity krw = account(QUOTE_CURRENCY);
        if (krw.getBalance().compareTo(spend) < 0) {
            return PaperFill.rejected("insufficient funds");
        }

        BigDecimal fee = spend.multiply(feeRate);
        BigDecimal netSpend = spend.subtract(fee);
        BigDecimal quantity = netSpend.divide(executionPrice, 8, RoundingMode.DOWN);
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return PaperFill.rejected("quantity too small");
        }

        PaperAccountEntity coin = account(currency);
        BigDecimal previousQty = coin.getBalance();
        BigDecimal previousCost = previousQty.multiply(coin.getAvgBuyPrice());
        BigDecimal newQty = previousQty.add(quantity);
        // Fee-exclusive, matching Upbit's avg_buy_price so stop-loss levels agree with live.
        BigDecimal newAvg = previousCost.add(quantity.multiply(executionPrice))
                .divide(newQty, SCALE, RoundingMode.HALF_UP);

        krw.setBalance(krw.getBalance().subtract(spend));
        coin.setBalance(newQty);
        coin.setAvgBuyPrice(newAvg);
        paperAccountRepository.saveAll(List.of(krw, coin));

        return PaperFill.filled(quantity, executionPrice, spend, fee);
    }

    private PaperFill fillSell(String currency, BigDecimal executionPrice, BigDecimal volume) {
        if (volume == null || volume.compareTo(BigDecimal.ZERO) <= 0) {
            return PaperFill.rejected("no volume");
        }
        PaperAccountEntity coin = account(currency);
        if (coin.getBalance().compareTo(volume) < 0) {
            return PaperFill.rejected("insufficient volume");
        }

        BigDecimal gross = volume.multiply(executionPrice);
        BigDecimal fee = gross.multiply(feeRate);
        BigDecimal proceeds = gross.subtract(fee);

        PaperAccountEntity krw = account(QUOTE_CURRENCY);
        krw.setBalance(krw.getBalance().add(proceeds));
        BigDecimal remaining = coin.getBalance().subtract(volume);
        coin.setBalance(remaining);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            coin.setBalance(BigDecimal.ZERO);
            coin.setAvgBuyPrice(BigDecimal.ZERO);
        }
        paperAccountRepository.saveAll(List.of(krw, coin));

        return PaperFill.filled(volume, executionPrice, proceeds, fee);
    }

    /** Market orders cross the spread; limit orders fill only when the market is already through them. */
    private BigDecimal resolveExecutionPrice(BigDecimal marketPrice, String ordType, BigDecimal limitPrice, boolean buy) {
        boolean limit = "LIMIT".equalsIgnoreCase(ordType);
        if (!limit) {
            BigDecimal factor = buy ? BigDecimal.ONE.add(slippagePct) : BigDecimal.ONE.subtract(slippagePct);
            return marketPrice.multiply(factor);
        }
        if (limitPrice == null || limitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        if (buy) {
            return marketPrice.compareTo(limitPrice) <= 0 ? marketPrice : null;
        }
        return marketPrice.compareTo(limitPrice) >= 0 ? marketPrice : null;
    }

    private BigDecimal currentPrice(String market) {
        Map<String, Object> ticker = upbitService.fetchTicker(market);
        if (ticker == null) {
            return null;
        }
        Object tradePrice = ticker.get("trade_price");
        if (tradePrice == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(tradePrice));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private PaperAccountEntity account(String currency) {
        return paperAccountRepository.findById(currency)
                .orElseGet(() -> new PaperAccountEntity(currency, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    private void seedIfEmpty() {
        if (paperAccountRepository.existsById(QUOTE_CURRENCY)) {
            return;
        }
        paperAccountRepository.save(new PaperAccountEntity(QUOTE_CURRENCY, initialKrw, BigDecimal.ZERO));
        log.info("Seeded paper account with {} KRW", initialKrw);
    }

    /** Wipes simulated state back to the starting balance. */
    @Transactional
    public void reset() {
        paperAccountRepository.deleteAll();
        paperAccountRepository.save(new PaperAccountEntity(QUOTE_CURRENCY, initialKrw, BigDecimal.ZERO));
        log.info("Paper account reset to {} KRW", initialKrw);
    }

    private static String extractCurrency(String market) {
        if (market == null) {
            return null;
        }
        int idx = market.indexOf('-');
        if (idx <= 0 || idx >= market.length() - 1) {
            return null;
        }
        return market.substring(idx + 1).toUpperCase(Locale.ROOT);
    }

    public record PaperFill(
            boolean filled,
            String reason,
            BigDecimal quantity,
            BigDecimal price,
            BigDecimal funds,
            BigDecimal fee
    ) {
        static PaperFill filled(BigDecimal quantity, BigDecimal price, BigDecimal funds, BigDecimal fee) {
            return new PaperFill(true, "paper_filled", quantity, price, funds, fee);
        }

        static PaperFill rejected(String reason) {
            return new PaperFill(false, reason, null, null, null, null);
        }
    }
}
