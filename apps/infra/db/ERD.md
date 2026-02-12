# ERD

```mermaid
erDiagram
  portfolio_snapshot {
    BIGSERIAL id PK
    VARCHAR event_type
    VARCHAR source
    TIMESTAMPTZ occurred_at
    TEXT note
  }

  portfolio_snapshot_item {
    BIGSERIAL id PK
    BIGINT snapshot_id FK
    VARCHAR currency
    NUMERIC balance
    NUMERIC locked
    NUMERIC avg_buy_price
    VARCHAR unit_currency
  }

  orders {
    BIGSERIAL id PK
    VARCHAR external_id UK
    VARCHAR client_order_id UK
    VARCHAR market
    VARCHAR side
    VARCHAR type
    VARCHAR ord_type
    VARCHAR state
    VARCHAR status
    NUMERIC price
    NUMERIC volume
    NUMERIC funds
    TIMESTAMPTZ requested_at
    TIMESTAMPTZ created_at
  }

  strategy_config {
    BIGINT id PK
    BOOLEAN enabled
    DOUBLE max_order_krw
    DOUBLE take_profit_pct
    DOUBLE stop_loss_pct
    DOUBLE trailing_stop_pct
    DOUBLE partial_take_profit_pct
    VARCHAR profile
    DOUBLE stop_exit_pct
    DOUBLE trend_exit_pct
    DOUBLE momentum_exit_pct
    TIMESTAMPTZ updated_at
  }

  strategy_markets {
    VARCHAR market PK
    TIMESTAMPTZ updated_at
  }

  strategy_presets {
    VARCHAR code PK
    VARCHAR display_name
    DOUBLE take_profit_pct
    DOUBLE stop_loss_pct
    DOUBLE trailing_stop_pct
    DOUBLE partial_take_profit_pct
    DOUBLE stop_exit_pct
    DOUBLE trend_exit_pct
    DOUBLE momentum_exit_pct
    TIMESTAMPTZ updated_at
  }

  strategy_market_overrides {
    VARCHAR market PK
    DOUBLE max_order_krw
    VARCHAR profile
    TIMESTAMPTZ updated_at
  }

  trade_decisions {
    BIGSERIAL id PK
    VARCHAR market
    VARCHAR action
    VARCHAR reason
    TIMESTAMPTZ executed_at
    VARCHAR profile
    NUMERIC price
    NUMERIC quantity
    NUMERIC funds
    VARCHAR order_id
    VARCHAR request_status
    NUMERIC ma_short
    NUMERIC ma_long
    NUMERIC rsi
    NUMERIC macd_histogram
    NUMERIC breakout_level
    NUMERIC trailing_high
    NUMERIC ma_long_slope_pct
    NUMERIC volatility_pct
    TEXT details
  }

  engine_state {
    BIGINT id PK
    BOOLEAN running
    TIMESTAMPTZ updated_at
  }

  portfolio_snapshot ||--o{ portfolio_snapshot_item : snapshot_id
  strategy_markets ||--o| strategy_market_overrides : market_logical
  orders ||--o{ trade_decisions : order_id_logical
```

## Notes

- The only physical FK in `schema.sql` is:
  - `portfolio_snapshot_item.snapshot_id -> portfolio_snapshot.id`
- `strategy_markets -> strategy_market_overrides` and `orders -> trade_decisions` are logical relationships, not DB-level FK constraints.

