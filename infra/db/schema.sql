-- Minimal schema for single user / Upbit spot
-- Requires pgcrypto for gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE accounts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL,
  mode text NOT NULL CHECK (mode IN ('LIVE','PAPER')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE markets (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  symbol text NOT NULL UNIQUE,
  base_asset text NOT NULL,
  quote_asset text NOT NULL,
  status text NOT NULL
);

CREATE TABLE strategy_config (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  enabled boolean NOT NULL DEFAULT true,
  max_order_krw numeric NOT NULL,
  take_profit_pct numeric NOT NULL,
  stop_loss_pct numeric NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE orders (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES accounts(id),
  market_id uuid NOT NULL REFERENCES markets(id),
  strategy_config_id uuid REFERENCES strategy_config(id),
  side text NOT NULL CHECK (side IN ('BUY','SELL')),
  type text NOT NULL CHECK (type IN ('MARKET','LIMIT')),
  price numeric,
  volume numeric,
  funds numeric,
  status text NOT NULL CHECK (status IN ('NEW','PARTIAL','FILLED','CANCELED','REJECTED')),
  exchange_order_id text UNIQUE,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE fills (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id uuid NOT NULL REFERENCES orders(id),
  fill_price numeric NOT NULL,
  fill_volume numeric NOT NULL,
  fee numeric NOT NULL,
  fee_asset text NOT NULL,
  filled_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE balances (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES accounts(id),
  asset text NOT NULL,
  available numeric NOT NULL,
  locked numeric NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (account_id, asset)
);

CREATE TABLE positions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES accounts(id),
  market_id uuid NOT NULL REFERENCES markets(id),
  base_qty numeric NOT NULL,
  avg_entry_price numeric NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (account_id, market_id)
);

-- Optional: engine state (single row)
-- CREATE TABLE engine_state (
--   id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
--   running boolean NOT NULL DEFAULT false,
--   started_at timestamptz,
--   stopped_at timestamptz,
--   updated_at timestamptz NOT NULL DEFAULT now()
-- );

-- Optional: signals log
-- CREATE TABLE signals (
--   id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
--   account_id uuid NOT NULL REFERENCES accounts(id),
--   market_id uuid NOT NULL REFERENCES markets(id),
--   ts timestamptz NOT NULL DEFAULT now(),
--   signal text NOT NULL CHECK (signal IN ('BUY','SELL','HOLD')),
--   score numeric,
--   details_json jsonb
-- );
