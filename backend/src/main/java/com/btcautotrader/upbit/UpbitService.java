package com.btcautotrader.upbit;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UpbitService {
    private static final String UPBIT_ACCOUNTS_URL = "https://api.upbit.com/v1/accounts";
    private static final String UPBIT_TICKER_URL = "https://api.upbit.com/v1/ticker";

    private final RestTemplate restTemplate;
    private final UpbitCredentials credentials;

    public UpbitService(RestTemplateBuilder restTemplateBuilder, UpbitCredentials credentials) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
        this.credentials = credentials;
    }

    public List<Map<String, Object>> fetchAccounts() {
        String jwtToken = createJwtToken();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + jwtToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<List> response = restTemplate.exchange(
                UPBIT_ACCOUNTS_URL,
                HttpMethod.GET,
                entity,
                List.class
        );

        return response.getBody();
    }

    public Map<String, Object> fetchTicker(String market) {
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

    public Map<String, Map<String, Object>> fetchTickers(List<String> markets) {
        if (markets == null || markets.isEmpty()) {
            return Map.of();
        }

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

    private String createJwtToken() {
        String nonce = UUID.randomUUID().toString();
        Algorithm algorithm = Algorithm.HMAC256(credentials.getSecretKey());

        return JWT.create()
                .withClaim("access_key", credentials.getAccessKey())
                .withClaim("nonce", nonce)
                .sign(algorithm);
    }
}
