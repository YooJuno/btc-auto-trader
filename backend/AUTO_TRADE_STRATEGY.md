# Auto-Trade Strategy (Baseline, Conservative)

This document proposes a **safe, widely used baseline** for automated trading on Upbit,
with defaults you can adjust. It focuses on simplicity, risk control, and robustness.

## 1) Timeframe (Configurable)
- **Default:** 1-minute candles
- **Allowed units:** 1, 3, 5, 10, 15, 30, 60, 240 minutes

Upbit minute candle API (Korea):  
`GET https://api.upbit.com/v1/candles/minutes/{unit}`  
Notes: A candle is created only when trades occur. If no trades happen in a window,
no candle is returned. This matters for sparse markets.  
Ref: https://docs.upbit.com/kr/reference/%EB%B6%84minute-%EC%BA%94%EB%93%A4-1

**Recommendation:** 1-minute default is okay for rapid reaction, but it is noisier.
For stability, consider 5m or 15m as your production default.

## 2) Strategy Logic (Balanced)
**Trend-following with confirmation signals**
- Compute `MA_SHORT` and `MA_LONG` on closing prices.
- **Trend filter:** `MA_SHORT > MA_LONG` and price above `MA_LONG`.
- **Confirmation signals (need 2 of 3 by default):**
  - RSI: `RSI >= RSI_BUY` and not overbought.
  - MACD: MACD histogram > 0.
  - Breakout: price breaks above recent high by a small buffer.

**Exit logic**
- **Stop-loss:** price below `avg_buy_price * (1 - STOP_LOSS%)`.
- **Trailing stop:** price drops below recent-high * (1 - TRAILING_STOP%).
- **Momentum reversal:** RSI below `RSI_SELL` and MACD histogram < 0.
- **Trend break:** price below `MA_LONG`.
- **Take-profit:** optional partial take-profit before full exit.

**Suggested defaults (balanced)**
- `MA_SHORT = 20`
- `MA_LONG = 100`
- `RSI_PERIOD = 14`
- `RSI_BUY = 55`, `RSI_SELL = 45`, `RSI_OVERBOUGHT = 70`
- `MACD = (12, 26, 9)`
- `BREAKOUT_LOOKBACK = 20`, `BREAKOUT_PCT = 0.3%`
- `STOP_LOSS = 1.5%`
- `TAKE_PROFIT = 3.0%`
- `TRAILING_STOP = 2.0%`
- `PARTIAL_TAKE_PROFIT = 50%` (sell half at take-profit, let rest run)

These values are conservative and easy to reason about. Adjust per timeframe:
- 1m: 20/100 (20 minutes / 100 minutes)
- 5m: 20/100 (100 minutes / 500 minutes)
- 15m: 20/100 (300 minutes / 1500 minutes)

## 3) Risk Controls (Defaults)
Use multiple layers to avoid oversized risk:
- **Max order amount (KRW):** use `maxOrderKrw` (already in config)
- **Min order amount (KRW):** 5,000 KRW (Upbit policy)
- **Cooldown:** avoid repeated orders in short time
- **Pending protection:** do not submit new orders while an open request exists
- **Trailing stop:** protects gains without fixed take-profit
- **Partial take-profit:** locks some profit while keeping exposure

Upbit KRW minimum order amount and tick size rules:  
https://docs.upbit.com/kr/docs/krw-market-info

## 4) Order Types (Decision)
**Current implementation**
- **Entry:** Market order
- **Exit:** Market order

**Why:** to avoid missed fills and keep logic simple.

If you prefer price control for entries:
- Use **limit order** with a short timeout (e.g., 10-20s).
- If not filled, cancel and fall back to market.

Limit orders do not guarantee execution, while market orders do.  
Ref: https://global-docs.upbit.com/reference/order

## 5) Optional Enhancements (Later)
- **Limit-entry with timeout + fallback to market.**
- **Orders chance API:** read per-market supported order types.
  Ref: https://docs.upbit.com/kr/v1.5.8/reference/%EC%A3%BC%EB%AC%B8

## 6) Config Keys (Implemented)
These keys are wired into the current auto-trade engine:
```
signal.timeframe-unit=1
signal.ma-short=20
signal.ma-long=100
signal.rsi-period=14
signal.rsi-buy-threshold=55
signal.rsi-sell-threshold=45
signal.rsi-overbought=70
signal.macd-fast=12
signal.macd-slow=26
signal.macd-signal=9
signal.breakout-lookback=20
signal.breakout-pct=0.3
signal.min-confirmations=2
risk.trailing-window=20
risk.partial-take-profit-cooldown-minutes=120
risk.volatility-window=30
risk.target-vol-pct=0.5
```

Risk parameters `takeProfitPct` / `stopLossPct` / `trailingStopPct`
/ `partialTakeProfitPct` / `maxOrderKrw` are managed via the Strategy API:
- `GET /api/strategy`
- `PUT /api/strategy`

## 7) Next Implementation Steps (If You Approve)
1. Add optional limit-entry with timeout + fallback to market.
2. Add per-market configuration overrides.
