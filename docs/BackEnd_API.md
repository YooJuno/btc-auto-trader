# BackEnd API

## 공통
- Base URL: `http://localhost:8080`
- 응답은 JSON 형식이며, 시간은 ISO-8601 문자열입니다.
- 오류 응답은 보통 아래 형태입니다.

```jsonc
{
  "error": "메시지",
  "fields": {
    "필드명": "에러 상세"
  }
}
```

---

## 시세

### GET /api/market/price
현재가 조회.

**쿼리 파라미터**
- `market` 또는 `coin` 중 하나 필수
- `coin`이 있으면 자동으로 `KRW-`가 붙습니다.

**호출**
```bash
curl "http://localhost:8080/api/market/price?coin=BTC"
curl "http://localhost:8080/api/market/price?market=KRW-BTC"
```

**응답**
```jsonc
{
  "queriedAt": "2026-02-10T01:45:12.345Z",
  "market": "KRW-BTC",
  "ticker": {
    "market": "KRW-BTC",
    "trade_price": 12345678
  }
}
```

---

## 주문 (Upbit 실제 주문)

### POST /api/order
주문 생성. Upbit에 실제 주문을 요청합니다.

**요청 필드**
- `market`: 예) `KRW-BTC`
- `side`: `BUY` | `SELL`
- `type`: `MARKET` | `LIMIT`
- `price`: 지정가 가격 (LIMIT)
- `volume`: 수량 (LIMIT, MARKET SELL)
- `funds`: 총 주문 금액 (MARKET BUY)
- `clientOrderId`: 선택. Upbit `identifier`로 전달됨. `identifier` 또는 `client_order_id`도 허용

**규칙 요약**
- `MARKET BUY`: `funds` 또는 `price` 중 하나 필요(둘 다 금지), `volume` 금지
- `MARKET SELL`: `volume` 필수, `price`/`funds` 금지
- `LIMIT`: `price` + `volume` 필수, `funds` 금지

**호출**
```bash
curl -X POST "http://localhost:8080/api/order" \
  -H "Content-Type: application/json" \
  -d '{"market":"KRW-BTC","side":"BUY","type":"MARKET","funds":10000}'

curl -X POST "http://localhost:8080/api/order" \
  -H "Content-Type: application/json" \
  -d '{"market":"KRW-BTC","side":"SELL","type":"LIMIT","price":41000000,"volume":0.002}'
```

**응답**
```jsonc
{
  "orderId": "upbit-order-uuid",
  "status": "wait",            // Upbit 상태값
  "requestStatus": "SUBMITTED", // 내부 요청 상태: REQUESTED | PENDING | SUBMITTED | FAILED
  "errorMessage": null,
  "receivedAt": "2026-02-10T01:45:12.345Z",
  "market": "KRW-BTC",
  "side": "BUY",
  "type": "MARKET",
  "price": null,
  "volume": null,
  "funds": 10000,
  "clientOrderId": "..."
}
```

**상태 코드**
- `200 OK`: 정상 처리
- `202 Accepted`: `requestStatus=PENDING`
- `409 Conflict`: 같은 `clientOrderId`로 다른 파라미터 주문
- `4xx`: 검증 실패

### GET /api/order/history
최근 주문 로그 조회.

**쿼리 파라미터**
- `limit`: 기본 30, 최대 200

**응답**
```jsonc
[
  {
    "id": 1,
    "market": "KRW-BTC",
    "side": "BUY",
    "type": "MARKET",
    "ordType": "price",         // Upbit ord_type
    "requestStatus": "SUBMITTED",
    "state": "wait",
    "price": null,
    "volume": null,
    "funds": 10000,
    "requestedAt": "2026-02-10T01:45:12.345Z",
    "createdAt": "2026-02-10T01:45:12.678Z",
    "orderId": "upbit-order-uuid",
    "clientOrderId": "...",
    "errorMessage": null
  }
]
```

