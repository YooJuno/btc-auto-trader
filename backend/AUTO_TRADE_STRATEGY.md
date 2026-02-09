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

## 2) Strategy Logic (Safe, Popular Baseline)
**Trend-following with a moving-average filter**
- Compute `MA_SHORT` and `MA_LONG` on closing prices.
- **Entry condition:** `MA_SHORT > MA_LONG` and price is above `MA_LONG`.
- **Exit condition:** price falls below `MA_LONG`, or stop-loss is hit.

**Suggested defaults**
- `MA_SHORT = 20`
- `MA_LONG = 100`
- `STOP_LOSS = 1.5%`
- `TAKE_PROFIT = 3.0%`

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

Upbit KRW minimum order amount and tick size rules:  
https://docs.upbit.com/kr/docs/krw-market-info

## 4) Order Types (Decision)
**Default recommendation**
- **Entry:** Market order (guaranteed execution)
- **Exit:** Market order (avoid missing stop-loss)

If you prefer price control for entries:
- Use **limit order** with a short timeout (e.g., 10-20s).
- If not filled, cancel and fall back to market.

Limit orders do not guarantee execution, while market orders do.  
Ref: https://global-docs.upbit.com/reference/order

## 5) Optional Enhancements (Later)
- **Volatility targeting:** adjust position size based on recent volatility.
- **Trailing stop:** protects gains while allowing trend continuation.
- **Orders chance API:** read per-market supported order types.
  Ref: https://docs.upbit.com/kr/v1.5.8/reference/%EC%A3%BC%EB%AC%B8

## 6) Suggested Config Keys (Proposed)
These are not yet wired in code, but recommended if you want a clean config layer:
```
signal.timeframe-unit=1
signal.ma-short=20
signal.ma-long=100
risk.take-profit-pct=3.0
risk.stop-loss-pct=1.5
order.entry-type=market
order.exit-type=market
order.limit-timeout-seconds=15
```

## 7) Next Implementation Steps (If You Approve)
1. Add candle fetching + MA calculation for the configured timeframe.
2. Implement MA-based entry/exit in `AutoTradeService`.
3. Add optional limit-entry with timeout + fallback to market.
4. Add volatility-based sizing (optional).
