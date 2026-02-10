#!/usr/bin/env python3
import argparse
import json
import math
import os
import time
import urllib.parse
import urllib.request
from datetime import datetime, timezone

UPBIT_MINUTE_URL = "https://api.upbit.com/v1/candles/minutes/{unit}"


def fetch_candles(market, unit, count, to=None):
    params = {
        "market": market,
        "count": str(count),
    }
    if to:
        params["to"] = to
    url = UPBIT_MINUTE_URL.format(unit=unit) + "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": "btc-auto-trader-backtest"})
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.loads(resp.read().decode("utf-8"))


def load_candles(market, unit, days, cache_dir, sleep_s=0.12):
    total = int(days * 24 * 60 / unit)
    total = max(10, total)
    cache_name = f"{market}_{unit}m_{days}d.json"
    cache_path = os.path.join(cache_dir, cache_name)
    if os.path.exists(cache_path):
        with open(cache_path, "r", encoding="utf-8") as f:
            return json.load(f)

    candles = []
    to = None
    while len(candles) < total:
        batch = fetch_candles(market, unit, 200, to=to)
        if not batch:
            break
        candles.extend(batch)
        if len(candles) % 1000 < len(batch):
            print(f"Fetched {market} {unit}m: {len(candles)}/{total}")
        to = batch[-1].get("candle_date_time_utc")
        time.sleep(sleep_s)

    # Upbit returns newest first; reverse to oldest -> newest
    candles = list(reversed(candles))
    if len(candles) > total:
        candles = candles[-total:]

    slim = [
        {
            "time": c.get("candle_date_time_utc"),
            "open": c.get("opening_price"),
            "high": c.get("high_price"),
            "low": c.get("low_price"),
            "close": c.get("trade_price"),
            "quote": c.get("candle_acc_trade_price"),
        }
        for c in candles
    ]
    with open(cache_path, "w", encoding="utf-8") as f:
        json.dump(slim, f)
    return slim


def average(values):
    return sum(values) / len(values)


def sma(values, window):
    if len(values) < window:
        return None
    return average(values[-window:])


def compute_rsi(closes, period):
    if len(closes) < period + 1:
        return None
    gains = 0.0
    losses = 0.0
    for i in range(len(closes) - period, len(closes)):
        diff = closes[i] - closes[i - 1]
        if diff >= 0:
            gains += diff
        else:
            losses -= diff
    avg_gain = gains / period
    avg_loss = losses / period
    if avg_loss == 0:
        return 100.0
    if avg_gain == 0:
        return 0.0
    rs = avg_gain / avg_loss
    return 100.0 - (100.0 / (1.0 + rs))


def ema_series(values, period):
    if not values or period <= 0:
        return []
    k = 2.0 / (period + 1.0)
    ema = [values[0]]
    prev = values[0]
    for price in values[1:]:
        prev = price * k + prev * (1.0 - k)
        ema.append(prev)
    return ema


def compute_macd_histogram(closes, fast, slow, signal):
    if len(closes) < slow + signal:
        return None
    ema_fast = ema_series(closes, fast)
    ema_slow = ema_series(closes, slow)
    size = min(len(ema_fast), len(ema_slow))
    macd_line = [ema_fast[i] - ema_slow[i] for i in range(size)]
    signal_line = ema_series(macd_line, signal)
    if not signal_line:
        return None
    return macd_line[-1] - signal_line[-1]


def true_range(high, low, prev_close):
    range1 = high - low
    range2 = abs(high - prev_close)
    range3 = abs(low - prev_close)
    return max(range1, range2, range3)


def compute_adx(highs, lows, closes, period):
    size = min(len(highs), len(lows), len(closes))
    if size < period * 2 + 1:
        return None
    tr_smooth = 0.0
    plus_dm_smooth = 0.0
    minus_dm_smooth = 0.0
    for i in range(1, period + 1):
        tr = true_range(highs[i], lows[i], closes[i - 1])
        up_move = highs[i] - highs[i - 1]
        down_move = lows[i - 1] - lows[i]
        plus_dm = up_move if up_move > down_move and up_move > 0 else 0.0
        minus_dm = down_move if down_move > up_move and down_move > 0 else 0.0
        tr_smooth += tr
        plus_dm_smooth += plus_dm
        minus_dm_smooth += minus_dm

    dx_values = [compute_dx(tr_smooth, plus_dm_smooth, minus_dm_smooth)]
    for i in range(period + 1, size):
        tr = true_range(highs[i], lows[i], closes[i - 1])
        up_move = highs[i] - highs[i - 1]
        down_move = lows[i - 1] - lows[i]
        plus_dm = up_move if up_move > down_move and up_move > 0 else 0.0
        minus_dm = down_move if down_move > up_move and down_move > 0 else 0.0
        tr_smooth = tr_smooth - (tr_smooth / period) + tr
        plus_dm_smooth = plus_dm_smooth - (plus_dm_smooth / period) + plus_dm
        minus_dm_smooth = minus_dm_smooth - (minus_dm_smooth / period) + minus_dm
        dx_values.append(compute_dx(tr_smooth, plus_dm_smooth, minus_dm_smooth))

    if len(dx_values) < period:
        return None
    adx = sum(dx_values[:period]) / period
    for i in range(period, len(dx_values)):
        adx = ((adx * (period - 1)) + dx_values[i]) / period
    return adx