---

## 포트폴리오

### GET /api/portfolio/summary
계좌 요약.

**응답**
```jsonc
{
  "queriedAt": "2026-02-10T01:45:12.345Z",
  "cash": {
    "currency": "KRW",
    "balance": 1000000,
    "locked": 0,
    "total": 1000000
  },
  "positions": [
    {
      "market": "KRW-BTC",
      "currency": "BTC",
      "unitCurrency": "KRW",
      "quantity": 0.01,
      "avgBuyPrice": 50000000,
      "currentPrice": 51000000,
      "valuation": 510000,
      "cost": 500000,
      "pnl": 10000,
      "pnlRate": 0.02
    }
  ],
  "totals": {
    "cash": 1000000,
    "positionValue": 510000,
    "positionCost": 500000,
    "positionPnl": 10000,
    "positionPnlRate": 0.02,
    "totalAsset": 1510000
  }
}
```

### GET /api/portfolio/performance
자동매매 의사결정(BUY/SELL) 로그 기반 성과 추정.

**쿼리 파라미터**
- 범위 모드: `from` / `to` (YYYY-MM-DD). 누락 시 최근 30일 기본값
- 연/월 모드: `year` (필수), `month` (선택)
- `from/to`와 `year/month`는 동시에 사용 불가

**호출**
```bash
curl "http://localhost:8080/api/portfolio/performance?from=2026-02-01&to=2026-02-10"
curl "http://localhost:8080/api/portfolio/performance?year=2026&month=2"
```

**응답**
```jsonc
{
  "timezone": "Asia/Seoul",
  "estimated": true,
  "note": "자동매매 BUY/SELL 의사결정 로그 기반 추정치입니다.",
  "from": "2026-02-01",
  "to": "2026-02-10",
  "total": {
    "period": "TOTAL",
    "from": "2026-02-01",
    "to": "2026-02-10",
    "estimatedRealizedPnlKrw": 12345,
    "netCashFlowKrw": 10000,
    "buyNotionalKrw": 50000,
    "sellNotionalKrw": 60000,
    "unmatchedSellNotionalKrw": 0,
    "estimatedFeeKrw": 123,
    "buyCount": 5,
    "sellCount": 4,
    "tradeCount": 9,
    "matchedSellCount": 4,
    "winningSellCount": 3,
    "losingSellCount": 1,
    "sellWinRate": 0.75
  },
  "yearly": [],
  "monthly": []
}
```

---

## 자동매매 엔진

### POST /api/engine/start
엔진 시작.

### POST /api/engine/stop
엔진 정지.

### GET /api/engine/status
엔진 상태 조회.

**응답**
```jsonc
{
  "running": true,
  "timestamp": "2026-02-10T01:45:12.345Z"
}
```

### POST /api/engine/tick
자동매매 1회 실행.

**쿼리 파라미터**
- `force` (default: false) — 엔진 OFF 상태에서도 실행할지 여부

**응답**
```jsonc
{
  "executedAt": "2026-02-10T01:45:12.345Z",
  "actions": [
    {
      "market": "KRW-BTC",
      "action": "SKIP",
      "reason": "no signal",
      "price": 104000000,
      "quantity": null,
      "funds": null,
      "orderId": null,
      "requestStatus": null
    }
  ]
}
```

엔진이 꺼져 있고 `force=false`면 `409`와 함께 `SKIP: engine_stopped` 응답을 반환합니다.

### GET /api/engine/decisions
최근 매매 의사결정 로그 조회.

**쿼리 파라미터**
- `limit`: 기본 30
- `includeSkips`: 기본 true

