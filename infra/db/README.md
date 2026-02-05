# DB Schema (Minimal, Single User, Upbit Spot)

이 문서는 단일 사용자 / Upbit 현물 기준의 최소 스키마를 정리한 ERD 표입니다.

---

## Tables

**accounts**  
역할: 계정(실전/모의 구분)

| Column | Type | Key | Notes |
| --- | --- | --- | --- |
| id | uuid | PK | |
| name | text | | |
| mode | text | | LIVE / PAPER |
| created_at | timestamptz | | |
| updated_at | timestamptz | | |

**markets**  
역할: 거래 마켓 (예: KRW-BTC)

| Column | Type | Key | Notes |
| --- | --- | --- | --- |
| id | uuid | PK | |
| symbol | text | UQ | KRW-BTC |
| base_asset | text | | BTC |
| quote_asset | text | | KRW |
| status | text | | |

**strategy_config**  
역할: 현재 전략 설정(단일 row)

| Column | Type | Key | Notes |
| --- | --- | --- | --- |
| id | uuid | PK | 단일 row |
| enabled | boolean | | |
| max_order_krw | numeric | | |
| take_profit_pct | numeric | | |
| stop_loss_pct | numeric | | |
| updated_at | timestamptz | | |

**orders**  
역할: 주문 요청/생성 기록

| Column | Type | Key | Notes |
| --- | --- | --- | --- |
| id | uuid | PK | |
| account_id | uuid | FK | accounts.id |
| market_id | uuid | FK | markets.id |
| strategy_config_id | uuid | FK | nullable |
| side | text | | BUY / SELL |
| type | text | | MARKET / LIMIT |
| price | numeric | | nullable |
| volume | numeric | | nullable |
| funds | numeric | | nullable |
| status | text | | NEW / PARTIAL / FILLED / CANCELED / REJECTED |
| exchange_order_id | text | UQ | nullable |
| created_at | timestamptz | | |
| updated_at | timestamptz | | |

**fills**  
역할: 체결 기록(부분체결 포함)

| Column | Type | Key | Notes |
| --- | --- | --- | --- |
| id | uuid | PK | |
| order_id | uuid | FK | orders.id |
| fill_price | numeric | | |
| fill_volume | numeric | | |
| fee | numeric | | |
| fee_asset | text | | |
| filled_at | timestamptz | | |

**balances**  
역할: 자산 잔고 스냅샷

| Column | Type | Key | Notes |
| --- | --- | --- | --- |
| id | uuid | PK | |
| account_id | uuid | FK | accounts.id |
| asset | text | UQ | account_id + asset |
| available | numeric | | |
| locked | numeric | | |
| updated_at | timestamptz | | |

**positions**  
역할: 보유 상태(현물)

| Column | Type | Key | Notes |
| --- | --- | --- | --- |
| id | uuid | PK | |
| account_id | uuid | FK | accounts.id |
| market_id | uuid | FK | markets.id |
| base_qty | numeric | | |
| avg_entry_price | numeric | | |
| updated_at | timestamptz | | |

---

## Optional Tables

**engine_state**  
역할: 엔진 실행 상태(단일 row)

| Column | Type | Key | Notes |
| --- | --- | --- | --- |
| id | uuid | PK | 단일 row |
| running | boolean | | |
| started_at | timestamptz | | |
| stopped_at | timestamptz | | |
| updated_at | timestamptz | | |

**signals**  
역할: 전략 판단 로그

| Column | Type | Key | Notes |
| --- | --- | --- | --- |
| id | uuid | PK | |
| account_id | uuid | FK | accounts.id |
| market_id | uuid | FK | markets.id |
| ts | timestamptz | | |
| signal | text | | BUY / SELL / HOLD |
| score | numeric | | |
| details_json | jsonb | | |

---

## Relationships (Summary)
accounts → orders, balances, positions  
markets → orders, positions  
orders → fills  
strategy_config → orders (nullable)  
engine_state, signals are standalone optional additions  