def compute_dx(tr_smooth, plus_dm_smooth, minus_dm_smooth):
    if tr_smooth <= 0:
        return 0.0
    plus_di = 100.0 * (plus_dm_smooth / tr_smooth)
    minus_di = 100.0 * (minus_dm_smooth / tr_smooth)
    di_sum = plus_di + minus_di
    if di_sum <= 0:
        return 0.0
    return 100.0 * abs(plus_di - minus_di) / di_sum


def compute_volume_ratio(quote_vols, lookback):
    if len(quote_vols) < lookback + 1:
        return None
    current = quote_vols[-1]
    if current <= 0:
        return 0.0
    avg = average(quote_vols[-lookback - 1:-1])
    if avg <= 0:
        return None
    return current / avg


def highest_high(highs, window, exclude_last):
    if len(highs) < window:
        return None
    end = len(highs) - 1
    if exclude_last:
        end -= 1
    if end < 0:
        return None
    start = max(0, end - window + 1)
    return max(highs[start:end + 1])


def compute_volatility_pct(closes, window):
    if len(closes) < window + 1:
        return None
    returns = []
    for i in range(len(closes) - window, len(closes)):
        prev = closes[i - 1]
        curr = closes[i]
        if prev <= 0:
            continue
        returns.append((curr - prev) / prev)
    if not returns:
        return None
    mean = average(returns)
    variance = average([(r - mean) ** 2 for r in returns])
    return math.sqrt(variance) * 100.0


def compute_bollinger(closes, window, stddev, current_price):
    if window <= 1 or len(closes) < window:
        return None
    middle = sma(closes, window)
    if middle is None:
        return None
    diffs = [(c - middle) ** 2 for c in closes[-window:]]
    variance = average(diffs)
    stdev = math.sqrt(variance)
    deviation = stdev * stddev
    upper = middle + deviation
    lower = middle - deviation
    band = upper - lower
    bandwidth_pct = None
    if middle > 0:
        bandwidth_pct = (band / middle) * 100.0
    percent_b = None
    if band > 0:
        percent_b = (current_price - lower) / band
    return {
        "middle": middle,
        "upper": upper,
        "lower": lower,
        "bandwidth_pct": bandwidth_pct,
        "percent_b": percent_b,
    }


def percent_factor(pct):
    return 1.0 + (pct / 100.0)


