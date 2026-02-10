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
- **Slope filter (optional):** `MA_LONG` must be flat-to-up over recent candles.
- **Overextension filter:** skip entries if price is too far above `MA_LONG`.
- **Trend strength filter:** require ADX above a minimum threshold.
- **Volume quality filter:** require current quote-volume ratio above baseline.
- **Confirmation signals (need 2 of 3 by default):**
  - RSI: `RSI >= RSI_BUY` and not overbought.
  - MACD: MACD histogram > 0.
  - Breakout: price breaks above recent high by a small buffer.

**Exit logic**
- **Stop-loss:** price below `avg_buy_price * (1 - STOP_LOSS%)`.
- **Trailing stop:** price drops below **entry 이후 최고가** * (1 - TRAILING_STOP%).
- **Momentum reversal:** RSI below `RSI_SELL` and MACD histogram < 0.
- **Trend break:** price below `MA_LONG`.
- **Take-profit:** optional partial take-profit before full exit.
 - **Exit sizing:** non-stop exits can be partial; stop/trailing defaults to full exit.

**Suggested defaults (popular baseline)**
- `MA_SHORT = 20`
- `MA_LONG = 100`
- `RSI_PERIOD = 14`
- `RSI_BUY = 55`, `RSI_SELL = 45`, `RSI_OVERBOUGHT = 70`
- `MACD = (12, 26, 9)`
- `BREAKOUT_LOOKBACK = 20`, `BREAKOUT_PCT = 0.3%`
- `STOP_LOSS = 2.0%`
- `TAKE_PROFIT = 4.0%` (1:2 risk/reward baseline)
- `TRAILING_STOP = 2.0%`
- `PARTIAL_TAKE_PROFIT = 50%` (sell half at take-profit, let rest run)
- `STOP_EXIT = 100%` (full exit on stop/trailing)
- `TREND_EXIT = 50%` (partial exit on trend break)
- `MOMENTUM_EXIT = 50%` (partial exit on momentum reversal)

These align with commonly cited risk/reward conventions (1:2 to 1:3) and default
indicator settings (MACD 12-26-9, RSI 70/30 overbought/oversold) used widely in
technical analysis literature and broker education.

## 2-1) Profile Selection (Aggressive/Balanced/Conservative)
Profiles adjust confirmation strictness without changing your core MA settings.

- **AGGRESSIVE**
  - Fewer confirmations (min-confirmations - 1, min 1)
  - Lower RSI entry threshold
  - Smaller breakout buffer
  - Allows larger MA extension, gentler slope requirement
  - Higher overbought ceiling
- **BALANCED**
  - Defaults as listed above
- **CONSERVATIVE**
  - More confirmations (min-confirmations + 1, max 3)
  - Higher RSI entry threshold
  - Larger breakout buffer
  - Tighter MA extension, positive MA slope required
  - Lower overbought ceiling

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
- **Stop-loss cooldown:** avoid immediate re-entry after a loss
- **Re-entry cooldown:** avoid immediate buy after any sell exit
- **Stop-loss guard:** if stop-like exits cluster in a short window, lock buys temporarily
- **Order chance pre-check:** Upbit `orders/chance`로 최소 주문 금액 사전 확인
- **Fee/slippage buffer:** 매수 자금 산정 시 보수적 버퍼 반영
- **State restore:** 재시작 시 최근 SELL 로그로 쿨다운/가드 상태 복원
- **Decision logging:** each tick stores reason/indicator snapshot for audit

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

## 5) Operational Protections (Implemented)
- **Per-market cap:** `trading.market-max-order-krw`로 종목별 최대 매수금액 제한.
- **Per-market profile override:** `trading.market-profile`로 종목별 공격/균형/보수 분리.
- **Per-market backoff:** 특정 종목 실패가 다른 종목 거래를 멈추지 않도록 분리 백오프 적용.
- **Tick budget:** `engine.max-markets-per-tick`로 1회 tick 처리 종목 수 제한(라운드로빈 순환).
- **API rate-limit guard:** Upbit 호출 간격 및 초/분당 요청량 제어.

## 6) Config Keys (Implemented)
These keys are wired into the current auto-trade engine:
```
signal.timeframe-unit=1
signal.use-closed-candle=true
signal.ma-short=20
signal.ma-long=100
signal.rsi-period=14
signal.rsi-buy-threshold=55
signal.rsi-sell-threshold=45
signal.rsi-overbought=70
signal.macd-fast=12
signal.macd-slow=26
signal.macd-signal=9
signal.adx-period=14
signal.min-adx=18
signal.volume-lookback=20
signal.min-volume-ratio=0.8
signal.breakout-lookback=20
signal.breakout-pct=0.3
signal.max-extension-pct=1.2
signal.ma-long-slope-lookback=5
signal.min-confirmations=2
signal.relative-momentum.enabled=true
signal.relative-momentum.timeframe-unit=15
signal.relative-momentum.short-lookback=24
signal.relative-momentum.long-lookback=96
signal.relative-momentum.top-n=3
signal.relative-momentum.min-score-pct=0.0
signal.relative-momentum.cache-minutes=5
trading.market-max-order-krw=
trading.market-profile=
trading.fee-rate=0.0005
trading.slippage-pct=0.001
engine.max-markets-per-tick=0
engine.state-restore-limit=500
risk.trailing-window=20
risk.partial-take-profit-cooldown-minutes=120
risk.stop-loss-cooldown-minutes=30
risk.reentry-cooldown-minutes=15
risk.stop-loss-guard-lookback-minutes=180
risk.stop-loss-guard-trigger-count=3
risk.stop-loss-guard-lock-minutes=180
risk.volatility-window=30
risk.target-vol-pct=0.5
upbit.rate-limit.enabled=true
upbit.rate-limit.min-interval-ms=120
upbit.rate-limit.max-requests-per-second=8
upbit.rate-limit.max-requests-per-minute=240
orders.chance-cache-minutes=5
api.auth.enabled=false
api.auth.header=X-API-KEY
api.auth.key=
```

Risk parameters `takeProfitPct` / `stopLossPct` / `trailingStopPct`
/ `partialTakeProfitPct` / `maxOrderKrw` / `profile`
/ `stopExitPct` / `trendExitPct` / `momentumExitPct` are managed via the Strategy API:
- `GET /api/strategy`
- `PUT /api/strategy`

Per-market cap/profile overrides are managed via:
- `GET /api/strategy/market-overrides`
- `PUT /api/strategy/market-overrides`

If you want to **enforce a specific profile** regardless of API updates, set:
```
strategy.force-profile=CONSERVATIVE
```

## 7) Next Implementation Steps (If You Approve)
1. Add optional limit-entry with timeout + fallback to market.
