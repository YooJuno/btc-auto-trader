package com.btcautotrader.order;

import com.btcautotrader.tenant.TenantContext;
import com.btcautotrader.tenant.TenantDatabaseProvisioningService;
import com.btcautotrader.upbit.UpbitOrderResponse;
import com.btcautotrader.upbit.UpbitService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class OrderReconcileService {
    private final TenantDatabaseProvisioningService tenantDatabaseProvisioningService;
    private final OrderRepository orderRepository;
    private final UpbitService upbitService;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final long lookbackMinutes;
    private final long staleMinutes;

    public OrderReconcileService(
            TenantDatabaseProvisioningService tenantDatabaseProvisioningService,
            OrderRepository orderRepository,
            UpbitService upbitService,
            ObjectMapper objectMapper,
            @Value("${orders.reconcile.enabled:true}") boolean enabled,
            @Value("${orders.reconcile.lookback-minutes:60}") long lookbackMinutes,
            @Value("${orders.reconcile.stale-minutes:180}") long staleMinutes
    ) {
        this.tenantDatabaseProvisioningService = tenantDatabaseProvisioningService;
        this.orderRepository = orderRepository;
        this.upbitService = upbitService;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.lookbackMinutes = lookbackMinutes;
        this.staleMinutes = staleMinutes;
    }

    @Scheduled(fixedDelayString = "${orders.reconcile.delay-ms:10000}")
    public void reconcilePending() {
        if (!enabled) {
            return;
        }
        for (String tenantDatabase : tenantDatabaseProvisioningService.listKnownTenantDatabases()) {
            try {
                TenantContext.runWithTenantDatabase(tenantDatabase, this::reconcilePendingForCurrentTenant);
            } catch (RuntimeException ignored) {
                // Keep reconciling remaining tenants even if one tenant fails.
            }
        }
    }

    private void reconcilePendingForCurrentTenant() {
        OffsetDateTime after = OffsetDateTime.now().minusMinutes(lookbackMinutes);
        List<OrderEntity> pending = orderRepository.findByStatusInAndRequestedAtAfter(
                List.of(OrderStatus.REQUESTED, OrderStatus.PENDING, OrderStatus.SUBMITTED),
                after
        );

        for (OrderEntity order : pending) {
            String identifier = order.getClientOrderId();
            if (identifier == null || identifier.isBlank()) {
                continue;
            }

            try {
                UpbitOrderResponse response = upbitService.fetchOrderByIdentifier(identifier);
                if (response == null) {
                    continue;
                }

                order.setExternalId(response.uuid());
                order.setState(response.state());
                order.setStatus(resolveStatus(response, order.getStatus()));
                order.setCreatedAt(parseOffsetDateTime(response.createdAt()));
                applyExecutionSnapshot(order, response);
                order.setRawResponse(safeSerialize(response));
                orderRepository.save(order);
            } catch (RuntimeException ex) {
                order.setErrorMessage(truncate(ex.getMessage(), 2000));
                orderRepository.save(order);
            }
        }

        OffsetDateTime staleCutoff = OffsetDateTime.now().minusMinutes(staleMinutes);
        List<OrderEntity> stale = orderRepository.findByStatusInAndRequestedAtBefore(
                List.of(OrderStatus.REQUESTED, OrderStatus.PENDING, OrderStatus.SUBMITTED),
                staleCutoff
        );

        for (OrderEntity order : stale) {
            String identifier = order.getClientOrderId();
            if (identifier == null || identifier.isBlank()) {
                order.setStatus(OrderStatus.FAILED);
                order.setErrorMessage("reconcile timeout");
                orderRepository.save(order);
                continue;
            }

            try {
                UpbitOrderResponse response = upbitService.fetchOrderByIdentifier(identifier);
                if (response != null) {
                    order.setExternalId(response.uuid());
                    order.setState(response.state());
                    order.setStatus(resolveStatus(response, order.getStatus()));
                    order.setCreatedAt(parseOffsetDateTime(response.createdAt()));
                    applyExecutionSnapshot(order, response);
                    order.setRawResponse(safeSerialize(response));
                    orderRepository.save(order);
                    continue;
                }
                order.setStatus(OrderStatus.SUBMITTED);
                order.setErrorMessage("reconcile timeout");
                orderRepository.save(order);
            } catch (RuntimeException ex) {
                order.setStatus(OrderStatus.SUBMITTED);
                order.setErrorMessage(truncate(ex.getMessage(), 2000));
                orderRepository.save(order);
            }
        }
    }

    private static OffsetDateTime parseOffsetDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private String safeSerialize(UpbitOrderResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException ex) {
            return response.toString();
        }
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private static OrderStatus resolveStatus(UpbitOrderResponse response, OrderStatus fallback) {
        if (response == null || response.state() == null) {
            return fallback == null ? OrderStatus.SUBMITTED : fallback;
        }
        String normalized = response.state().trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "done" -> OrderStatus.FILLED;
            case "cancel" -> hasExecution(response) ? OrderStatus.FILLED : OrderStatus.CANCELED;
            case "wait" -> OrderStatus.SUBMITTED;
            default -> fallback == null ? OrderStatus.SUBMITTED : fallback;
        };
    }

    private static void applyExecutionSnapshot(OrderEntity order, UpbitOrderResponse response) {
        if (order == null || response == null) {
            return;
        }
        BigDecimal executed = parseDecimal(response.executedVolume());
        if (executed != null && executed.compareTo(BigDecimal.ZERO) > 0) {
            order.setVolume(executed);
            return;
        }
        if (order.getVolume() == null) {
            BigDecimal volume = parseDecimal(response.volume());
            if (volume != null) {
                order.setVolume(volume);
            }
        }
    }

    private static boolean hasExecution(UpbitOrderResponse response) {
        if (response == null) {
            return false;
        }
        BigDecimal executed = parseDecimal(response.executedVolume());
        if (executed != null && executed.compareTo(BigDecimal.ZERO) > 0) {
            return true;
        }
        Integer trades = response.tradesCount();
        return trades != null && trades > 0;
    }

    private static BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
