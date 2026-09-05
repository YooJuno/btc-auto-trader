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

## 2) Strategy Logic (as implemented)

> This section describes what `UnifiedTrendSignalModel` and `AutoTradeService.handleSell` actually do.
> Earlier revisions of this document described RSI/MACD "2 of 3 confirmations" and a Bollinger entry
> filter that were never implemented; those claims have been removed rather than left as aspirations.

**Entry — trend-gated Donchian breakout (ALL conditions required)**
- `MA_SHORT > MA_LONG` and price above `MA_LONG`
- `MA_LONG` slope at or above the profile minimum
- ADX at or above `signal.min-adx`
- quote-volume ratio at or above `signal.min-volume-ratio`
- price breaks above the `signal.breakout-lookback` high by `signal.breakout-pct`
- higher-timeframe trend agrees (`signal.htf-confirm.*`)
- market regime allows entries (`regime.filter.*`)

`signal.max-extension-pct` defaults to **0 (disabled)**. Requiring price above the 20-bar high while
also requiring it to stay close to `MA_LONG` are contradictory demands: strong breakouts get rejected
and only weak ones near the MA are taken. Enable it only if you want that behaviour deliberately.

RSI, MACD and Bollinger values are computed and written to the decision log for auditing, but the
entry model does not gate on them.

**Exit (evaluated in this order)**
1. **Stop-loss** — price below `avg_buy_price * (1 - STOP_LOSS%)`. Full exit.
2. **Trailing stop** — price below `post-entry high * (1 - TRAILING_STOP%)`, once armed. Full exit.
3. **Partial take-profit** — price at or above `avg_buy_price * (1 + TAKE_PROFIT%)`, sells
   `PARTIAL_TAKE_PROFIT%` of the position and lets the rest run. Rate-limited by
   `risk.partial-take-profit-cooldown-minutes`.
4. **Donchian exit** — price below the `signal.breakdown-lookback` low. Full exit.
5. **Trend break** — price below `MA_LONG`. Sells `TREND_EXIT%`.
6. **Momentum reversal** — RSI below `RSI_SELL` *and* MACD histogram negative. Sells `MOMENTUM_EXIT%`.

### Exit geometry invariant (important)

The trailing stop arms at `entry * (1 + ARM%)` and then sits at `high * (1 - TRAIL%)`. If `ARM% < TRAIL%`
the stop is **below the entry price at the moment it arms**, so a position that runs up and comes back can
only ever be closed for a loss — there is no path to banking a winner.

`AutoTradeService.resolveConfiguredTrailingArmPct` therefore enforces:

```
ARM% >= TRAIL% + round-trip cost%
```

`AutoTradeServiceTest.trailingArmIsNeverNarrowerThanTheTrailingStop` pins this. Do not remove it.

### Cost floor

Upbit KRW spot charges 0.05% per side to both maker and taker, so a round trip costs at least 0.10%
before slippage; the backtester models 0.15% per side. A strategy is only viable where the average gross
move per round trip is several times that — which is why the default timeframe is **1 hour**, not 1-15
minutes. At 15m the ATR and the transaction cost are the same order of magnitude.

**Defaults (see `application.properties` for the authoritative list)**
- `MA_SHORT = 5`, `MA_LONG = 55` on 1h candles
- `RSI_PERIOD = 14`, `RSI_BUY = 53`, `RSI_SELL = 47`, `RSI_OVERBOUGHT = 68`
- `MACD = (12, 26, 9)`
- `BREAKOUT_LOOKBACK = 20`, `BREAKOUT_PCT = 0.05%`
- ATR-derived stops: stop `2.6 x ATR`, trailing `3.0 x ATR`, arm `3.5 x ATR`
- `PARTIAL_TAKE_PROFIT = 35%`, `STOP_EXIT = 100%`, `TREND_EXIT = 40%`, `MOMENTUM_EXIT = 25%`

Note that `STOP_EXIT` and friends are **position fractions**, not price levels. A value of 0 disables
that exit; stop-loss and trailing stop ignore 0 and always liquidate in full.

## 1-1) Entry Models (registry)

