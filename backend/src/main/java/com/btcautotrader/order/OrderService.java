package com.btcautotrader.order;

import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class OrderService {
    public OrderResponse create(OrderRequest request) {
        return new OrderResponse(
                UUID.randomUUID().toString(),
                "SIMULATED",
                OffsetDateTime.now().toString(),
                request.market(),
                request.side(),
                request.type(),
                request.price(),
                request.volume(),
                request.funds()
        );
    }
}
