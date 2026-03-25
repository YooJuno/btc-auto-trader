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

CREATE INDEX idx_orders_status_requested_at
  ON orders(status, requested_at);

CREATE INDEX idx_orders_market_side_requested_at
  ON orders(market, side, requested_at);

CREATE INDEX idx_orders_client_order_id
  ON orders(client_order_id);

-- 전략 설정
CREATE TABLE strategy_config (
  id                      BIGINT PRIMARY KEY,
  enabled                 BOOLEAN NOT NULL,
  max_order_krw           DOUBLE PRECISION NOT NULL,
  take_profit_pct         DOUBLE PRECISION NOT NULL,
  stop_loss_pct           DOUBLE PRECISION NOT NULL,
  trailing_stop_pct       DOUBLE PRECISION,
  partial_take_profit_pct DOUBLE PRECISION,
  profile                 VARCHAR(255),
  stop_exit_pct           DOUBLE PRECISION,
  trend_exit_pct          DOUBLE PRECISION,
  momentum_exit_pct       DOUBLE PRECISION,
  risk_per_trade_pct      DOUBLE PRECISION,
  updated_at              TIMESTAMPTZ NOT NULL
);

-- 전략 프리셋 (공격형/안정형 등)
CREATE TABLE strategy_presets (
  code                    VARCHAR(40) PRIMARY KEY,
  display_name            VARCHAR(60) NOT NULL,
  take_profit_pct         DOUBLE PRECISION NOT NULL,
  stop_loss_pct           DOUBLE PRECISION NOT NULL,
  trailing_stop_pct       DOUBLE PRECISION NOT NULL,
  partial_take_profit_pct DOUBLE PRECISION NOT NULL,
  stop_exit_pct           DOUBLE PRECISION NOT NULL,
  trend_exit_pct          DOUBLE PRECISION NOT NULL,
  momentum_exit_pct       DOUBLE PRECISION NOT NULL,
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 마켓별 전략 override 설정
CREATE TABLE strategy_market_overrides (
  market                  VARCHAR(20) PRIMARY KEY,
  max_order_krw           DOUBLE PRECISION,
  profile                 VARCHAR(20),
  trade_paused            BOOLEAN DEFAULT false,
  take_profit_pct         DOUBLE PRECISION,
  stop_loss_pct           DOUBLE PRECISION,
  trailing_stop_pct       DOUBLE PRECISION,
  partial_take_profit_pct DOUBLE PRECISION,
  stop_exit_pct           DOUBLE PRECISION,
  trend_exit_pct          DOUBLE PRECISION,
  momentum_exit_pct       DOUBLE PRECISION,
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 매매 결정 기록 (매수/매도/스킵/에러)
CREATE TABLE trade_decisions (
  id                BIGSERIAL PRIMARY KEY,
  market            VARCHAR(20) NOT NULL,
  action            VARCHAR(20) NOT NULL,
  reason            VARCHAR(200),
  executed_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  profile           VARCHAR(20),
  price             NUMERIC(38, 18),
  quantity          NUMERIC(38, 18),
  funds             NUMERIC(38, 18),
  order_id          VARCHAR(60),
  request_status    VARCHAR(20),
  ma_short          NUMERIC(38, 18),
  ma_long           NUMERIC(38, 18),
  rsi               NUMERIC(38, 18),
  macd_histogram    NUMERIC(38, 18),
  breakout_level    NUMERIC(38, 18),
  trailing_high     NUMERIC(38, 18),
  ma_long_slope_pct NUMERIC(38, 18),
  volatility_pct    NUMERIC(38, 18),
  details           TEXT
);

CREATE INDEX idx_trade_decisions_executed_at
  ON trade_decisions(executed_at);

CREATE INDEX idx_trade_decisions_market
  ON trade_decisions(market);

CREATE INDEX idx_trade_decisions_action_executed_at
  ON trade_decisions(action, executed_at);

CREATE INDEX idx_trade_decisions_market_executed_at
  ON trade_decisions(market, executed_at);

-- 엔진 실행 상태 (재시작 시 마지막 ON/OFF 복원)
CREATE TABLE engine_state (
  id          BIGINT PRIMARY KEY,
  running     BOOLEAN NOT NULL,
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- OAuth 사용자 계정
CREATE TABLE app_users (
  id                BIGSERIAL PRIMARY KEY,
  provider          VARCHAR(40) NOT NULL,
  provider_user_id  VARCHAR(120) NOT NULL,
  email             VARCHAR(160),
  display_name      VARCHAR(160),
  tenant_db         VARCHAR(63),
  trading_approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  trading_approval_note VARCHAR(500),
  trading_approval_updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_login_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uk_app_users_provider_subject UNIQUE (provider, provider_user_id)
);

CREATE INDEX idx_app_users_email
  ON app_users(email);

CREATE INDEX idx_app_users_tenant_db
  ON app_users(tenant_db);

-- 사용자별 거래소 API 키(암호화 저장)
CREATE TABLE user_exchange_credentials (
  user_id              BIGINT PRIMARY KEY REFERENCES app_users(id) ON DELETE CASCADE,
  access_key_encrypted TEXT NOT NULL,
  secret_key_encrypted TEXT NOT NULL,
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 사용자별 인터페이스 설정
CREATE TABLE user_settings (
  user_id           BIGINT PRIMARY KEY REFERENCES app_users(id) ON DELETE CASCADE,
  preferred_markets TEXT NOT NULL DEFAULT '[]',
  risk_profile      VARCHAR(20) NOT NULL DEFAULT 'BALANCED',
  ui_prefs          TEXT NOT NULL DEFAULT '{}',
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
