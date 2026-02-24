#!/usr/bin/env python3
import argparse
import copy
import glob
import itertools
import json
import math
import os
import re
import time
import urllib.parse
import urllib.request
from collections import deque
from datetime import datetime, timezone
from urllib.error import HTTPError, URLError

UPBIT_MINUTE_URL = "https://api.upbit.com/v1/candles/minutes/{unit}"


def to_float(value, default=0.0):
    try:
        if value is None:
            return default
        return float(value)
    except (TypeError, ValueError):
        return default


def clamp(value, min_value, max_value):
    return max(min_value, min(max_value, value))


def average(values):
    if not values:
        return None
    return sum(values) / len(values)


def stddev(values):
    if not values:
        return None
    mean = average(values)
    variance = average([(v - mean) ** 2 for v in values])
    return math.sqrt(variance)


def parse_time_utc(text):
    if not text:
        return None
    try:
        dt = datetime.fromisoformat(text)
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return dt.astimezone(timezone.utc)
    except ValueError:
        return None


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


def parse_cache_days(filename, market, unit):
    pattern = rf"^{re.escape(market)}_{unit}m_(\d+)d\\.json$"
    match = re.match(pattern, filename)
    if not match:
        return None
    return int(match.group(1))


def load_best_cache(market, unit, days, cache_dir):
    pattern = os.path.join(cache_dir, f"{market}_{unit}m_*d.json")
    candidates = []
    for path in glob.glob(pattern):
        parsed_days = parse_cache_days(os.path.basename(path), market, unit)
        if parsed_days is None:
            continue
        candidates.append((parsed_days, path))

    if not candidates:
        return None

    candidates.sort(key=lambda item: item[0])
    selected_days = None
    selected_path = None

    for item_days, item_path in candidates:
        if item_days >= days:
            selected_days = item_days
            selected_path = item_path
            break

    if selected_path is None:
        selected_days, selected_path = candidates[-1]

    with open(selected_path, "r", encoding="utf-8") as file:
        cached = json.load(file)

    candles = sanitize_candles(cached)
    if not candles:
        return None
    return {
        "candles": candles,
        "days": selected_days,
        "path": selected_path,
    }


