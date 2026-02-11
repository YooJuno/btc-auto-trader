package com.btcautotrader.upbit;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UpbitService {
    private static final String UPBIT_ACCOUNTS_URL = "https://api.upbit.com/v1/accounts";
    private static final String UPBIT_TICKER_URL = "https://api.upbit.com/v1/ticker";
    private static final String UPBIT_MARKETS_URL = "https://api.upbit.com/v1/market/all";
    private static final String UPBIT_CANDLES_MINUTE_URL = "https://api.upbit.com/v1/candles/minutes";
    private static final String UPBIT_ORDER_URL = "https://api.upbit.com/v1/orders";
    private static final String UPBIT_ORDER_CHANCE_URL = "https://api.upbit.com/v1/orders/chance";
    private static final String UPBIT_ORDER_DETAIL_URL = "https://api.upbit.com/v1/order";

    private final RestTemplate restTemplate;
    private final UpbitCredentials credentials;
    private final UpbitRateLimiter rateLimiter;

    public UpbitService(
            RestTemplateBuilder restTemplateBuilder,
            UpbitCredentials credentials,
            UpbitRateLimiter rateLimiter
    ) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
        this.credentials = credentials;
        this.rateLimiter = rateLimiter;
    }

    public List<Map<String, Object>> fetchAccounts() {
        rateLimiter.acquire("accounts");
        String jwtToken = createJwtToken(null);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + jwtToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<List> response = restTemplate.exchange(
                UPBIT_ACCOUNTS_URL,
                HttpMethod.GET,
                entity,
                List.class
        );

        List<Map<String, Object>> body = response.getBody();
        return body == null ? List.of() : body;
    }

    public Map<String, Object> fetchTicker(String market) {
        rateLimiter.acquire("ticker");
        String url = UriComponentsBuilder.fromHttpUrl(UPBIT_TICKER_URL)
                .queryParam("markets", market)
                .toUriString();

        ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
        List<Map<String, Object>> body = response.getBody();

        if (body == null || body.isEmpty()) {
            return null;
        }

        return body.get(0);
    }

    public List<Map<String, Object>> fetchMarkets() {
        rateLimiter.acquire("markets");
        String url = UriComponentsBuilder.fromHttpUrl(UPBIT_MARKETS_URL)
                .queryParam("isDetails", true)
                .toUriString();

        ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
        List<Map<String, Object>> body = response.getBody();
        return body == null ? List.of() : body;
    }

    public List<Map<String, Object>> fetchMinuteCandles(String market, int unit, int count) {
        if (unit <= 0) {
            throw new IllegalArgumentException("unit must be positive");
        }
        rateLimiter.acquire("candles");
        int safeCount = Math.max(1, Math.min(count, 200));
        String url = UriComponentsBuilder.fromHttpUrl(UPBIT_CANDLES_MINUTE_URL + "/" + unit)
                .queryParam("market", market)
                .queryParam("count", safeCount)
                .toUriString();

        ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
        List<Map<String, Object>> body = response.getBody();
        return body == null ? List.of() : body;
    }

    public Map<String, Map<String, Object>> fetchTickers(List<String> markets) {
        if (markets == null || markets.isEmpty()) {
            return Map.of();
        }
        rateLimiter.acquire("tickers");

        String url = UriComponentsBuilder.fromHttpUrl(UPBIT_TICKER_URL)
                .queryParam("markets", String.join(",", markets))
                .toUriString();

        ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
        List<Map<String, Object>> body = response.getBody();

        if (body == null || body.isEmpty()) {
            return Map.of();
        }

        Map<String, Map<String, Object>> byMarket = new HashMap<>();
        for (Map<String, Object> ticker : body) {
            Object market = ticker.get("market");
            if (market != null) {
                byMarket.put(market.toString(), ticker);
            }
        }

        return byMarket;
    }

    public UpbitOrderResponse createOrder(Map<String, String> body, String queryString) {
        rateLimiter.acquire("create-order");
        String jwtToken = createJwtToken(queryString);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + jwtToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<UpbitOrderResponse> response = restTemplate.exchange(
                    UPBIT_ORDER_URL,
                    HttpMethod.POST,
                    entity,
                    UpbitOrderResponse.class
            );
            return response.getBody();
        } catch (HttpStatusCodeException ex) {
            throw new UpbitApiException(ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (RestClientException ex) {
            throw new UpbitApiException(502, ex.getMessage());
        }
    }

    public UpbitOrderResponse fetchOrderByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return null;
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("identifier", identifier);
        return fetchOrder(params);
    }

    public Map<String, Object> fetchOrderChance(String market) {
        if (market == null || market.isBlank()) {
            return Map.of();
        }
        rateLimiter.acquire("order-chance");

        Map<String, String> params = new LinkedHashMap<>();
        params.put("market", market);
        String queryString = buildQueryString(params);
        String jwtToken = createJwtToken(queryString);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + jwtToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        String url = UPBIT_ORDER_CHANCE_URL + "?" + queryString;

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    Map.class
            );
            Map<String, Object> body = response.getBody();
            return body == null ? Map.of() : body;
        } catch (HttpStatusCodeException ex) {
            throw new UpbitApiException(ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (RestClientException ex) {
            throw new UpbitApiException(502, ex.getMessage());
        }
    }

    private String createJwtToken(String queryString) {
        String nonce = UUID.randomUUID().toString();
        Algorithm algorithm = Algorithm.HMAC512(credentials.getSecretKey());

        com.auth0.jwt.JWTCreator.Builder builder = JWT.create()
                .withClaim("access_key", credentials.getAccessKey())
                .withClaim("nonce", nonce);

        if (queryString != null && !queryString.isBlank()) {
            builder.withClaim("query_hash", sha512Hex(queryString));
            builder.withClaim("query_hash_alg", "SHA512");
        }

        return builder.sign(algorithm);
    }

    private UpbitOrderResponse fetchOrder(Map<String, String> params) {
        rateLimiter.acquire("order-detail");
        String queryString = buildQueryString(params);
        String jwtToken = createJwtToken(queryString);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + jwtToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        String url = UPBIT_ORDER_DETAIL_URL + "?" + queryString;

        try {
            ResponseEntity<UpbitOrderResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    UpbitOrderResponse.class
            );
            return response.getBody();
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 404) {
                return null;
            }
            throw new UpbitApiException(ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (RestClientException ex) {
            throw new UpbitApiException(502, ex.getMessage());
        }
    }

    private static String sha512Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create SHA-512 hash", ex);
        }
    }

    private static String buildQueryString(Map<String, String> params) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append("&");
            }
            builder.append(encode(entry.getKey())).append("=").append(encode(entry.getValue()));
        }
        return builder.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
