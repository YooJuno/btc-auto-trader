package com.btcautotrader.order;

import com.btcautotrader.upbit.UpbitApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/order")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/history")
    public ResponseEntity<List<OrderHistoryItem>> history(
            @RequestParam(name = "limit", defaultValue = "30") int limit
    ) {
        return ResponseEntity.ok(orderService.listRecent(limit));
    }

    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody(required = false) OrderRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(error("request body is required"));
        }

        String market = normalize(request.market());
        String side = normalize(request.side());
        String type = normalize(request.type());
        String clientOrderId = normalizeClientOrderId(request.clientOrderId());

        if (isBlank(market) || isBlank(side) || isBlank(type)) {
            return ResponseEntity.badRequest().body(error("market, side, type are required"));
        }

        if (!side.equals("BUY") && !side.equals("SELL")) {
            return ResponseEntity.badRequest().body(error("side must be BUY or SELL"));
        }
        if (!type.equals("MARKET") && !type.equals("LIMIT")) {
            return ResponseEntity.badRequest().body(error("type must be MARKET or LIMIT"));
        }

        BigDecimal price = request.price();
        BigDecimal volume = request.volume();
        BigDecimal funds = request.funds();

        if (type.equals("MARKET")) {
            if (side.equals("BUY")) {
                if (funds != null && price != null) {
                    return ResponseEntity.badRequest().body(error("MARKET BUY requires either funds or price, not both"));
                }
                if (funds == null && price == null) {
                    return ResponseEntity.badRequest().body(error("MARKET BUY requires funds (or price as total)") );
                }
                if (volume != null) {
                    return ResponseEntity.badRequest().body(error("MARKET BUY must not include volume"));
                }
                if (funds == null) {
                    funds = price;
                }
                if (!isPositive(funds)) {
                    return ResponseEntity.badRequest().body(error("funds must be > 0"));
                }
                price = null;
                volume = null;
            } else {
                if (volume == null) {
                    return ResponseEntity.badRequest().body(error("MARKET SELL requires volume") );
                }
                if (price != null || funds != null) {
                    return ResponseEntity.badRequest().body(error("MARKET SELL must not include price or funds"));
                }
                if (!isPositive(volume)) {
                    return ResponseEntity.badRequest().body(error("volume must be > 0"));
                }
                price = null;
                funds = null;
            }
        }

        if (type.equals("LIMIT")) {
            if (price == null || volume == null) {
                return ResponseEntity.badRequest().body(error("LIMIT requires price and volume") );
            }
            if (funds != null) {
                return ResponseEntity.badRequest().body(error("LIMIT must not include funds"));
            }
            if (!isPositive(price) || !isPositive(volume)) {
                return ResponseEntity.badRequest().body(error("price and volume must be > 0"));
            }
            funds = null;
        }

        OrderRequest normalized = new OrderRequest(market, side, type, price, volume, funds, clientOrderId);
        try {
            OrderResponse response = orderService.create(normalized);
            if ("PENDING".equalsIgnoreCase(response.requestStatus())) {
                return ResponseEntity.accepted().body(response);
            }
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            Map<String, Object> payload = error(ex.getMessage());
            return ResponseEntity.status(409).body(payload);
        } catch (UpbitApiException ex) {
            Map<String, Object> payload = error("upbit api error");
            payload.put("status", ex.getStatusCode());
            if (ex.getResponseBody() != null && !ex.getResponseBody().isBlank()) {
                payload.put("details", ex.getResponseBody());
            }
            return ResponseEntity.status(ex.getStatusCode()).body(payload);
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String normalizeClientOrderId(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        return error;
    }
}
