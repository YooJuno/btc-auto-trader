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
