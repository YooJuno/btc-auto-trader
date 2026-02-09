-- 이벤트 단위 스냅샷 헤더
CREATE TABLE portfolio_snapshot (
  id            BIGSERIAL PRIMARY KEY,
  event_type    VARCHAR(20) NOT NULL, -- BUY, SELL, DEPOSIT, WITHDRAW, MANUAL, etc
  source        VARCHAR(10) NOT NULL DEFAULT 'UPBIT',
  occurred_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  note          TEXT
);

-- 자산별 잔고 스냅샷
CREATE TABLE portfolio_snapshot_item (
  id              BIGSERIAL PRIMARY KEY,
  snapshot_id     BIGINT NOT NULL REFERENCES portfolio_snapshot(id) ON DELETE CASCADE,
  currency        VARCHAR(10) NOT NULL, -- KRW, BTC, ETH ...
  balance         NUMERIC(38, 18) NOT NULL,
  locked          NUMERIC(38, 18) NOT NULL DEFAULT 0,
  avg_buy_price   NUMERIC(38, 18) NOT NULL DEFAULT 0,
  unit_currency   VARCHAR(10) NOT NULL DEFAULT 'KRW'
);

CREATE INDEX idx_portfolio_snapshot_occurred_at
  ON portfolio_snapshot(occurred_at);

CREATE INDEX idx_portfolio_snapshot_item_snapshot
  ON portfolio_snapshot_item(snapshot_id);

CREATE INDEX idx_portfolio_snapshot_item_currency
  ON portfolio_snapshot_item(currency);

-- 주문 기록 (Upbit Spot)
CREATE TABLE orders (
  id              BIGSERIAL PRIMARY KEY,
  external_id     VARCHAR(60) UNIQUE,
  client_order_id VARCHAR(100) UNIQUE,
  market          VARCHAR(20) NOT NULL,
  side            VARCHAR(10) NOT NULL,
  type            VARCHAR(10) NOT NULL,
  ord_type        VARCHAR(10) NOT NULL,
  state           VARCHAR(20),
  status          VARCHAR(20) NOT NULL,
  price           NUMERIC(38, 18),
  volume          NUMERIC(38, 18),
  funds           NUMERIC(38, 18),
  requested_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at      TIMESTAMPTZ,
  raw_request     TEXT,
  error_message   TEXT,
  raw_response    TEXT
);

CREATE INDEX idx_orders_market
  ON orders(market);

CREATE INDEX idx_orders_requested_at
  ON orders(requested_at);

CREATE INDEX idx_orders_client_order_id
  ON orders(client_order_id);
