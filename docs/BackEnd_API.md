# BackEnd API

## 공통
- Base URL: `http://localhost:8080`
- 응답 시간/필드는 Upbit 상태에 따라 달라질 수 있음
- `Order` 관련 API는 현재 시뮬레이션 응답

---

## 시세

### GET /api/market/price
현재가 조회.

**호출**
```bash
curl "http://localhost:8080/api/market/price?coin=BTC" # coin: 코인 심볼(예: BTC)
curl "http://localhost:8080/api/market/price?market=KRW-BTC" # market: 마켓 코드(예: KRW-BTC)
```

둘 중 하나 필요하며, `coin`이 있으면 `KRW-`가 자동으로 붙습니다.

**응답**
```jsonc
{
  "queriedAt": "2026-02-03T03:45:12.345+09:00", // 서버 기준 조회 시간(ISO-8601)
  "market": "KRW-BTC", // 요청/정규화된 마켓 코드
  "ticker": { // Upbit ticker 원문 응답
    "market": "KRW-BTC", // 마켓 코드
    "trade_price": 12345678 // 현재가
  }
}
```

---

## 주문 (시뮬레이션)

### POST /api/order
주문 생성. 현재는 시뮬레이션 응답만 반환합니다.

**호출**
```bash
curl -X POST "http://localhost:8080/api/order" \
  -H "Content-Type: application/json" \
  -d '{"market":"KRW-BTC","side":"BUY","type":"MARKET","funds":10000}'

curl -X POST "http://localhost:8080/api/order" \
  -H "Content-Type: application/json" \
  -d '{"market":"KRW-BTC","side":"SELL","type":"LIMIT","price":41000000,"volume":0.002}'
```

**요청 (MARKET BUY)**
```jsonc
{
  "market": "KRW-BTC", // 마켓 코드(예: KRW-BTC)
  "side": "BUY",       // BUY or SELL
  "type": "MARKET",    // MARKET or LIMIT
  "funds": 10000       // 총 주문 금액(MARKET BUY)
}
```

**요청 (LIMIT)**
```jsonc
{
  "market": "KRW-BTC",  // 마켓 코드(예: KRW-BTC)
  "side": "SELL",       // BUY or SELL
  "type": "LIMIT",      // MARKET or LIMIT
  "price": 41000000,    // 지정가 가격(LIMIT)
  "volume": 0.002       // 수량(LIMIT, MARKET SELL)
}
```

**응답**
```jsonc
{
  "orderId": "9f9f1b6d-53a4-4b0b-9b34-7b29a2b8e1a0", // 시뮬레이션 주문 ID
  "status": "SIMULATED", // 상태
  "receivedAt": "2026-02-03T03:45:12.345+09:00", // 서버 기준 수신 시간(ISO-8601)
  "market": "KRW-BTC", // 마켓 코드
  "side": "BUY", // BUY or SELL
  "type": "MARKET", // MARKET or LIMIT
  "price": null, // 지정가 가격 (LIMIT)
  "volume": null, // 수량 (LIMIT, MARKET SELL)
  "funds": 10000 // 총 주문 금액 (MARKET BUY)
}
```

---

## 자동매매 엔진

### POST /api/engine/start
자동매매 엔진 시작.

**호출**
```bash
curl -X POST "http://localhost:8080/api/engine/start"
```

**응답**
```jsonc
{
  "running": true, // 엔진 실행 여부
  "timestamp": "2026-02-03T03:45:12.345+09:00" // 응답 시간(ISO-8601)
}
```

### POST /api/engine/stop
자동매매 엔진 정지.

**호출**
```bash
curl -X POST "http://localhost:8080/api/engine/stop"
```

**응답**
```jsonc
{
  "running": false, // 엔진 실행 여부
  "timestamp": "2026-02-03T03:45:12.345+09:00" // 응답 시간(ISO-8601)
}
```

---

## 전략 설정

### GET /api/strategy
현재 전략 설정 조회.

**호출**
```bash
curl "http://localhost:8080/api/strategy"
```

**응답**
```jsonc
{
  "enabled": true, // 전략 활성화 여부
  "maxOrderKrw": 10000, // 1회 최대 주문 금액 (KRW)
  "takeProfitPct": 3.0, // 익절 퍼센트
  "stopLossPct": 1.5 // 손절 퍼센트
}
```

### PUT /api/strategy
전략 설정 변경.

**호출**
```bash
curl -X PUT "http://localhost:8080/api/strategy" \
  -H "Content-Type: application/json" \
  -d '{"enabled":true,"maxOrderKrw":10000,"takeProfitPct":3.0,"stopLossPct":1.5}'
```

**요청**
```jsonc
{
  "enabled": true,       // 전략 활성화 여부
  "maxOrderKrw": 10000,  // 1회 최대 주문 금액 (KRW)
  "takeProfitPct": 3.0,  // 익절 퍼센트
  "stopLossPct": 1.5     // 손절 퍼센트
}
```

**응답**
```jsonc
{
  "enabled": true, // 전략 활성화 여부
  "maxOrderKrw": 10000, // 1회 최대 주문 금액 (KRW)
  "takeProfitPct": 3.0, // 익절 퍼센트
  "stopLossPct": 1.5 // 손절 퍼센트
}
```
