package com.btcautotrader.order;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/order")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody(required = false) OrderRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(error("request body is required"));
        }

        String market = normalize(request.market());
        String side = normalize(request.side());
        String type = normalize(request.type());

        if (isBlank(market) || isBlank(side) || isBlank(type)) {
            return ResponseEntity.badRequest().body(error("market, side, type are required"));
        }

        if (!side.equals("BUY") && !side.equals("SELL")) {
            return ResponseEntity.badRequest().body(error("side must be BUY or SELL"));
        }
        if (!type.equals("MARKET") && !type.equals("LIMIT")) {
            return ResponseEntity.badRequest().body(error("type must be MARKET or LIMIT"));
        }

        Double price = request.price();
        Double volume = request.volume();
        Double funds = request.funds();

        if (type.equals("MARKET")) {
            if (side.equals("BUY")) {
                if (funds == null && price == null) {
                    return ResponseEntity.badRequest().body(error("MARKET BUY requires funds (or price as total)") );
                }
                if (funds == null) {
                    funds = price;
                }
                price = null;
                volume = null;
            } else {
                if (volume == null) {
                    return ResponseEntity.badRequest().body(error("MARKET SELL requires volume") );
                }
                price = null;
                funds = null;
            }
        }

        if (type.equals("LIMIT")) {
            if (price == null || volume == null) {
                return ResponseEntity.badRequest().body(error("LIMIT requires price and volume") );
            }
            funds = null;
        }

        OrderRequest normalized = new OrderRequest(market, side, type, price, volume, funds);
        return ResponseEntity.ok(orderService.create(normalized));
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

    private static Map<String, Object> error(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        return error;
    }
}
