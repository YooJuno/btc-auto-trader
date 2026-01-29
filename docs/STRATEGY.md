# Strategy (v1)

This module defines the first-pass trading algorithm used by the engine. It is **signal-only** and does not place orders.

## Inputs
- Candles (OHLCV)
- Bot config (strategy mode, risk preset, max positions)

## Indicators
- EMA (fast/slow)
- RSI (14)
- ATR (14)
- Bollinger Bands (20, 2.0)

## Market regime
- **TREND_UP / TREND_DOWN**: EMA spread above threshold and volatility below cap
- **RANGE**: EMA spread below threshold
- **HIGH_VOLATILITY**: ATR / price above cap

## Strategy selection
- High volatility => cooldown (HOLD)
- Trend regime => TrendFollow
- Range regime => MeanReversion

## Signals
- TrendFollow:
  - BUY: EMA fast > EMA slow + RSI >= buy threshold
  - SELL: EMA fast < EMA slow + RSI <= sell threshold
- MeanReversion:
  - BUY: price below lower band + RSI oversold
  - SELL: price above upper band + RSI overbought

## Risk mapping
- Conservative: 0.3% per trade
- Standard: 0.7% per trade
- Aggressive: 1.2% per trade

## Files
- Strategy engine: `backend/src/main/java/com/hvs/btctrader/strategy/StrategyEngine.java`
- Indicators: `backend/src/main/java/com/hvs/btctrader/strategy/IndicatorService.java`
- Auto-selection scoring: `backend/src/main/java/com/hvs/btctrader/strategy/AutoSelector.java`

## Notes
- Spot-only logic; SELL signals assume an existing position.
- Real execution must check balances and open orders.
