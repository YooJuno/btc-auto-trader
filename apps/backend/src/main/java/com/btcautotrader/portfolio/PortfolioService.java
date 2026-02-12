package com.btcautotrader.portfolio;

import com.btcautotrader.upbit.UpbitService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PortfolioService {
    private final UpbitService upbitService;

    public PortfolioService(UpbitService upbitService) {
        this.upbitService = upbitService;
    }

    public PortfolioSummary getSummary() {
        List<Map<String, Object>> accounts = upbitService.fetchAccounts();
        CashBalance cash = null;
        List<Position> rawPositions = new ArrayList<>();
        List<String> markets = new ArrayList<>();

        for (Map<String, Object> account : accounts) {
            String currency = asString(account.get("currency"));
            if (currency == null) {
                continue;
            }

            String unitCurrency = asString(account.get("unit_currency"));
            BigDecimal balance = toDecimal(account.get("balance"));
            BigDecimal locked = toDecimal(account.get("locked"));
            BigDecimal total = balance.add(locked);
            BigDecimal avgBuyPrice = toDecimal(account.get("avg_buy_price"));

            if ("KRW".equalsIgnoreCase(currency)) {
                cash = new CashBalance("KRW", balance, locked, total);
                continue;
            }

            if (total.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            String unit = isBlank(unitCurrency) ? "KRW" : unitCurrency.trim().toUpperCase();
            String market = unit + "-" + currency.trim().toUpperCase();

            rawPositions.add(new Position(
                    market,
                    currency.trim().toUpperCase(),
                    unit,
                    total,
                    avgBuyPrice,
                    null,
                    null,
                    null,
                    null,
                    null
            ));
            markets.add(market);
        }

        Map<String, Map<String, Object>> tickers = upbitService.fetchTickers(markets);

        BigDecimal positionValueTotal = BigDecimal.ZERO;
        BigDecimal positionCostTotal = BigDecimal.ZERO;

        List<Position> positions = new ArrayList<>();
        for (Position position : rawPositions) {
            Map<String, Object> ticker = tickers.get(position.market());
            BigDecimal currentPrice = null;
            if (ticker != null) {
                currentPrice = toDecimal(ticker.get("trade_price"));
            }

            BigDecimal valuation = currentPrice == null ? null : currentPrice.multiply(position.quantity());
            BigDecimal cost = position.avgBuyPrice().multiply(position.quantity());
            BigDecimal pnl = null;
            BigDecimal pnlRate = null;

            if (valuation != null && cost.compareTo(BigDecimal.ZERO) > 0) {
                pnl = valuation.subtract(cost);
                pnlRate = safeDivide(pnl, cost);
            }

            if (valuation != null) {
                positionValueTotal = positionValueTotal.add(valuation);
            }
            if (cost.compareTo(BigDecimal.ZERO) > 0) {
                positionCostTotal = positionCostTotal.add(cost);
            }

            positions.add(new Position(
                    position.market(),
                    position.currency(),
                    position.unitCurrency(),
                    position.quantity(),
                    position.avgBuyPrice(),
                    currentPrice,
                    valuation,
                    cost,
                    pnl,
                    pnlRate
            ));
        }

        BigDecimal cashTotal = cash != null ? cash.total() : BigDecimal.ZERO;
        BigDecimal positionPnl = null;
        BigDecimal positionPnlRate = null;

        if (positionCostTotal.compareTo(BigDecimal.ZERO) > 0) {
            positionPnl = positionValueTotal.subtract(positionCostTotal);
            positionPnlRate = safeDivide(positionPnl, positionCostTotal);
        }

        Totals totals = new Totals(
                cashTotal,
                positionValueTotal,
                positionCostTotal,
                positionPnl,
                positionPnlRate,
                cashTotal.add(positionValueTotal)
        );

        return new PortfolioSummary(
                OffsetDateTime.now().toString(),
                cash,
                positions,
                totals
        );
    }

    private static BigDecimal safeDivide(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return numerator.divide(denominator, 8, RoundingMode.HALF_UP);
    }

    private static BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
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

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
