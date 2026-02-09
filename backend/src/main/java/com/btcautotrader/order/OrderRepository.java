package com.btcautotrader.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
    Optional<OrderEntity> findByClientOrderId(String clientOrderId);

    List<OrderEntity> findByStatusInAndRequestedAtAfter(List<OrderStatus> statuses, OffsetDateTime after);

    List<OrderEntity> findByStatusInAndRequestedAtBefore(List<OrderStatus> statuses, OffsetDateTime before);

    boolean existsByMarketAndSideAndRequestedAtAfter(String market, String side, OffsetDateTime after);

    boolean existsByMarketAndSideAndStatusInAndRequestedAtAfter(
            String market,
            String side,
            List<OrderStatus> statuses,
            OffsetDateTime after
    );
}
