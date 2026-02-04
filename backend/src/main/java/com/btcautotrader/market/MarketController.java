package com.btcautotrader.market;

import com.btcautotrader.upbit.UpbitService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/market")
public class MarketController {
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

    private String normalizeMarket(String market, String coin) {
        if (coin != null && !coin.trim().isEmpty()) {
            return "KRW-" + coin.trim().toUpperCase();
        }
        if (market != null && !market.trim().isEmpty()) {
            return market.trim().toUpperCase();
        }
        return null;
    }
}