`resolveSignalModel` previously ignored its argument and returned one hardcoded instance — the
pluggability hook existed in name only. Entry models are now registered by name and selected with
`signal.model`. Exits, sizing and every risk control stay shared; a model only decides whether to enter.

| `signal.model` | Entry rule | Use when |
|---|---|---|
| `trend_breakout` (default) | trend gate + Donchian break | continuation in an established trend |
| `squeeze_breakout` | trend gate + Bollinger contraction + break + volume | range expansion out of a quiet period |

`squeeze_breakout` deliberately has **no overextension cap**. Requiring price to break the lookback high
while also staying near `MA_LONG` is self-contradictory, and it is why the original model systematically
took the weakest breaks. It replaces that cap with a contraction requirement, so the entries it takes
have the move size needed to clear a 0.1-0.3% round trip.

Its squeeze test compares current Bollinger bandwidth to a fixed threshold
(`signal.squeeze.max-bandwidth-pct`) rather than to its own trailing percentile. A percentile would adapt
per market and is the textbook form; it needs bandwidth history `MarketIndicators` does not yet carry.

**Adding a model:** implement `TradeSignalModel` (`name()` + `evaluateBuy`) and add it to the registry in
`AutoTradeService`. Per-market selection is not wired yet — `signal.model` is currently a global setting.

## 2-0) Universe Selection (Cross-Sectional Momentum, opt-in)

`signal.universe.enabled=false` by default. When enabled, the engine no longer trades a fixed
hand-typed market list; it ranks the KRW universe and opens new positions only in the leaders.

**Why this exists.** Cross-sectional momentum — hold the strongest names, drop the rest — is the
best-documented edge available to a long-only spot account, and it was entirely missing. It is also
orthogonal to the per-market signal: selection decides *which* markets are eligible, the
trend/breakout model still decides *when* to enter.

**Pipeline**
1. **Risk-off gate.** If `signal.universe.risk-off-market` closes below its
   `signal.universe.risk-off-ma-days` MA, the universe is **empty** — no new entries anywhere.
   Holding the strongest alt through a broad decline is how this strategy family loses money.
2. **Liquidity floor.** One batched ticker call ranks every KRW market by 24h traded value;
   anything under `signal.universe.min-daily-value-krw` is dropped, as is anything Upbit has flagged
   유의종목 (`market_warning`). Only the top `max-candidates` survivors go to step 3, so the daily-candle
   fetch costs ~40 calls, not ~200.
3. **Momentum rank.** Trailing return over `lookback-days`, measured to `skip-days` ago. The most recent
   week is deliberately excluded: short-horizon crypto returns mean-revert, so including it inverts the
   signal. Markets with negative absolute momentum are dropped regardless of rank.
4. **Top-K.** The best `top-k` markets become the tradable universe, cached for `refresh-minutes`.

**Safety property.** The selected universe is always unioned with markets currently held. A position
whose market falls out of the ranking keeps being evaluated, so its stop-loss still runs. Dropping it
from the list would orphan the position. `UniverseSelectionServiceTest` pins this in both the normal and
the risk-off path.

**Known limitation.** This is a momentum *screen* feeding a trend engine, not a periodic portfolio
rebalance with volatility weighting. A true rebalance needs a different execution model than this tick
loop, and is better done deliberately than bolted on.

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
- **Bollinger filter:** 밴드폭이 좁거나 상단 과열(%B)인 경우 진입 회피
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
- **Single-market runtime:** `trading.markets`의 첫 번째 마켓만 자동매매 대상으로 사용.
- **Per-market backoff:** 특정 종목 실패가 다른 종목 거래를 멈추지 않도록 분리 백오프 적용.
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
signal.bollinger.window=20
signal.bollinger.stddev=2.0
signal.bollinger.min-bandwidth-pct=0.6
signal.bollinger.max-percent-b=1.05
signal.breakout-lookback=20
signal.breakout-pct=0.3
signal.max-extension-pct=1.2
signal.ma-long-slope-lookback=5
signal.min-confirmations=2
trading.fee-rate=0.0005
trading.slippage-pct=0.001
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

## 7) Next Implementation Steps (If You Approve)
1. Add optional limit-entry with timeout + fallback to market.