def resample_candles(candles, source_unit, target_unit):
    if target_unit <= source_unit:
        return candles
    if target_unit % source_unit != 0:
        return None

    factor = target_unit // source_unit
    if factor <= 1:
        return candles

    usable_count = (len(candles) // factor) * factor
    if usable_count <= 0:
        return None

    rows = candles[-usable_count:]
    resampled = []
    for i in range(0, len(rows), factor):
        chunk = rows[i:i + factor]
        if len(chunk) < factor:
            continue
        resampled.append({
            "time": chunk[-1]["time"],
            "open": chunk[0]["open"],
            "high": max(c["high"] for c in chunk),
            "low": min(c["low"] for c in chunk),
            "close": chunk[-1]["close"],
            "quote": sum(c["quote"] for c in chunk),
        })
    return resampled


def sanitize_candles(raw_rows):
    sanitized = []
    for row in raw_rows:
        time_utc = row.get("time") or row.get("candle_date_time_utc")
        open_p = to_float(row.get("open", row.get("opening_price")))
        high_p = to_float(row.get("high", row.get("high_price")))
        low_p = to_float(row.get("low", row.get("low_price")))
        close_p = to_float(row.get("close", row.get("trade_price")))
        quote = to_float(row.get("quote", row.get("candle_acc_trade_price")))

        if open_p <= 0 or high_p <= 0 or low_p <= 0 or close_p <= 0:
            continue

        sanitized.append({
            "time": time_utc,
            "open": open_p,
            "high": high_p,
            "low": low_p,
            "close": close_p,
            "quote": max(0.0, quote),
        })

    # Sort oldest -> newest and drop duplicated timestamps.
    sanitized.sort(key=lambda x: (parse_time_utc(x["time"]) or datetime.min.replace(tzinfo=timezone.utc), x["close"]))
    deduped = []
    seen = set()
    for row in sanitized:
        t = row.get("time")
        if t and t in seen:
            continue
        if t:
            seen.add(t)
        deduped.append(row)
    return deduped


def load_candles(market, unit, days, cache_dir, sleep_s=0.12, refresh_cache=False):
    total = int(days * 24 * 60 / unit)
    total = max(100, total)

    cache_name = f"{market}_{unit}m_{days}d.json"
    cache_path = os.path.join(cache_dir, cache_name)

    if os.path.exists(cache_path) and not refresh_cache:
        with open(cache_path, "r", encoding="utf-8") as file:
            cached = json.load(file)
        candles = sanitize_candles(cached)
        if candles:
            return candles[-total:]

    if not refresh_cache:
        best_cache = load_best_cache(market, unit, days, cache_dir)
        if best_cache:
            return best_cache["candles"][-total:]

        source_cache = load_best_cache(market, 1, days, cache_dir)
        if source_cache:
            resampled = resample_candles(source_cache["candles"], 1, unit)
            if resampled:
                if len(resampled) > total:
                    resampled = resampled[-total:]
                with open(cache_path, "w", encoding="utf-8") as file:
                    json.dump(resampled, file, ensure_ascii=False)
                print(f"Loaded {market} {unit}m from 1m cache ({source_cache['days']}d).")
                return resampled

    rows = []
    to = None
    backoff_seconds = max(1.0, sleep_s * 10.0)
    rate_limit_retries = 0
    while len(rows) < total:
        try:
            batch = fetch_candles(market, unit, 200, to=to)
        except HTTPError as exc:
            if exc.code == 429:
                rate_limit_retries += 1
                if rate_limit_retries >= 10:
                    print(f"Rate limit retries exceeded for {market} {unit}m; using partial data.")
                    break
                print(f"Rate limited for {market} {unit}m, sleeping {backoff_seconds:.1f}s...")
                time.sleep(backoff_seconds)
                backoff_seconds = min(backoff_seconds * 1.7, 20.0)
                continue
            raise
        except URLError:
            if rows:
                break
            raise
        if not batch:
            break
        rows.extend(batch)
        backoff_seconds = max(1.0, sleep_s * 10.0)
        rate_limit_retries = 0

        if len(rows) % 1000 < len(batch):
            print(f"Fetched {market} {unit}m: {len(rows)}/{total}")

        to = batch[-1].get("candle_date_time_utc")
        time.sleep(sleep_s)

    candles = sanitize_candles(rows)
    if len(candles) > total:
        candles = candles[-total:]

    with open(cache_path, "w", encoding="utf-8") as file:
        json.dump(candles, file, ensure_ascii=False)

    return candles


def sma(values, window):
    if window <= 0 or len(values) < window:
        return None
    return average(values[-window:])


def sma_with_offset(values, window, offset):
    if window <= 0 or offset < 0:
        return None
    end = len(values) - 1 - offset
    start = end - window + 1
    if start < 0 or end < 0:
        return None
    return average(values[start:end + 1])


def compute_rsi(closes, period):
    if period <= 0 or len(closes) < period + 1:
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
    for value in values[1:]:
        prev = value * k + prev * (1.0 - k)
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


def compute_dx(tr_smooth, plus_dm_smooth, minus_dm_smooth):
    if tr_smooth <= 0:
        return 0.0

    plus_di = 100.0 * (plus_dm_smooth / tr_smooth)
    minus_di = 100.0 * (minus_dm_smooth / tr_smooth)
    di_sum = plus_di + minus_di
    if di_sum <= 0:
        return 0.0

    return 100.0 * abs(plus_di - minus_di) / di_sum


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


def compute_volume_ratio(quote_vols, lookback):
    if len(quote_vols) < lookback + 1:
        return None

    current = quote_vols[-1]
    if current <= 0:
        return 0.0

    avg_volume = average(quote_vols[-lookback - 1:-1])
    if avg_volume is None or avg_volume <= 0:
        return None

    return current / avg_volume


def highest_high(highs, window, exclude_last):
    if window <= 0 or len(highs) < window:
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

    sigma = stddev(returns)
    if sigma is None:
        return None
    return sigma * 100.0


def compute_bollinger(closes, window, stddev_multiplier, current_price):
    if window <= 1 or len(closes) < window:
        return None

    middle = sma(closes, window)
    if middle is None:
        return None

    diffs = [(c - middle) ** 2 for c in closes[-window:]]
    variance = average(diffs)
    stdev = math.sqrt(variance)
    deviation = stdev * stddev_multiplier

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


def minutes_to_candles(minutes, unit):
    if minutes <= 0:
        return 0
    return int(math.ceil(minutes / unit))


def required_history(params):
    required = max(params["ma_short"], params["ma_long"])
    required = max(required, params["rsi_period"] + 1)
    required = max(required, params["macd_slow"] + params["macd_signal"])
    required = max(required, params["adx_period"] * 2 + 1)
    required = max(required, params["volume_lookback"] + 1)

    if params["boll_window"] > 1:
        required = max(required, params["boll_window"])
    if params["breakout_lookback"] > 1:
        required = max(required, params["breakout_lookback"] + 1)
    if params["trailing_window"] > 1:
        required = max(required, params["trailing_window"])
    if params["ma_long_slope_lookback"] > 0:
        required = max(required, params["ma_long"] + params["ma_long_slope_lookback"])
    if params["target_vol_pct"] > 0 and params["volatility_window"] > 1:
        required = max(required, params["volatility_window"] + 1)

    return required


def regime_required_history(params):
    required = params["regime_ma_long"]
    if params["regime_ma_long_slope_lookback"] > 0:
        required = max(required, params["regime_ma_long"] + params["regime_ma_long_slope_lookback"])
    if params["regime_volatility_window"] > 1:
        required = max(required, params["regime_volatility_window"] + 1)
    return required


def resolve_signal_tuning(params):
    profile = str(params.get("profile", "BALANCED")).upper()

    rsi_buy = params["rsi_buy_threshold"]
    rsi_sell = params["rsi_sell_threshold"]
    rsi_over = params["rsi_overbought"]
    min_adx = params["min_adx"]
    min_volume_ratio = params["min_volume_ratio"]
    breakout_pct = params["breakout_pct"]
    max_extension_pct = params["max_extension_pct"]
    min_ma_long_slope_pct = 0.0
    min_confirmations = params["min_confirmations"]

    if profile == "AGGRESSIVE":
        rsi_buy -= 5.0
        rsi_sell -= 5.0
        rsi_over += 10.0
        min_adx -= 4.0
        min_volume_ratio -= 0.15
        breakout_pct *= 0.5
        max_extension_pct *= 1.5
        min_ma_long_slope_pct = -0.1
        min_confirmations -= 1
    elif profile == "CONSERVATIVE":
        rsi_buy += 5.0
        rsi_sell += 5.0
        rsi_over -= 5.0
        min_adx += 4.0
        min_volume_ratio += 0.15
        breakout_pct *= 1.5
        max_extension_pct *= 0.7
        min_ma_long_slope_pct = 0.05
        min_confirmations += 1

    return {
        "rsi_buy": clamp(rsi_buy, 40.0, 80.0),
        "rsi_sell": clamp(rsi_sell, 30.0, 70.0),
        "rsi_over": clamp(rsi_over, 60.0, 90.0),
        "min_adx": clamp(min_adx, 5.0, 60.0),
        "min_volume_ratio": clamp(min_volume_ratio, 0.1, 3.0),
        "breakout_pct": clamp(breakout_pct, 0.05, 3.0),
        "max_extension_pct": clamp(max_extension_pct, 0.2, 5.0),
        "min_ma_long_slope_pct": clamp(min_ma_long_slope_pct, -0.5, 1.0),
        "min_confirmations": int(clamp(min_confirmations, 1, 3)),
    }


def build_indicators(closes, highs, lows, quote_vols, params, tuning):
    if len(closes) < required_history(params):
        return None

    current_price = closes[-1]
    ma_short = sma(closes, params["ma_short"])
    ma_long = sma(closes, params["ma_long"])

    rsi_window = params["rsi_period"] + 1
    rsi = compute_rsi(closes[-rsi_window:], params["rsi_period"])

    macd_window = params["macd_slow"] + params["macd_signal"] + 50
    macd_hist = compute_macd_histogram(
        closes[-macd_window:],
        params["macd_fast"],
        params["macd_slow"],
        params["macd_signal"],
    )

    adx_window = params["adx_period"] * 2 + 1
    adx = compute_adx(
        highs[-adx_window:],
        lows[-adx_window:],
        closes[-adx_window:],
        params["adx_period"],
    )

    volume_ratio = compute_volume_ratio(quote_vols[-(params["volume_lookback"] + 1):], params["volume_lookback"])

    bollinger = compute_bollinger(
        closes[-params["boll_window"]:] if params["boll_window"] > 1 else closes,
        params["boll_window"],
        params["boll_stddev"],
        current_price,
    )

    ma_long_slope = None
    if params["ma_long_slope_lookback"] > 0:
        previous = sma_with_offset(closes, params["ma_long"], params["ma_long_slope_lookback"])
        if previous is not None and previous > 0 and ma_long is not None:
            ma_long_slope = ((ma_long - previous) / previous) * 100.0

    breakout_level = None
    if params["breakout_lookback"] > 1:
        breakout_high = highest_high(highs, params["breakout_lookback"], True)
        if breakout_high is not None:
            breakout_level = breakout_high * percent_factor(tuning["breakout_pct"])

    trailing_window_high = None
    if params["trailing_window"] > 1 and len(highs) >= params["trailing_window"]:
        trailing_window_high = max(highs[-params["trailing_window"]:])

    volatility_pct = None
    if params["target_vol_pct"] > 0 and params["volatility_window"] > 1:
        volatility_pct = compute_volatility_pct(closes[-(params["volatility_window"] + 1):], params["volatility_window"])

    return {
        "current_price": current_price,
        "ma_short": ma_short,
        "ma_long": ma_long,
        "rsi": rsi,
        "macd_hist": macd_hist,
        "adx": adx,
        "volume_ratio": volume_ratio,
        "bollinger": bollinger,
        "breakout_level": breakout_level,
        "trailing_window_high": trailing_window_high,
        "ma_long_slope": ma_long_slope,
        "volatility_pct": volatility_pct,
    }


def evaluate_regime(closes, params):
    if not params["regime_filter_enabled"]:
        return {"allow_entries": True, "reason": "regime_disabled"}

    if len(closes) < regime_required_history(params):
        return {"allow_entries": False, "reason": "regime_insufficient_data"}

    current_price = closes[-1]
    ma_short = sma(closes, params["regime_ma_short"])
    ma_long = sma(closes, params["regime_ma_long"])
    if ma_short is None or ma_long is None or ma_long <= 0:
        return {"allow_entries": False, "reason": "regime_invalid_trend"}

    slope_pct = None
    if params["regime_ma_long_slope_lookback"] > 0:
        previous = sma_with_offset(closes, params["regime_ma_long"], params["regime_ma_long_slope_lookback"])
        if previous is not None and previous > 0:
            slope_pct = ((ma_long - previous) / previous) * 100.0
        elif params["regime_min_ma_long_slope_pct"] > 0:
            return {"allow_entries": False, "reason": "regime_missing_slope"}

    volatility_pct = None
    if params["regime_volatility_window"] > 1:
        volatility_pct = compute_volatility_pct(closes, params["regime_volatility_window"])

    if current_price <= ma_long or ma_short <= ma_long:
        return {"allow_entries": False, "reason": "regime_trend_off"}

    if (
        params["regime_min_ma_long_slope_pct"] > 0
        and slope_pct is not None
        and slope_pct < params["regime_min_ma_long_slope_pct"]
    ):
        return {"allow_entries": False, "reason": "regime_slope_off"}

    if (
        params["regime_max_volatility_pct"] > 0
        and volatility_pct is not None
        and volatility_pct > params["regime_max_volatility_pct"]
    ):
        return {"allow_entries": False, "reason": "regime_high_vol"}

    return {"allow_entries": True, "reason": "regime_on"}


def is_stop_loss_guard_active(index, state):
    until_index = state.get("stop_loss_guard_until_index")
    if until_index is None:
        return False
    if index >= until_index:
        state["stop_loss_guard_until_index"] = None
        return False
    return True


def record_protective_exit(index, state, params):
    lookback = params["stop_loss_guard_lookback_candles"]
    trigger_count = params["stop_loss_guard_trigger_count"]
    lock_candles = params["stop_loss_guard_lock_candles"]

    if lookback <= 0 or trigger_count <= 0 or lock_candles <= 0:
        return

    events = state["stop_loss_events"]
    while events and (index - events[0]) > lookback:
        events.popleft()

    events.append(index)
    if len(events) >= trigger_count:
        state["stop_loss_guard_until_index"] = index + lock_candles


def can_take_partial_profit(index, state, params):
    cooldown = params["partial_take_profit_cooldown_candles"]
    if cooldown <= 0:
        return True
    last_index = state.get("last_partial_take_index")
    if last_index is None:
        return True
    return (index - last_index) >= cooldown


def choose_sell_intent(index, state, indicators, params, tuning):
    qty = state["qty"]
    if qty <= 0:
        return None

    avg_buy = state["avg_buy"]
    current_price = indicators["current_price"]
    if avg_buy <= 0 or current_price <= 0:
        return None

    trailing_high = state["trailing_high"]
    candidates = [avg_buy, current_price, indicators.get("trailing_window_high") or 0.0]
    for candidate in candidates:
        if candidate and candidate > 0:
            trailing_high = max(trailing_high or candidate, candidate)
    state["trailing_high"] = trailing_high

    stop_loss_threshold = avg_buy * percent_factor(-params["stop_loss_pct"])
    take_profit_threshold = avg_buy * percent_factor(params["take_profit_pct"])

    trailing_stop_threshold = None
    if params["trailing_stop_pct"] > 0 and trailing_high is not None and trailing_high > 0:
        trailing_stop_threshold = trailing_high * percent_factor(-params["trailing_stop_pct"])

    if current_price <= stop_loss_threshold:
        return {
            "type": "SELL_PCT",
            "pct": params["stop_exit_pct"],
            "reason": "stop_loss",
            "allow_full_fallback": True,
        }

    if trailing_stop_threshold is not None and current_price <= trailing_stop_threshold:
        return {
            "type": "SELL_PCT",
            "pct": params["stop_exit_pct"],
            "reason": "trailing_stop",
            "allow_full_fallback": True,
        }

    if (
        indicators["macd_hist"] is not None
        and indicators["rsi"] is not None
        and indicators["macd_hist"] < 0
        and indicators["rsi"] < tuning["rsi_sell"]
    ):
        return {
            "type": "SELL_PCT",
            "pct": params["momentum_exit_pct"],
            "reason": "momentum_reversal",
            "allow_full_fallback": False,
        }

    if current_price >= take_profit_threshold:
        partial_pct = params["partial_take_profit_pct"]
        if 0 < partial_pct < 100:
            if not can_take_partial_profit(index, state, params):
                return None

            # Engine falls back to full TP if partial is too small for min order.
            partial_est = qty * (partial_pct / 100.0) * current_price * (1.0 - params["trade_cost_rate"])
            full_est = qty * current_price * (1.0 - params["trade_cost_rate"])
            if partial_est < params["min_order_krw"] <= full_est:
                return {
                    "type": "SELL_PCT",
                    "pct": 100.0,
                    "reason": "take_profit",
                    "allow_full_fallback": False,
                }

            return {
                "type": "SELL_PCT",
                "pct": partial_pct,
                "reason": "take_profit_partial",
                "allow_full_fallback": False,
            }

        return {
            "type": "SELL_PCT",
            "pct": 100.0,
            "reason": "take_profit",
            "allow_full_fallback": False,
        }

    if indicators["ma_long"] is not None and current_price < indicators["ma_long"]:
        return {
            "type": "SELL_PCT",
            "pct": params["trend_exit_pct"],
            "reason": "trend_break",
            "allow_full_fallback": False,
        }

    return None


def choose_buy_intent(index, state, indicators, params, tuning):
    cash = state["cash"]
    price = indicators["current_price"]
    ma_short = indicators["ma_short"]
    ma_long = indicators["ma_long"]

    if ma_short is None or ma_long is None or price <= 0:
        return None

    if ma_short <= ma_long or price <= ma_long:
        return None

    if tuning["min_ma_long_slope_pct"] > 0 and indicators["ma_long_slope"] is None:
        return None

    if indicators["ma_long_slope"] is not None and indicators["ma_long_slope"] < tuning["min_ma_long_slope_pct"]:
        return None

    if tuning["max_extension_pct"] > 0:
        max_entry = ma_long * percent_factor(tuning["max_extension_pct"])
        if price > max_entry:
            return None

    if tuning["min_adx"] > 0:
        if indicators["adx"] is None or indicators["adx"] < tuning["min_adx"]:
            return None

    if tuning["min_volume_ratio"] > 0:
        if indicators["volume_ratio"] is None or indicators["volume_ratio"] < tuning["min_volume_ratio"]:
            return None

    if params["boll_window"] > 1:
        bollinger = indicators["bollinger"]
        if params["boll_min_bandwidth_pct"] > 0:
            if bollinger is None or bollinger["bandwidth_pct"] is None or bollinger["bandwidth_pct"] < params["boll_min_bandwidth_pct"]:
                return None
        if params["boll_max_percent_b"] > 0:
            if bollinger is None or bollinger["percent_b"] is None or bollinger["percent_b"] > params["boll_max_percent_b"]:
                return None

    rsi_ok = (
        indicators["rsi"] is not None
        and indicators["rsi"] >= tuning["rsi_buy"]
        and (tuning["rsi_over"] <= 0 or indicators["rsi"] <= tuning["rsi_over"])
    )
    macd_ok = indicators["macd_hist"] is not None and indicators["macd_hist"] > 0
    breakout_ok = indicators["breakout_level"] is not None and price > indicators["breakout_level"]

    confirmations = (1 if rsi_ok else 0) + (1 if macd_ok else 0) + (1 if breakout_ok else 0)
    if confirmations < tuning["min_confirmations"]:
        return None

    if is_stop_loss_guard_active(index, state):
        return None

    last_exit = state.get("last_exit_index")
    if last_exit is not None and (index - last_exit) < params["reentry_cooldown_candles"]:
        return None

    last_stop_loss = state.get("last_stop_loss_index")
    if last_stop_loss is not None and (index - last_stop_loss) < params["stop_loss_cooldown_candles"]:
        return None

    order_funds = min(cash, params["max_order_krw"])

    if params["target_vol_pct"] > 0 and indicators["volatility_pct"] is not None and indicators["volatility_pct"] > 0:
        scale = min(1.0, params["target_vol_pct"] / indicators["volatility_pct"])
        order_funds *= scale

    if order_funds < params["min_order_krw"]:
        return None

    return {
        "type": "BUY",
        "funds": order_funds,
        "reason": "entry",
    }


def execute_intent(index, intent, execution_price, state, params, trades):
    if intent is None or execution_price <= 0:
        return

    cost_rate = params["trade_cost_rate"]

    if intent["type"] == "BUY":
        funds = min(state["cash"], intent.get("funds", 0.0))
        if funds < params["min_order_krw"]:
            return

        qty = funds / (execution_price * (1.0 + cost_rate))
        if qty <= 0:
            return

        spent = qty * execution_price * (1.0 + cost_rate)
        spent = min(spent, state["cash"])

        prev_qty = state["qty"]
        prev_avg = state["avg_buy"]
        new_qty = prev_qty + qty
        avg_entry_price = execution_price * (1.0 + cost_rate)

        if new_qty > 0:
            state["avg_buy"] = ((prev_avg * prev_qty) + (avg_entry_price * qty)) / new_qty
        state["qty"] = new_qty
        state["cash"] -= spent
        state["trailing_high"] = max(state.get("trailing_high") or execution_price, execution_price)

        trades.append({
            "index": index,
            "time": state["times"][index],
            "side": "BUY",
            "price": execution_price,
            "qty": qty,
            "notional": spent,
            "reason": intent.get("reason", "entry"),
        })
        return

    if intent["type"] == "SELL_PCT":
        pct = clamp(intent.get("pct", 0.0), 0.0, 100.0)
        if pct <= 0:
            return

        position_qty = state["qty"]
        if position_qty <= 0:
            return

        sell_qty = position_qty if pct >= 100.0 else position_qty * (pct / 100.0)
        if sell_qty <= 0:
            return

        proceeds = sell_qty * execution_price * (1.0 - cost_rate)
        if proceeds < params["min_order_krw"]:
            if intent.get("allow_full_fallback", False):
                full_proceeds = position_qty * execution_price * (1.0 - cost_rate)
                if full_proceeds >= params["min_order_krw"]:
                    sell_qty = position_qty
                    proceeds = full_proceeds
                else:
                    return
            else:
                return

        cost_basis = sell_qty * state["avg_buy"]
        realized_pnl = proceeds - cost_basis

        state["cash"] += proceeds
        state["qty"] -= sell_qty
        if state["qty"] <= 1e-12:
            state["qty"] = 0.0
            state["avg_buy"] = 0.0
            state["trailing_high"] = None

        reason = intent.get("reason", "sell")
        state["last_exit_index"] = index
        if reason == "take_profit_partial":
            state["last_partial_take_index"] = index

        if reason.startswith("stop_loss") or reason.startswith("trailing_stop") or reason.startswith("momentum_reversal"):
            state["last_stop_loss_index"] = index
            record_protective_exit(index, state, params)

        trades.append({
            "index": index,
            "time": state["times"][index],
            "side": "SELL",
            "price": execution_price,
            "qty": sell_qty,
            "notional": proceeds,
            "reason": reason,
            "realized_pnl": realized_pnl,
        })


def max_drawdown(equity_curve):
    if not equity_curve:
        return 0.0

    peak = equity_curve[0]
    max_dd = 0.0
    for value in equity_curve:
        if value > peak:
            peak = value
        if peak > 0:
            dd = (peak - value) / peak
            if dd > max_dd:
                max_dd = dd
    return max_dd


def annualized_sharpe(equity_curve, unit):
    if len(equity_curve) < 3 or unit <= 0:
        return None

    returns = []
    for i in range(1, len(equity_curve)):
        prev = equity_curve[i - 1]
        curr = equity_curve[i]
        if prev <= 0:
            continue
        returns.append((curr - prev) / prev)

    if len(returns) < 2:
        return None

    sigma = stddev(returns)
    if sigma is None or sigma <= 0:
        return None

    mean_return = average(returns)
    periods_per_year = (365.0 * 24.0 * 60.0) / unit
    return (mean_return / sigma) * math.sqrt(periods_per_year)


def build_metrics(initial_cash, final_value, equity_curve, trades, unit, candles_count, position_candles):
    roi = (final_value - initial_cash) / initial_cash if initial_cash > 0 else 0.0
    mdd = max_drawdown(equity_curve)

    days = (candles_count * unit) / (24.0 * 60.0) if unit > 0 else 0.0
    years = days / 365.0 if days > 0 else 0.0
    cagr = None
    if years > 0 and initial_cash > 0 and final_value > 0:
        cagr = (final_value / initial_cash) ** (1.0 / years) - 1.0

    sell_trades = [t for t in trades if t["side"] == "SELL"]
    realized = [t.get("realized_pnl") for t in sell_trades if t.get("realized_pnl") is not None]
    wins = [p for p in realized if p > 0]
    losses = [p for p in realized if p < 0]

    gross_profit = sum(wins) if wins else 0.0
    gross_loss = abs(sum(losses)) if losses else 0.0

    win_rate = None
    if realized:
        win_rate = len(wins) / len(realized)

    profit_factor = None
    if gross_loss > 0:
        profit_factor = gross_profit / gross_loss

    expectancy = None
    if realized:
        expectancy = average(realized)

    trades_per_day = len(sell_trades) / days if days > 0 else 0.0
    sharpe = annualized_sharpe(equity_curve, unit)

    exposure = (position_candles / candles_count) if candles_count > 0 else 0.0

    return {
        "roi_pct": roi * 100.0,
        "max_drawdown_pct": mdd * 100.0,
        "final_value": final_value,
        "sell_trades": len(sell_trades),
        "total_trades": len(trades),
        "trades_per_day": trades_per_day,
        "win_rate_pct": (win_rate * 100.0) if win_rate is not None else None,
        "profit_factor": profit_factor,
        "expectancy_krw": expectancy,
        "gross_profit_krw": gross_profit,
        "gross_loss_krw": -gross_loss,
        "cagr_pct": (cagr * 100.0) if cagr is not None else None,
        "sharpe": sharpe,
        "exposure_pct": exposure * 100.0,
        "days": days,
    }


def backtest_strategy(candles, params, unit):
    if not candles:
        return {
            "metrics": build_metrics(params["initial_cash"], params["initial_cash"], [], [], unit, 0, 0),
            "trades": [],
        }

    state = {
        "cash": params["initial_cash"],
        "qty": 0.0,
        "avg_buy": 0.0,
        "trailing_high": None,
        "last_partial_take_index": None,
        "last_stop_loss_index": None,
        "last_exit_index": None,
        "stop_loss_guard_until_index": None,
        "stop_loss_events": deque(),
        "times": [c.get("time") for c in candles],
    }

    closes = []
    highs = []
    lows = []
    quote_vols = []

    trades = []
    equity_curve = []
    pending_intent = None
    tuning = resolve_signal_tuning(params)

    position_candles = 0

    for index, candle in enumerate(candles):
        open_price = candle["open"]
        close_price = candle["close"]

        if pending_intent is not None:
            execute_intent(index, pending_intent, open_price, state, params, trades)
            pending_intent = None

        if state["qty"] > 0:
            position_candles += 1

        closes.append(close_price)
        highs.append(candle["high"])
        lows.append(candle["low"])
        quote_vols.append(candle["quote"])

        equity_curve.append(state["cash"] + state["qty"] * close_price)

        if index >= len(candles) - 1:
            continue

        indicators = build_indicators(closes, highs, lows, quote_vols, params, tuning)
        if indicators is None:
            continue

        if state["qty"] > 0:
            sell_intent = choose_sell_intent(index, state, indicators, params, tuning)
            if sell_intent is not None:
                pending_intent = sell_intent
            continue

        regime = evaluate_regime(closes, params)
        if not regime["allow_entries"]:
            continue

        buy_intent = choose_buy_intent(index, state, indicators, params, tuning)
        if buy_intent is not None:
            pending_intent = buy_intent

    final_value = state["cash"] + state["qty"] * candles[-1]["close"]
    metrics = build_metrics(
        params["initial_cash"],
        final_value,
        equity_curve,
        trades,
        unit,
        len(candles),
        position_candles,
    )

    return {
        "metrics": metrics,
        "trades": trades,
    }


def backtest_buy_and_hold(candles, params, unit):
    if not candles:
        return build_metrics(params["initial_cash"], params["initial_cash"], [], [], unit, 0, 0)

    initial_cash = params["initial_cash"]
    cost_rate = params["trade_cost_rate"]

    first_open = candles[0]["open"]
    qty = initial_cash / (first_open * (1.0 + cost_rate)) if first_open > 0 else 0.0

    equity_curve = [qty * c["close"] for c in candles]
    final_value = equity_curve[-1] if equity_curve else initial_cash

    trades = [{
        "side": "BUY",
        "price": first_open,
        "qty": qty,
        "reason": "buy_and_hold",
    }]

    return build_metrics(initial_cash, final_value, equity_curve, trades, unit, len(candles), len(candles))


def score_metrics(metrics):
    if not metrics:
        return -1e18

    score = metrics["roi_pct"] - (0.6 * metrics["max_drawdown_pct"])

    sharpe = metrics.get("sharpe")
    if sharpe is not None:
        score += 0.2 * sharpe

    profit_factor = metrics.get("profit_factor")
    if profit_factor is not None:
        score += min(profit_factor, 4.0) * 0.4

    trades_per_day = metrics.get("trades_per_day", 0.0)
    if trades_per_day < 0.2 or trades_per_day > 5.0:
        score -= 2.0

    return score


def summarize_result(result):
    return result["metrics"]


def make_params(timeframe_unit, profile="BALANCED"):
    return {
        "profile": profile.upper(),
        "initial_cash": 1_000_000.0,
        "max_order_krw": 30_000.0,
        "min_order_krw": 5_000.0,
        "trade_cost_rate": 0.0015,
        "ma_short": 8,
        "ma_long": 34,
        "rsi_period": 14,
        "rsi_buy_threshold": 57.0,
        "rsi_sell_threshold": 47.0,
        "rsi_overbought": 68.0,
        "macd_fast": 12,
        "macd_slow": 26,
        "macd_signal": 9,
        "adx_period": 14,
        "min_adx": 22.0,
        "volume_lookback": 20,
        "min_volume_ratio": 0.9,
        "boll_window": 20,
        "boll_stddev": 2.0,
        "boll_min_bandwidth_pct": 0.8,
        "boll_max_percent_b": 1.05,
        "breakout_lookback": 30,
        "breakout_pct": 0.5,
        "max_extension_pct": 1.0,
        "ma_long_slope_lookback": 5,
        "min_confirmations": 2,
        "trailing_window": 20,
        "stop_loss_pct": 1.6,
        "take_profit_pct": 2.4,
        "trailing_stop_pct": 1.2,
        "partial_take_profit_pct": 35.0,
        "stop_exit_pct": 100.0,
        "trend_exit_pct": 40.0,
        "momentum_exit_pct": 25.0,
        "reentry_cooldown_candles": minutes_to_candles(30, timeframe_unit),
        "stop_loss_cooldown_candles": minutes_to_candles(45, timeframe_unit),
        "partial_take_profit_cooldown_candles": minutes_to_candles(120, timeframe_unit),
        "stop_loss_guard_lookback_candles": minutes_to_candles(180, timeframe_unit),
        "stop_loss_guard_trigger_count": 3,
        "stop_loss_guard_lock_candles": minutes_to_candles(240, timeframe_unit),
        "volatility_window": 30,
        "target_vol_pct": 0.35,
        "regime_filter_enabled": True,
        "regime_ma_short": 40,
        "regime_ma_long": 120,
        "regime_ma_long_slope_lookback": 5,
        "regime_min_ma_long_slope_pct": 0.02,
        "regime_volatility_window": 48,
        "regime_max_volatility_pct": 1.0,
    }


def build_optimization_grid(base_params):
    return {
        "take_profit_pct": [
            round(base_params["take_profit_pct"] * 0.8, 4),
            base_params["take_profit_pct"],
            round(base_params["take_profit_pct"] * 1.2, 4),
        ],
        "stop_loss_pct": [
            round(base_params["stop_loss_pct"] * 0.8, 4),
            base_params["stop_loss_pct"],
            round(base_params["stop_loss_pct"] * 1.2, 4),
        ],
        "trailing_stop_pct": [
            round(base_params["trailing_stop_pct"] * 0.75, 4),
            base_params["trailing_stop_pct"],
            round(base_params["trailing_stop_pct"] * 1.25, 4),
        ],
        "min_adx": [
            max(5.0, base_params["min_adx"] - 4.0),
            base_params["min_adx"],
            base_params["min_adx"] + 4.0,
        ],
        "min_volume_ratio": [
            max(0.1, round(base_params["min_volume_ratio"] - 0.2, 4)),
            base_params["min_volume_ratio"],
            round(base_params["min_volume_ratio"] + 0.2, 4),
        ],
    }


def iter_param_sets(base_params, max_combos):
    grid = build_optimization_grid(base_params)
    keys = list(grid.keys())
    values = [grid[k] for k in keys]

    all_combos = list(itertools.product(*values))
    if max_combos > 0 and len(all_combos) > max_combos:
        # Deterministic down-sampling.
        step = len(all_combos) / max_combos
        sampled = []
        for i in range(max_combos):
            sampled.append(all_combos[min(int(i * step), len(all_combos) - 1)])
        all_combos = sampled

    for combo in all_combos:
        params = copy.deepcopy(base_params)
        for i, value in enumerate(combo):
            params[keys[i]] = value
        yield params


def compact_params(params):
    keys = [
        "profile",
        "ma_short",
        "ma_long",
        "take_profit_pct",
        "stop_loss_pct",
        "trailing_stop_pct",
        "partial_take_profit_pct",
        "trend_exit_pct",
        "momentum_exit_pct",
        "min_adx",
        "min_volume_ratio",
        "breakout_pct",
        "target_vol_pct",
    ]
    return {k: params[k] for k in keys}


def optimize_params(candles, unit, base_params, max_combos):
    best_params = copy.deepcopy(base_params)
    best_result = backtest_strategy(candles, best_params, unit)
    best_score = score_metrics(best_result["metrics"])

    tried = 1
    for params in iter_param_sets(base_params, max_combos):
        result = backtest_strategy(candles, params, unit)
        score = score_metrics(result["metrics"])
        tried += 1
        if score > best_score:
            best_score = score
            best_params = params
            best_result = result

    return best_params, best_result, tried


def split_candles(candles, split_ratio):
    if split_ratio <= 0.0 or split_ratio >= 1.0:
        return candles, []

    split_index = int(len(candles) * split_ratio)
    split_index = max(1, min(len(candles) - 1, split_index))
    return candles[:split_index], candles[split_index:]


def run_one(label, market, unit, args):
    candles = load_candles(
        market=market,
        unit=unit,
        days=args.days,
        cache_dir=args.cache_dir,
        sleep_s=args.sleep,
        refresh_cache=args.refresh_cache,
    )

    if len(candles) < 200:
        raise RuntimeError(f"Not enough candles for {label} ({unit}m): {len(candles)}")

    base_params = make_params(unit, profile=args.profile)
    train_candles, test_candles = split_candles(candles, args.split_ratio)

    if args.optimize:
        optimize_target = train_candles if test_candles else candles
        best_params, train_result, tried = optimize_params(optimize_target, unit, base_params, args.max_combos)
    else:
        best_params = base_params
        optimize_target = train_candles if test_candles else candles
        train_result = backtest_strategy(optimize_target, best_params, unit)
        tried = 1

    full_result = backtest_strategy(candles, best_params, unit)
    full_buy_hold = backtest_buy_and_hold(candles, best_params, unit)

    if test_candles:
        test_result = backtest_strategy(test_candles, best_params, unit)
        test_buy_hold = backtest_buy_and_hold(test_candles, best_params, unit)
    else:
        test_result = None
        test_buy_hold = None

    run = {
        "label": label,
        "market": market,
        "unit": unit,
        "candles": len(candles),
        "train_candles": len(train_candles),
        "test_candles": len(test_candles),
        "optimized": args.optimize,
        "trials": tried,
        "params": compact_params(best_params),
        "strategy": {
            "train": summarize_result(train_result),
            "test": summarize_result(test_result) if test_result else None,
            "full": summarize_result(full_result),
        },
        "buy_and_hold": {
            "test": test_buy_hold,
            "full": full_buy_hold,
        },
    }

    if test_result and test_buy_hold:
        run["alpha_test_pct"] = test_result["metrics"]["roi_pct"] - test_buy_hold["roi_pct"]
    else:
        run["alpha_test_pct"] = run["strategy"]["full"]["roi_pct"] - full_buy_hold["roi_pct"]

    if args.show_trades:
        run["trades_preview"] = full_result["trades"][: min(20, len(full_result["trades"]))]

    return run


def recommendation_score(run):
    test_metrics = run["strategy"].get("test")
    if test_metrics:
        return score_metrics(test_metrics)
    return score_metrics(run["strategy"]["full"])


def main():
    parser = argparse.ArgumentParser(description="Backtest auto-trading strategy with train/test validation")
    parser.add_argument("--market", default="KRW-BTC")
    parser.add_argument("--days", type=int, default=30)
    parser.add_argument("--sleep", type=float, default=0.12)
    parser.add_argument("--cache-dir", default="data/backtest")
    parser.add_argument("--short-unit", type=int, default=3)
    parser.add_argument("--mid-unit", type=int, default=15)
    parser.add_argument("--profile", default="BALANCED", choices=["AGGRESSIVE", "BALANCED", "CONSERVATIVE"])
    parser.add_argument("--split-ratio", type=float, default=0.7)
    parser.add_argument("--optimize", action="store_true")
    parser.add_argument("--max-combos", type=int, default=120)
    parser.add_argument("--refresh-cache", action="store_true")
    parser.add_argument("--show-trades", action="store_true")
    parser.add_argument("--export", default="")
    args = parser.parse_args()

    os.makedirs(args.cache_dir, exist_ok=True)

    runs = []
    for label, unit in [("short", args.short_unit), ("mid", args.mid_unit)]:
        print(f"Running {label} ({unit}m) ...")
        run = run_one(label, args.market, unit, args)
        runs.append(run)

    report = {
        "market": args.market,
        "days": args.days,
        "profile": args.profile,
        "split_ratio": args.split_ratio,
        "optimize": args.optimize,
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "runs": runs,
    }

    winner = max(runs, key=recommendation_score)
    recommendation = {
        "label": winner["label"],
        "unit": winner["unit"],
        "params": winner["params"],
        "score_basis": "test" if winner["strategy"].get("test") else "full",
        "strategy_metrics": winner["strategy"].get("test") or winner["strategy"]["full"],
        "buy_hold_metrics": winner["buy_and_hold"].get("test") or winner["buy_and_hold"]["full"],
        "alpha_pct": winner["alpha_test_pct"],
    }

    output = {
        "report": report,
        "recommended": recommendation,
    }

    print(json.dumps(output, indent=2, ensure_ascii=False))

    if args.export:
        with open(args.export, "w", encoding="utf-8") as file:
            json.dump(output, file, indent=2, ensure_ascii=False)
        print(f"Saved report: {args.export}")


if __name__ == "__main__":
    main()