**응답**
```jsonc
[
  {
    "id": 1,
    "market": "KRW-BTC",
    "action": "BUY",
    "reason": "rsi+macd",
    "executedAt": "2026-02-10T01:45:12.345Z",
    "profile": "CONSERVATIVE",
    "price": 104000000,
    "quantity": 0.0001,
    "funds": 10000,
    "orderId": "upbit-order-uuid",
    "requestStatus": "SUBMITTED",
    "maShort": 103000000,
    "maLong": 102000000,
    "rsi": 60.1,
    "macdHistogram": 12.3,
    "breakoutLevel": 104500000,
    "trailingHigh": 105000000,
    "maLongSlopePct": 0.02,
    "volatilityPct": 0.38,
    "details": {
      "useClosedCandle": true,
      "regimeFilterEnabled": true,
      "regimeFilterPerMarket": true,
      "regimeMarket": "KRW-BTC"
    }
  }
]
```

---

## 전략 설정

### GET /api/strategy
현재 전략 설정 조회.

### PUT /api/strategy
전략 설정 변경.

**요청/응답 필드**
```jsonc
{
  "enabled": true,
  "maxOrderKrw": 10000,
  "takeProfitPct": 3.0,
  "stopLossPct": 1.5,
  "trailingStopPct": 1.0,
  "partialTakeProfitPct": 40.0,
  "profile": "CONSERVATIVE",
  "stopExitPct": 100.0,
  "trendExitPct": 0.0,
  "momentumExitPct": 0.0
}
```

### PATCH /api/strategy/ratios
비율 필드만 부분 업데이트.

**요청**
```jsonc
{
  "takeProfitPct": 3.0,
  "stopLossPct": 1.5,
  "trailingStopPct": 1.0,
  "partialTakeProfitPct": 40.0,
  "stopExitPct": 100.0,
  "trendExitPct": 0.0,
  "momentumExitPct": 0.0
}
```

### GET /api/strategy/presets
기본 프리셋 목록 조회.

**응답**
```jsonc
[
  {
    "code": "AGGRESSIVE",
    "displayName": "공격형",
    "takeProfitPct": 3.0,
    "stopLossPct": 1.5,
    "trailingStopPct": 1.0,
    "partialTakeProfitPct": 40.0,
    "stopExitPct": 100.0,
    "trendExitPct": 0.0,
    "momentumExitPct": 0.0
  }
]
```

### GET /api/strategy/markets
자동매매 대상 마켓 목록 조회.

### PUT /api/strategy/markets
자동매매 대상 마켓 목록 교체.

**요청**
```jsonc
{
  "markets": ["KRW-BTC", "KRW-ETH"]
}
```

**응답**
```jsonc
{
  "markets": ["KRW-BTC", "KRW-ETH"]
}
```

### GET /api/strategy/market-overrides
마켓별 override 조회.

### PUT /api/strategy/market-overrides
마켓별 override 전체 교체.

**요청**
```jsonc
{
  "maxOrderKrwByMarket": {
    "KRW-BTC": 12000
  },
  "profileByMarket": {
    "KRW-BTC": "CONSERVATIVE"
  },
  "ratiosByMarket": {
    "KRW-BTC": {
      "takeProfitPct": 3.0,
      "stopLossPct": 1.5,
      "trailingStopPct": 1.0,
      "partialTakeProfitPct": 40.0,
      "stopExitPct": 100.0,
      "trendExitPct": 0.0,
      "momentumExitPct": 0.0
    }
  }
}
```

**응답**
```jsonc
{
  "markets": ["KRW-BTC", "KRW-ETH"],
  "maxOrderKrwByMarket": {
    "KRW-BTC": 12000
  },
  "profileByMarket": {
    "KRW-BTC": "CONSERVATIVE"
  },
  "ratiosByMarket": {
    "KRW-BTC": {
      "takeProfitPct": 3.0,
      "stopLossPct": 1.5,
      "trailingStopPct": 1.0,
      "partialTakeProfitPct": 40.0,
      "stopExitPct": 100.0,
      "trendExitPct": 0.0,
      "momentumExitPct": 0.0
    }
  }
}
```