def backtest(candles, params):
    closes = []
    highs = []
    lows = []
    quote_vols = []

    cash = params["initial_cash"]
    qty = 0.0
    avg_buy = 0.0
    trailing_high = None

    last_partial_take = None
    last_stop_loss = None
    last_exit = None

    equity_curve = []
    trades = []

    for c in candles:
        closes.append(float(c["close"]))
        highs.append(float(c["high"]))
        lows.append(float(c["low"]))
        quote_vols.append(float(c["quote"]))

        price = closes[-1]
        idx = len(closes)
        # indicators (use minimal slices to keep runtime sane)
        ma_short = sma(closes, params["ma_short"])
        ma_long = sma(closes, params["ma_long"])
        rsi_slice = closes[-(params["rsi_period"] + 1):]
        rsi = compute_rsi(rsi_slice, params["rsi_period"])
        macd_slice = closes[-(params["macd_slow"] + params["macd_signal"] + 1):]
        macd_hist = compute_macd_histogram(macd_slice, params["macd_fast"], params["macd_slow"], params["macd_signal"])
        adx_window = params["adx_period"] * 2 + 1
        adx_slice_high = highs[-adx_window:]
        adx_slice_low = lows[-adx_window:]
        adx_slice_close = closes[-adx_window:]
        adx = compute_adx(adx_slice_high, adx_slice_low, adx_slice_close, params["adx_period"])
        volume_slice = quote_vols[-(params["volume_lookback"] + 1):]
        volume_ratio = compute_volume_ratio(volume_slice, params["volume_lookback"])
        breakout_level = None
        if params["breakout_lookback"] > 1:
            breakout_high = highest_high(highs, params["breakout_lookback"], True)
            if breakout_high is not None:
                breakout_level = breakout_high * percent_factor(params["breakout_pct"])
        ma_long_slope = None
        if params["ma_long_slope_lookback"] > 0 and len(closes) >= params["ma_long"] + params["ma_long_slope_lookback"]:
            prev = sma(closes[:-params["ma_long_slope_lookback"]], params["ma_long"])
            if prev and prev > 0 and ma_long:
                ma_long_slope = ((ma_long - prev) / prev) * 100.0
        vol_slice = closes[-(params["volatility_window"] + 1):]
        volatility_pct = compute_volatility_pct(vol_slice, params["volatility_window"]) if params["target_vol_pct"] > 0 else None
        boll_slice = closes[-params["boll_window"]:]
        boll = compute_bollinger(boll_slice, params["boll_window"], params["boll_stddev"], price)
        window_trailing_high = None
        if params["trailing_window"] > 1 and len(highs) >= params["trailing_window"]:
            window_trailing_high = max(highs[-params["trailing_window"]:])

        # Update equity
        equity_curve.append(cash + qty * price)

        # SELL logic
        if qty > 0:
            if trailing_high is None:
                trailing_high = max(avg_buy, price)
            trailing_high = max(trailing_high, price, window_trailing_high or price)

            stop_loss_threshold = avg_buy * percent_factor(-params["stop_loss_pct"])
            take_profit_threshold = avg_buy * percent_factor(params["take_profit_pct"])
            trailing_stop_threshold = trailing_high * percent_factor(-params["trailing_stop_pct"])

            def can_partial(now):
                if params["partial_take_profit_pct"] <= 0 or params["partial_take_profit_pct"] >= 100:
                    return False
                if last_partial_take is None:
                    return True
                return (now - last_partial_take) >= params["partial_take_profit_cooldown"]

            now = idx
            sold = False

            if price <= stop_loss_threshold:
                sell_pct = params["stop_exit_pct"]
                sold = True
                reason = "stop_loss"
            elif price <= trailing_stop_threshold:
                sell_pct = params["stop_exit_pct"]
                sold = True
                reason = "trailing_stop"
            elif macd_hist is not None and rsi is not None and macd_hist < 0 and rsi < params["rsi_sell_threshold"]:
                sell_pct = params["momentum_exit_pct"]
                sold = True
                reason = "momentum_reversal"
            elif price >= take_profit_threshold:
                if can_partial(now):
                    sell_pct = params["partial_take_profit_pct"]
                    sold = True
                    reason = "take_profit_partial"
                else:
                    sell_pct = 100.0
                    sold = True
                    reason = "take_profit"
            elif ma_long is not None and price < ma_long:
                sell_pct = params["trend_exit_pct"]
                sold = True
                reason = "trend_break"

            if sold and sell_pct > 0:
                fraction = min(1.0, sell_pct / 100.0)
                sell_qty = qty * fraction
                proceeds = sell_qty * price * (1.0 - params["trade_cost_rate"])
                cash += proceeds
                qty -= sell_qty
                if qty <= 1e-12:
                    qty = 0.0
                    avg_buy = 0.0
                    trailing_high = None
                if reason == "take_profit_partial":
                    last_partial_take = now
                if reason in ("stop_loss", "trailing_stop", "momentum_reversal"):
                    last_stop_loss = now
                last_exit = now
                trades.append({"side": "SELL", "price": price, "reason": reason})
            continue

        # BUY logic
        if ma_short is None or ma_long is None or price <= 0:
            continue
        if ma_short <= ma_long or price <= ma_long:
            continue
        if params["ma_long_slope_min"] > 0 and ma_long_slope is None:
            continue
        if ma_long_slope is not None and ma_long_slope < params["ma_long_slope_min"]:
            continue
        if params["max_extension_pct"] > 0:
            max_entry = ma_long * percent_factor(params["max_extension_pct"])
            if price > max_entry:
                continue
        if params["min_adx"] > 0:
            if adx is None or adx < params["min_adx"]:
                continue
        if params["min_volume_ratio"] > 0:
            if volume_ratio is None or volume_ratio < params["min_volume_ratio"]:
                continue
        if params["boll_window"] > 1:
            if params["boll_min_bandwidth_pct"] > 0:
                if boll is None or boll["bandwidth_pct"] is None or boll["bandwidth_pct"] < params["boll_min_bandwidth_pct"]:
                    continue
            if params["boll_max_percent_b"] > 0:
                if boll is None or boll["percent_b"] is None or boll["percent_b"] > params["boll_max_percent_b"]:
                    continue

        rsi_ok = rsi is not None and rsi >= params["rsi_buy_threshold"] and (params["rsi_overbought"] <= 0 or rsi <= params["rsi_overbought"])
        macd_ok = macd_hist is not None and macd_hist > 0
        breakout_ok = breakout_level is not None and price > breakout_level
        confirmations = sum([1 if rsi_ok else 0, 1 if macd_ok else 0, 1 if breakout_ok else 0])
        if confirmations < params["min_confirmations"]:
            continue

        if last_exit is not None and (idx - last_exit) < params["reentry_cooldown"]:
            continue
        if last_stop_loss is not None and (idx - last_stop_loss) < params["stop_loss_cooldown"]:
            continue

        order_funds = min(cash, params["max_order_krw"])
        if params["target_vol_pct"] > 0 and volatility_pct is not None and volatility_pct > 0:
            scale = min(1.0, params["target_vol_pct"] / volatility_pct)
            order_funds *= scale
        order_funds *= (1.0 - params["trade_cost_rate"])
        if order_funds < params["min_order_krw"]:
            continue

        buy_qty = order_funds / price
        cash -= order_funds
        qty += buy_qty
        avg_buy = price if qty > 0 else 0.0
        trailing_high = max(price, avg_buy)
        trades.append({"side": "BUY", "price": price, "reason": "entry"})

    final_value = cash + qty * closes[-1]
    roi = (final_value - params["initial_cash"]) / params["initial_cash"]

    max_dd = 0.0
    peak = -1e18
    for value in equity_curve:
        if value > peak:
            peak = value
        dd = (peak - value) / peak if peak > 0 else 0.0
        if dd > max_dd:
            max_dd = dd

    sell_trades = [t for t in trades if t["side"] == "SELL"]
    return {
        "roi": roi,
        "max_drawdown": max_dd,
        "trades": len(trades),
        "sell_trades": len(sell_trades),
        "final_value": final_value,
    }


