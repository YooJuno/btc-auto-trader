package com.btcautotrader.order;

import com.btcautotrader.upbit.UpbitApiException;
import com.btcautotrader.upbit.UpbitOrderResponse;
import com.btcautotrader.upbit.UpbitService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderService {
    private final UpbitService upbitService;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    public OrderService(UpbitService upbitService, OrderRepository orderRepository, ObjectMapper objectMapper) {
        this.upbitService = upbitService;
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
    }

    public OrderResponse create(OrderRequest request) {
        String clientOrderId = normalizeClientOrderId(request.clientOrderId());
        if (clientOrderId == null) {
            clientOrderId = UUID.randomUUID().toString();
        }

        Optional<OrderEntity> existing = orderRepository.findByClientOrderId(clientOrderId);
        if (existing.isPresent()) {
            OrderEntity entity = existing.get();
            if (!matchesRequest(entity, request)) {
                throw new IllegalArgumentException("clientOrderId already used with different parameters");
            }
            return toResponse(entity);
        }

        UpbitPayload payload = buildUpbitPayload(request, clientOrderId);

        OrderEntity entity = new OrderEntity();
        entity.setClientOrderId(clientOrderId);
        entity.setMarket(request.market());
        entity.setSide(request.side());
        entity.setType(request.type());
        entity.setOrdType(payload.ordType());
        entity.setStatus(OrderStatus.REQUESTED);
        entity.setPrice(request.price());
        entity.setVolume(request.volume());
        entity.setFunds(request.funds());
        entity.setRawRequest(payload.queryString());

        try {
            orderRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            OrderEntity found = orderRepository.findByClientOrderId(clientOrderId)
                    .orElseThrow(() -> ex);
            return toResponse(found);
        }

        try {
            UpbitOrderResponse response = upbitService.createOrder(payload.body(), payload.queryString());
            if (response == null) {
                throw new IllegalStateException("Upbit response is empty");
            }
            entity.setExternalId(response.uuid());
            entity.setState(response.state());
            entity.setStatus(resolveStatus(response, OrderStatus.SUBMITTED));
            entity.setCreatedAt(parseOffsetDateTime(response.createdAt()));
            applyExecutionSnapshot(entity, response);
            entity.setRawResponse(safeSerialize(response));
            orderRepository.save(entity);
            return toResponse(entity);
        } catch (UpbitApiException ex) {
            if (isRetryable(ex)) {
                UpbitOrderResponse reconciled = upbitService.fetchOrderByIdentifier(clientOrderId);
                if (reconciled != null) {
                    entity.setExternalId(reconciled.uuid());
                    entity.setState(reconciled.state());
                    entity.setStatus(resolveStatus(reconciled, OrderStatus.SUBMITTED));
                    entity.setCreatedAt(parseOffsetDateTime(reconciled.createdAt()));
                    applyExecutionSnapshot(entity, reconciled);
                    entity.setRawResponse(safeSerialize(reconciled));
                    orderRepository.save(entity);
                    return toResponse(entity);
                }
                entity.setStatus(OrderStatus.PENDING);
                entity.setErrorMessage(truncate(resolveErrorMessage(ex), 2000));
                orderRepository.save(entity);
                return toResponse(entity);
            }
            entity.setStatus(OrderStatus.FAILED);
            entity.setErrorMessage(truncate(resolveErrorMessage(ex), 2000));
            orderRepository.save(entity);
            throw ex;
        } catch (RuntimeException ex) {
            entity.setStatus(OrderStatus.FAILED);
            entity.setErrorMessage(truncate(resolveErrorMessage(ex), 2000));
            orderRepository.save(entity);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<OrderHistoryItem> listRecent(int limit) {
        int safeLimit = normalizeLimit(limit);
        PageRequest pageRequest = PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "requestedAt"));
        return orderRepository.findAll(pageRequest)
                .getContent()
                .stream()
                .map(this::toHistoryItem)
                .toList();
    }

    private UpbitPayload buildUpbitPayload(OrderRequest request, String clientOrderId) {
        String side = request.side().equals("BUY") ? "bid" : "ask";
        String ordType = resolveOrdType(request, side);

        Map<String, String> body = new LinkedHashMap<>();
        body.put("market", request.market());
        body.put("side", side);
        body.put("ord_type", ordType);
        if (clientOrderId != null) {
            body.put("identifier", clientOrderId);
        }

        if (ordType.equals("limit")) {
            body.put("volume", toPlain(request.volume()));
            body.put("price", toPlain(request.price()));
        } else if (ordType.equals("price")) {
            body.put("price", toPlain(request.funds()));
        } else if (ordType.equals("market")) {
            body.put("volume", toPlain(request.volume()));
        }

        return new UpbitPayload(body, buildQueryString(body), ordType);
    }

    private String resolveOrdType(OrderRequest request, String side) {
        if (request.type().equals("LIMIT")) {
            return "limit";
        }
        if (side.equals("bid")) {
            return "price";
        }
        return "market";
    }

    private static String buildQueryString(Map<String, String> body) {
        return body.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String toPlain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
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

    private static boolean matchesRequest(OrderEntity entity, OrderRequest request) {
        return equalsIgnoreCase(entity.getMarket(), request.market())
                && equalsIgnoreCase(entity.getSide(), request.side())
                && equalsIgnoreCase(entity.getType(), request.type())
                && equalsDecimal(entity.getPrice(), request.price())
                && equalsDecimal(entity.getVolume(), request.volume())
                && equalsDecimal(entity.getFunds(), request.funds());
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.equalsIgnoreCase(right);
    }

    private static boolean equalsDecimal(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.compareTo(right) == 0;
    }

    private OrderResponse toResponse(OrderEntity entity) {
        String status = entity.getState();
        String requestStatus = entity.getStatus() == null ? null : entity.getStatus().name();
        String receivedAt = entity.getRequestedAt() == null ? OffsetDateTime.now().toString() : entity.getRequestedAt().toString();

        return new OrderResponse(
                entity.getExternalId(),
                status,
                requestStatus,
                entity.getErrorMessage(),
                receivedAt,
                entity.getMarket(),
                entity.getSide(),
                entity.getType(),
                entity.getPrice(),
                entity.getVolume(),
                entity.getFunds(),
                entity.getClientOrderId()
        );
    }

    private OrderHistoryItem toHistoryItem(OrderEntity entity) {
        UpbitOrderResponse snapshot = parseRawResponse(entity.getRawResponse());
        BigDecimal resolvedVolume = resolveExecutedVolume(entity.getVolume(), snapshot);
        String requestStatus = resolveDisplayRequestStatus(entity.getStatus(), snapshot);

        return new OrderHistoryItem(
                entity.getId(),
                entity.getMarket(),
                entity.getSide(),
                entity.getType(),
                entity.getOrdType(),
                requestStatus,
                entity.getState(),
                entity.getPrice(),
                resolvedVolume,
                entity.getFunds(),
                entity.getRequestedAt() == null ? null : entity.getRequestedAt().toString(),
                entity.getCreatedAt() == null ? null : entity.getCreatedAt().toString(),
                entity.getExternalId(),
                entity.getClientOrderId(),
                entity.getErrorMessage()
        );
    }

    private static void applyExecutionSnapshot(OrderEntity entity, UpbitOrderResponse response) {
        if (entity == null || response == null) {
            return;
        }
        BigDecimal executed = parseDecimal(response.executedVolume());
        if (executed != null && executed.compareTo(BigDecimal.ZERO) > 0) {
            entity.setVolume(executed);
            return;
        }
        if (entity.getVolume() == null) {
            BigDecimal volume = parseDecimal(response.volume());
            if (volume != null) {
                entity.setVolume(volume);
            }
        }
    }

    private static OrderStatus resolveStatus(UpbitOrderResponse response, OrderStatus fallback) {
        if (response == null) {
            return fallback == null ? OrderStatus.SUBMITTED : fallback;
        }
        String state = response.state();
        if (state == null) {
            return fallback == null ? OrderStatus.SUBMITTED : fallback;
        }
        String normalized = state.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "done" -> OrderStatus.FILLED;
            case "cancel" -> hasExecution(response) ? OrderStatus.FILLED : OrderStatus.CANCELED;
            case "wait" -> OrderStatus.SUBMITTED;
            default -> fallback == null ? OrderStatus.SUBMITTED : fallback;
        };
    }

    private static String resolveDisplayRequestStatus(OrderStatus status, UpbitOrderResponse snapshot) {
        if (status == OrderStatus.CANCELED && hasExecution(snapshot)) {
            return OrderStatus.FILLED.name();
        }
        return status == null ? null : status.name();
    }

    private static BigDecimal resolveExecutedVolume(BigDecimal storedVolume, UpbitOrderResponse snapshot) {
        if (storedVolume != null) {
            return storedVolume;
        }
        BigDecimal executed = parseDecimal(snapshot == null ? null : snapshot.executedVolume());
        if (executed != null && executed.compareTo(BigDecimal.ZERO) > 0) {
            return executed;
        }
        return parseDecimal(snapshot == null ? null : snapshot.volume());
    }

    private UpbitOrderResponse parseRawResponse(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(rawResponse, UpbitOrderResponse.class);
        } catch (JsonProcessingException ex) {
            return null;
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

    private static String normalizeClientOrderId(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private static String resolveErrorMessage(RuntimeException ex) {
        if (ex instanceof UpbitApiException upbit) {
            String body = upbit.getResponseBody();
            if (body != null && !body.isBlank()) {
                return body;
            }
        }
        return ex.getMessage();
    }

    private static boolean isRetryable(UpbitApiException ex) {
        int status = ex.getStatusCode();
        if (status == 429) {
            return true;
        }
        return status >= 500;
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 30;
        }
        return Math.min(limit, 200);
    }

    private record UpbitPayload(Map<String, String> body, String queryString, String ordType) {
    }
}
