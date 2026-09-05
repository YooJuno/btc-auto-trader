package com.btcautotrader.market;

import com.btcautotrader.upbit.UpbitService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/market")
public class MarketController {
    private static final List<Integer> ALLOWED_CANDLE_UNITS = List.of(1, 3, 5, 10, 15, 30, 60, 240);

    private final UpbitService upbitService;

    public MarketController(UpbitService upbitService) {
        this.upbitService = upbitService;
    }

    @GetMapping("/price")
    public ResponseEntity<Map<String, Object>> getPrice(
            @RequestParam(name = "market", required = false) String market,
            @RequestParam(name = "coin", required = false) String coin
    ) {
        String normalizedMarket = normalizeMarket(market, coin);
        if (normalizedMarket == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "market or coin is required");
            error.put("example", "/api/market/price?market=KRW-BTC or /api/market/price?coin=BTC");
            return ResponseEntity.badRequest().body(error);
        }

        Map<String, Object> ticker = upbitService.fetchTicker(normalizedMarket);
        if (ticker == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "ticker not found");
            error.put("market", normalizedMarket);
            return ResponseEntity.status(404).body(error);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("queriedAt", OffsetDateTime.now().toString());
        response.put("market", normalizedMarket);
        response.put("ticker", ticker);

        return ResponseEntity.ok(response);
    }

    /**
     * Public OHLCV for charting. The console had no visualisation of any kind, so an operator could not
     * see price action, where their average buy sits, or where the engine entered and exited.
     * Upbit minute candles support units 1/3/5/10/15/30/60/240 only.
     */
    @GetMapping("/candles")
    public ResponseEntity<Map<String, Object>> getCandles(
            @RequestParam(name = "market") String market,
            @RequestParam(name = "unit", defaultValue = "60") int unit,
            @RequestParam(name = "count", defaultValue = "200") int count
    ) {
        String normalizedMarket = normalizeMarket(market, null);
        if (normalizedMarket == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "market is required");
            return ResponseEntity.badRequest().body(error);
        }
        if (!ALLOWED_CANDLE_UNITS.contains(unit)) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "unsupported unit");
            error.put("allowed", ALLOWED_CANDLE_UNITS);
            return ResponseEntity.badRequest().body(error);
        }

        int normalizedCount = Math.max(1, Math.min(count, 400));
        List<Map<String, Object>> raw = upbitService.fetchMinuteCandles(normalizedMarket, unit, normalizedCount);

        List<Map<String, Object>> candles = new ArrayList<>();
        for (Map<String, Object> item : raw == null ? List.<Map<String, Object>>of() : raw) {
            Map<String, Object> candle = new HashMap<>();
            // UTC so the client can parse it unambiguously; display timezone is a client concern.
            candle.put("time", item.get("candle_date_time_utc"));
            candle.put("open", item.get("opening_price"));
            candle.put("high", item.get("high_price"));
            candle.put("low", item.get("low_price"));
            candle.put("close", item.get("trade_price"));
            candle.put("value", item.get("candle_acc_trade_price"));
            candles.add(candle);
        }
        // Upbit returns newest-first; charts want oldest-first.
        Collections.reverse(candles);

        Map<String, Object> response = new HashMap<>();
        response.put("queriedAt", OffsetDateTime.now().toString());
        response.put("market", normalizedMarket);
        response.put("unit", unit);
        response.put("candles", candles);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listMarkets(
            @RequestParam(name = "quote", defaultValue = "KRW") String quote
    ) {
        String normalizedQuote = normalizeQuote(quote);
        List<Map<String, Object>> raw = upbitService.fetchMarkets();
        List<Map<String, Object>> items = new ArrayList<>();

        for (Map<String, Object> item : raw) {
            Map<String, Object> normalized = normalizeMarketItem(item);
            if (normalized == null) {
                continue;
            }
            String market = (String) normalized.get("market");
            if (normalizedQuote != null && !market.startsWith(normalizedQuote + "-")) {
                continue;
            }
            items.add(normalized);
        }

        items.sort(Comparator.comparing(item -> String.valueOf(item.get("market"))));

        Map<String, Object> response = new HashMap<>();
        response.put("queriedAt", OffsetDateTime.now().toString());
        response.put("quote", normalizedQuote);
        response.put("count", items.size());
        response.put("markets", items);
        return ResponseEntity.ok(response);
    }

    private String normalizeMarket(String market, String coin) {
        if (coin != null && !coin.trim().isEmpty()) {
            return "KRW-" + coin.trim().toUpperCase();
        }
        if (market != null && !market.trim().isEmpty()) {
            return market.trim().toUpperCase();
        }
        return null;
    }

    private static String normalizeQuote(String quote) {
        if (quote == null) {
            return null;
        }
        String normalized = quote.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized;
    }

    private static Map<String, Object> normalizeMarketItem(Map<String, Object> raw) {
        if (raw == null) {
            return null;
        }
        String market = asString(raw.get("market"));
        if (market == null) {
            return null;
        }
        String ticker = extractTicker(market);
        Map<String, Object> item = new HashMap<>();
        item.put("market", market);
        item.put("ticker", ticker);
        item.put("koreanName", asString(raw.get("korean_name")));
        item.put("englishName", asString(raw.get("english_name")));
        item.put("marketWarning", asString(raw.get("market_warning")));
        return item;
    }

    private static String extractTicker(String market) {
        int idx = market.indexOf('-');
        if (idx <= 0 || idx >= market.length() - 1) {
            return market;
        }
        return market.substring(idx + 1);
    }

    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized;
    }
}