def make_params(timeframe_unit):
    return {
        "initial_cash": 1_000_000.0,
        "max_order_krw": 10000.0,
        "min_order_krw": 5000.0,
        "trade_cost_rate": 0.0015,
        "ma_short": 20,
        "ma_long": 100,
        "rsi_period": 14,
        "rsi_buy_threshold": 55,
        "rsi_sell_threshold": 45,
        "rsi_overbought": 70,
        "macd_fast": 12,
        "macd_slow": 26,
        "macd_signal": 9,
        "adx_period": 14,
        "min_adx": 18,
        "volume_lookback": 20,
        "min_volume_ratio": 0.8,
        "boll_window": 20,
        "boll_stddev": 2.0,
        "boll_min_bandwidth_pct": 0.6,
        "boll_max_percent_b": 1.05,
        "breakout_lookback": 20,
        "breakout_pct": 0.3,
        "max_extension_pct": 1.2,
        "ma_long_slope_lookback": 5,
        "ma_long_slope_min": 0.05,
        "min_confirmations": 2,
        "trailing_window": 20,
        "stop_loss_pct": 2.2,
        "take_profit_pct": 4.5,
        "trailing_stop_pct": 2.3,
        "partial_take_profit_pct": 40.0,
        "stop_exit_pct": 100.0,
        "trend_exit_pct": 0.0,
        "momentum_exit_pct": 0.0,
        "reentry_cooldown": int(15 * 60 / timeframe_unit),
        "stop_loss_cooldown": int(30 * 60 / timeframe_unit),
        "partial_take_profit_cooldown": int(120 * 60 / timeframe_unit),
        "volatility_window": 30,
        "target_vol_pct": 0.5,
    }


def summarize(label, result, days, unit):
    trades_per_day = result["sell_trades"] / days
    return {
        "label": label,
        "roi_pct": result["roi"] * 100.0,
        "max_dd_pct": result["max_drawdown"] * 100.0,
        "sell_trades": result["sell_trades"],
        "trades_per_day": trades_per_day,
        "final_value": result["final_value"],
        "unit": unit,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--market", default="KRW-BTC")
    parser.add_argument("--days", type=int, default=30)
    parser.add_argument("--sleep", type=float, default=0.12)
    parser.add_argument("--cache-dir", default="data/backtest")
    parser.add_argument("--short-unit", type=int, default=1)
    parser.add_argument("--mid-unit", type=int, default=15)
    args = parser.parse_args()

    os.makedirs(args.cache_dir, exist_ok=True)

    results = []
    for label, unit in [("short", args.short_unit), ("mid", args.mid_unit)]:
        candles = load_candles(args.market, unit, args.days, args.cache_dir, sleep_s=args.sleep)
        params = make_params(unit)
        result = backtest(candles, params)
        results.append(summarize(label, result, args.days, unit))

    print(json.dumps(results, indent=2))

    # Pick winner: prefer within 0.2~5 trades/day, otherwise highest ROI
    def score(r):
        freq_ok = 0.2 <= r["trades_per_day"] <= 5.0
        return (1 if freq_ok else 0, r["roi_pct"])

    winner = max(results, key=score)
    print("\nRECOMMENDED:")
    print(json.dumps(winner, indent=2))


if __name__ == "__main__":
    main()
