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
MAX_TRAILING_ARM_PCT = 1.2


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


def lowest_low(lows, window, exclude_last):
    if window <= 0 or len(lows) < window:
        return None

    end = len(lows) - 1
    if exclude_last:
        end -= 1
    if end < 0:
        return None

    start = max(0, end - window + 1)
    return min(lows[start:end + 1])


def compute_atr_pct(highs, lows, closes, period, current_price):
    if period <= 0 or current_price is None or current_price <= 0:
        return None
    if len(highs) != len(lows) or len(lows) != len(closes) or len(closes) < period + 1:
        return None

    true_ranges = []
    for i in range(len(closes) - period, len(closes)):
        prev_close = closes[i - 1]
        high = highs[i]
        low = lows[i]
        if prev_close <= 0:
            continue
        true_ranges.append(max(high - low, abs(high - prev_close), abs(low - prev_close)))

    atr = average(true_ranges)
    if atr is None or atr <= 0:
        return None
    return (atr / current_price) * 100.0


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


def compute_relative_momentum_pct(closes, short_lookback, long_lookback):
    if short_lookback <= 0 or long_lookback <= 0:
        return None
    needed = max(short_lookback, long_lookback)
    if len(closes) <= needed:
        return None

    current = closes[-1]
    short_prev = closes[-1 - short_lookback]
    long_prev = closes[-1 - long_lookback]
    if current <= 0 or short_prev <= 0 or long_prev <= 0:
        return None

    short_return = ((current - short_prev) / short_prev) * 100.0
    long_return = ((current - long_prev) / long_prev) * 100.0
    return short_return - long_return


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


def resolve_trailing_arm_pct(params):
    trailing_stop_pct = max(0.0, to_float(params.get("trailing_stop_pct"), 0.0))
    take_profit_pct = to_float(params.get("take_profit_pct"), 0.0)
    capped_take_profit = min(take_profit_pct, MAX_TRAILING_ARM_PCT) if take_profit_pct > 0 else MAX_TRAILING_ARM_PCT
    return max(trailing_stop_pct, capped_take_profit)


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
        # Parity with AutoTradeService.resolveSignalTuning: 0 disables the overextension filter, and the
        # 0.2 floor previously made it impossible to switch off.
        "max_extension_pct": 0.0 if max_extension_pct <= 0 else clamp(max_extension_pct, 0.2, 5.0),
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

    breakdown_level = None
    if params.get("breakdown_lookback", 0) > 1:
        breakdown_level = lowest_low(lows, params["breakdown_lookback"], True)

    trailing_window_high = None
    if params["trailing_window"] > 1 and len(highs) >= params["trailing_window"]:
        trailing_window_high = max(highs[-params["trailing_window"]:])

    atr_pct = compute_atr_pct(
        highs,
        lows,
        closes,
        int(to_float(params.get("atr_period"), 20)),
        current_price,
    )

    volatility_pct = None
    if params["target_vol_pct"] > 0 and params["volatility_window"] > 1:
        volatility_pct = compute_volatility_pct(closes[-(params["volatility_window"] + 1):], params["volatility_window"])

    momentum_score_pct = compute_relative_momentum_pct(
        closes,
        int(to_float(params.get("relative_momentum_short_lookback"), 24)),
        int(to_float(params.get("relative_momentum_long_lookback"), 96)),
    )

    return {
        "current_price": current_price,
        "current_high": highs[-1],
        "ma_short": ma_short,
        "ma_long": ma_long,
        "rsi": rsi,
        "macd_hist": macd_hist,
        "adx": adx,
        "volume_ratio": volume_ratio,
        "bollinger": bollinger,
        "breakout_level": breakout_level,
        "breakdown_level": breakdown_level,
        "trailing_window_high": trailing_window_high,
        "ma_long_slope": ma_long_slope,
        "atr_pct": atr_pct,
        "volatility_pct": volatility_pct,
        "momentum_score_pct": momentum_score_pct,
    }


def regime_result(
    allow_entries,
    reason,
    current_price=None,
    ma_short=None,
    ma_long=None,
    slope_pct=None,
    volatility_pct=None,
):
    return {
        "allow_entries": bool(allow_entries),
        "reason": reason,
        "price": current_price,
        "ma_short": ma_short,
        "ma_long": ma_long,
        "ma_long_slope_pct": slope_pct,
        "volatility_pct": volatility_pct,
    }


def evaluate_regime(closes, params):
    if not params["regime_filter_enabled"]:
        return regime_result(True, "regime_disabled")

    if len(closes) < regime_required_history(params):
        return regime_result(False, "regime_insufficient_data")

    current_price = closes[-1]
    ma_short = sma(closes, params["regime_ma_short"])
    ma_long = sma(closes, params["regime_ma_long"])
    if ma_short is None or ma_long is None or ma_long <= 0:
        return regime_result(False, "regime_invalid_trend", current_price, ma_short, ma_long)

    slope_pct = None
    if params["regime_ma_long_slope_lookback"] > 0:
        previous = sma_with_offset(closes, params["regime_ma_long"], params["regime_ma_long_slope_lookback"])
        if previous is not None and previous > 0:
            slope_pct = ((ma_long - previous) / previous) * 100.0
        elif params["regime_min_ma_long_slope_pct"] > 0:
            return regime_result(False, "regime_missing_slope", current_price, ma_short, ma_long)

    volatility_pct = None
    if params["regime_volatility_window"] > 1:
        volatility_pct = compute_volatility_pct(closes, params["regime_volatility_window"])

    if current_price <= ma_long or ma_short <= ma_long:
        return regime_result(False, "regime_trend_off", current_price, ma_short, ma_long, slope_pct, volatility_pct)

    if (
        params["regime_min_ma_long_slope_pct"] > 0
        and slope_pct is not None
        and slope_pct < params["regime_min_ma_long_slope_pct"]
    ):
        return regime_result(False, "regime_slope_off", current_price, ma_short, ma_long, slope_pct, volatility_pct)

    if (
        params["regime_max_volatility_pct"] > 0
        and volatility_pct is not None
        and volatility_pct > params["regime_max_volatility_pct"]
    ):
        return regime_result(False, "regime_high_vol", current_price, ma_short, ma_long, slope_pct, volatility_pct)

    return regime_result(True, "regime_on", current_price, ma_short, ma_long, slope_pct, volatility_pct)


def evaluate_htf_confirmation(closes, params, unit):
    if not params.get("htf_confirm_enabled", True):
        return {"allow_entries": True, "reason": "htf_disabled"}

    htf_unit = int(to_float(params.get("htf_confirm_unit"), 60))
    htf_short = int(to_float(params.get("htf_confirm_ma_short"), 20))
    htf_long = int(to_float(params.get("htf_confirm_ma_long"), 50))
    htf_slope_lookback = int(to_float(params.get("htf_confirm_slope_lookback"), 3))
    htf_min_slope_pct = to_float(params.get("htf_confirm_min_ma_long_slope_pct"), 0.0)

    if htf_unit <= 0 or htf_short <= 1 or htf_long <= htf_short:
        return {"allow_entries": True, "reason": "htf_invalid_config"}

    # Build higher timeframe close series by sampling the closing candle of each HTF block.
    if htf_unit > unit and htf_unit % unit == 0:
        factor = htf_unit // unit
        usable = (len(closes) // factor) * factor
        if usable <= 0:
            return {"allow_entries": True, "reason": "htf_insufficient_data"}
        htf_closes = [closes[i + factor - 1] for i in range(0, usable, factor)]
    else:
        htf_closes = list(closes)

    required = htf_long + max(0, htf_slope_lookback)
    if len(htf_closes) < required:
        return {"allow_entries": True, "reason": "htf_insufficient_data"}

    current_price = htf_closes[-1]
    ma_short = sma(htf_closes, htf_short)
    ma_long = sma(htf_closes, htf_long)
    if ma_short is None or ma_long is None or ma_long <= 0:
        return {"allow_entries": True, "reason": "htf_invalid_trend"}

    slope_pct = None
    if htf_slope_lookback > 0:
        previous = sma_with_offset(htf_closes, htf_long, htf_slope_lookback)
        if previous is not None and previous > 0:
            slope_pct = ((ma_long - previous) / previous) * 100.0

    if current_price <= ma_long or ma_short <= ma_long:
        return {"allow_entries": False, "reason": "htf_trend_off"}
    if htf_min_slope_pct > 0 and slope_pct is not None and slope_pct < htf_min_slope_pct:
        return {"allow_entries": False, "reason": "htf_slope_off"}

    return {"allow_entries": True, "reason": "htf_on"}


def resolve_regime_adjustment(params, tuning, regime):
    if not params.get("regime_switch_enabled", True) or not regime.get("allow_entries", False):
        return {
            "params": params,
            "tuning": tuning,
            "size_multiplier": 1.0,
            "mode": "regime_base",
        }

    slope = regime.get("ma_long_slope_pct")
    volatility = regime.get("volatility_pct")
    slope_threshold = to_float(params.get("regime_switch_risk_on_slope_pct"), 0.12)
    max_vol_threshold = to_float(params.get("regime_switch_risk_on_max_volatility_pct"), 0.8)

    strong_slope = slope is not None and slope >= slope_threshold
    safe_volatility = volatility is None or volatility <= max_vol_threshold
    risk_on = strong_slope and safe_volatility

    if risk_on:
        tp_mul = to_float(params.get("regime_switch_risk_on_take_profit_multiplier"), 1.1)
        sl_mul = to_float(params.get("regime_switch_risk_on_stop_loss_multiplier"), 1.05)
        trailing_mul = to_float(params.get("regime_switch_risk_on_trailing_stop_multiplier"), 1.1)
        size_mul = to_float(params.get("regime_switch_risk_on_size_multiplier"), 1.15)
        rsi_buy_adjust = to_float(params.get("regime_switch_risk_on_rsi_buy_adjust"), -1.0)
        mode = "regime_risk_on"
    else:
        tp_mul = to_float(params.get("regime_switch_caution_take_profit_multiplier"), 0.95)
        sl_mul = to_float(params.get("regime_switch_caution_stop_loss_multiplier"), 0.9)
        trailing_mul = to_float(params.get("regime_switch_caution_trailing_stop_multiplier"), 0.9)
        size_mul = to_float(params.get("regime_switch_caution_size_multiplier"), 0.8)
        rsi_buy_adjust = to_float(params.get("regime_switch_caution_rsi_buy_adjust"), 1.5)
        mode = "regime_caution"

    adjusted_params = copy.deepcopy(params)
    if adjusted_params.get("take_profit_pct", 0.0) > 0:
        adjusted_params["take_profit_pct"] *= tp_mul
    if adjusted_params.get("stop_loss_pct", 0.0) > 0:
        adjusted_params["stop_loss_pct"] *= sl_mul
    if adjusted_params.get("trailing_stop_pct", 0.0) > 0:
        adjusted_params["trailing_stop_pct"] *= trailing_mul

    adjusted_tuning = copy.deepcopy(tuning)
    adjusted_tuning["rsi_buy"] = clamp(adjusted_tuning["rsi_buy"] + rsi_buy_adjust, 40.0, 80.0)

    return {
        "params": adjusted_params,
        "tuning": adjusted_tuning,
        "size_multiplier": clamp(size_mul, 0.2, 2.0),
        "mode": mode,
    }

def update_daily_drawdown_state(index, candle, state):
    current_equity = state["cash"] + (state["qty"] * candle["close"])
    timestamp = parse_time_utc(candle.get("time"))
    if timestamp is None:
        return
    current_date = timestamp.date().isoformat()
    baseline_date = state.get("daily_baseline_date")
    baseline_equity = state.get("daily_baseline_equity")
    if baseline_date != current_date or baseline_equity is None or baseline_equity <= 0:
        state["daily_baseline_date"] = current_date
        state["daily_baseline_equity"] = max(current_equity, 0.0)
        state["daily_drawdown_pct"] = 0.0
        return
    drawdown = 0.0
    if baseline_equity > 0:
        drawdown = max(0.0, ((baseline_equity - current_equity) / baseline_equity) * 100.0)
    state["daily_drawdown_pct"] = drawdown


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


def apply_dynamic_position_sizing(order_funds, state, indicators, params, regime_size_multiplier=1.0):
    if order_funds <= 0:
        return 0.0

    price = indicators.get("current_price") or 0.0
    total_asset = state["cash"] + (state["qty"] * price)
    risk_per_trade_pct = max(0.0, to_float(params.get("risk_per_trade_pct"), 0.0))
    if total_asset > 0 and risk_per_trade_pct > 0:
        risk_budget = total_asset * (risk_per_trade_pct / 100.0)
        if risk_budget > 0 and order_funds > risk_budget:
            risk_scale = max(0.5, risk_budget / order_funds)
            order_funds *= risk_scale

        if params.get("atr_risk_sizing_enabled", True):
            atr_stop_pct = resolve_atr_backed_pct(
                state,
                indicators,
                params,
                "atr_stop_loss_multiplier",
                "stop_loss_pct",
                min_pct=0.2,
                max_pct=25.0,
            )
            if atr_stop_pct > 0:
                stop_loss_fraction = atr_stop_pct / 100.0
                if stop_loss_fraction > 0:
                    order_funds = min(order_funds, risk_budget / stop_loss_fraction)

    if params["target_vol_pct"] > 0 and indicators["volatility_pct"] is not None and indicators["volatility_pct"] > 0:
        scale = min(1.0, params["target_vol_pct"] / indicators["volatility_pct"])
        order_funds *= scale

    if params.get("dynamic_risk_enabled", True):
        limit_pct = max(0.0, to_float(params.get("dynamic_drawdown_limit_pct"), 1.2))
        drawdown_pct = max(0.0, to_float(state.get("daily_drawdown_pct"), 0.0))
        if limit_pct > 0 and drawdown_pct > 0:
            ratio = clamp(drawdown_pct / limit_pct, 0.0, 2.0)
            scale = max(to_float(params.get("dynamic_risk_min_scale"), 0.35), 1.0 - (ratio * 0.5))
            order_funds *= scale

    order_funds *= clamp(to_float(regime_size_multiplier, 1.0), 0.2, 2.0)
    return max(0.0, order_funds)


def append_regime_mode(reason, regime_mode):
    if not reason or regime_mode == "regime_base":
        return reason
    return f"{reason}:{regime_mode}"


def format_score(score):
    if score is None:
        return "na"
    return f"{score:.1f}"


def build_entry_reason(rsi_ok, macd_ok, breakout_ok):
    parts = []
    if rsi_ok:
        parts.append("rsi")
    if macd_ok:
        parts.append("macd")
    if breakout_ok:
        parts.append("breakout")
    if not parts:
        return "trend_entry"
    return "trend_entry:" + "+".join(parts)


def buy_signal_skip(reason):
    return {"kind": "SKIP", "reason": reason}


def buy_signal_proceed(reason, entry_score=None):
    decision = {"kind": "BUY", "reason": reason}
    if entry_score is not None:
        decision["entry_score"] = entry_score
    return decision


def buy_signal_shadow(reason):
    return {"kind": "SHADOW_BUY", "reason": reason}


def sell_signal_none():
    return {"kind": "NONE"}


def sell_signal_pct(pct, reason, allow_full_fallback):
    return {
        "kind": "SELL_PCT",
        "pct": pct,
        "reason": reason,
        "allow_full_fallback": allow_full_fallback,
    }


class TradeSignalModel:
    def evaluate_buy(self, indicators, tuning, params):
        raise NotImplementedError


class UnifiedTrendSignalModel(TradeSignalModel):
    def evaluate_buy(self, indicators, tuning, params):
        if indicators is None or tuning is None:
            return buy_signal_skip("insufficient candles")

        price = indicators.get("current_price")
        ma_short = indicators.get("ma_short")
        ma_long = indicators.get("ma_long")
        if price is None or ma_short is None or ma_long is None:
            return buy_signal_skip("insufficient candles")

        if ma_short <= ma_long or price <= ma_long:
            return buy_signal_skip("no trend")

        min_slope = tuning.get("min_ma_long_slope_pct", 0.0)
        ma_long_slope = indicators.get("ma_long_slope")
        if min_slope > 0 and ma_long_slope is None:
            return buy_signal_skip("no trend slope")
        if ma_long_slope is not None and ma_long_slope < min_slope:
            return buy_signal_skip("trend weakening")

        max_extension_pct = tuning.get("max_extension_pct", 0.0)
        if max_extension_pct > 0:
            max_entry = ma_long * percent_factor(max_extension_pct)
            if price > max_entry:
                return buy_signal_skip("overextended")

        min_adx = tuning.get("min_adx", 0.0)
        adx = indicators.get("adx")
        if min_adx > 0:
            if adx is None:
                return buy_signal_skip("no adx")
            if adx < min_adx:
                return buy_signal_skip("weak_trend")

        min_volume_ratio = tuning.get("min_volume_ratio", 0.0)
        volume_ratio = indicators.get("volume_ratio")
        if min_volume_ratio > 0:
            if volume_ratio is None:
                return buy_signal_skip("no volume")
            if volume_ratio < min_volume_ratio:
                return buy_signal_skip("low_volume")

        breakout_level = indicators.get("breakout_level")
        if breakout_level is None or price <= breakout_level:
            return buy_signal_skip("no breakout")

        return buy_signal_proceed("trend_breakout")


class VolatilityContractionBreakoutModel(TradeSignalModel):
    """Parity with VolatilityContractionBreakoutModel.java.

    Trend gate + Bollinger contraction + break + volume, with NO overextension cap. The cap and the
    breakout requirement contradict each other, which is what made the original model take only the
    weakest breaks.
    """

    def evaluate_buy(self, indicators, tuning, params):
        if indicators is None or tuning is None:
            return buy_signal_skip("insufficient candles")

        price = indicators.get("current_price")
        ma_short = indicators.get("ma_short")
        ma_long = indicators.get("ma_long")
        if price is None or ma_short is None or ma_long is None:
            return buy_signal_skip("insufficient candles")

        if ma_short <= ma_long or price <= ma_long:
            return buy_signal_skip("no trend")

        min_slope = tuning.get("min_ma_long_slope_pct", 0.0)
        ma_long_slope = indicators.get("ma_long_slope")
        if min_slope > 0:
            if ma_long_slope is None:
                return buy_signal_skip("no trend slope")
            if ma_long_slope < min_slope:
                return buy_signal_skip("trend weakening")

        squeeze_max = to_float(params.get("squeeze_max_bandwidth_pct"), 0.0)
        if squeeze_max > 0:
            bollinger = indicators.get("bollinger") or {}
            bandwidth = bollinger.get("bandwidth_pct")
            if bandwidth is None:
                return buy_signal_skip("no bandwidth")
            if bandwidth > squeeze_max:
                return buy_signal_skip("no_squeeze")

        min_adx = tuning.get("min_adx", 0.0)
        adx = indicators.get("adx")
        if min_adx > 0 and adx is not None and adx < min_adx:
            return buy_signal_skip("weak_trend")

        min_volume_ratio = tuning.get("min_volume_ratio", 0.0)
        volume_ratio = indicators.get("volume_ratio")
        if min_volume_ratio > 0:
            if volume_ratio is None:
                return buy_signal_skip("no volume")
            if volume_ratio < min_volume_ratio:
                return buy_signal_skip("low_volume")

        breakout_level = indicators.get("breakout_level")
        if breakout_level is None or price <= breakout_level:
            return buy_signal_skip("no breakout")

        rsi = indicators.get("rsi")
        rsi_over = tuning.get("rsi_over", 0.0)
        if rsi is not None and rsi_over > 0 and rsi >= rsi_over:
            return buy_signal_skip("overbought")

        return buy_signal_proceed("squeeze_breakout")


UNIFIED_SIGNAL_MODEL = UnifiedTrendSignalModel()
SQUEEZE_SIGNAL_MODEL = VolatilityContractionBreakoutModel()

# Mirrors the registry in AutoTradeService; keys match the signal.model setting.
SIGNAL_MODELS = {
    "trend_breakout": UNIFIED_SIGNAL_MODEL,
    "squeeze_breakout": SQUEEZE_SIGNAL_MODEL,
}


def resolve_signal_model(params):
    name = str((params or {}).get("signal_model") or "trend_breakout")
    return SIGNAL_MODELS.get(name, UNIFIED_SIGNAL_MODEL)


def resolve_entry_atr_pct(state, indicators):
    entry_atr_pct = to_float(state.get("entry_atr_pct"), 0.0)
    if entry_atr_pct > 0:
        return entry_atr_pct
    atr_pct = to_float(indicators.get("atr_pct"), 0.0)
    return atr_pct if atr_pct > 0 else None


def resolve_atr_backed_pct(state, indicators, params, multiplier_key, fallback_key, min_pct=0.2, max_pct=30.0):
    fallback = max(0.0, to_float(params.get(fallback_key), 0.0))
    # Parity with AutoTradeService.resolveAtrBackedPct.
    if not params.get("atr_exit_thresholds_enabled", True):
        return fallback
    atr_pct = resolve_entry_atr_pct(state, indicators)
    multiplier = max(0.0, to_float(params.get(multiplier_key), 0.0))
    if atr_pct is None or multiplier <= 0:
        return fallback
    return clamp(atr_pct * multiplier, min_pct, max_pct)


def choose_sell_intent(index, state, indicators, params, tuning):
    qty = state["qty"]
    if qty <= 0:
        return None

    avg_buy = state["avg_buy"]
    current_price = indicators["current_price"]
    if avg_buy <= 0 or current_price <= 0:
        return None

    trailing_high = state["trailing_high"]
    candidates = [avg_buy, current_price, indicators.get("current_high") or 0.0]
    for candidate in candidates:
        if candidate and candidate > 0:
            trailing_high = max(trailing_high or candidate, candidate)
    state["trailing_high"] = trailing_high

    stop_loss_pct = resolve_atr_backed_pct(
        state,
        indicators,
        params,
        "atr_stop_loss_multiplier",
        "stop_loss_pct",
        min_pct=0.2,
        max_pct=25.0,
    )
    trailing_stop_pct = resolve_atr_backed_pct(
        state,
        indicators,
        params,
        "atr_trailing_stop_multiplier",
        "trailing_stop_pct",
        min_pct=0.2,
        max_pct=30.0,
    )
    trailing_arm_pct = resolve_trailing_arm_pct(params)
    atr_pct = resolve_entry_atr_pct(state, indicators)
    atr_arm_multiplier = max(0.0, to_float(params.get("atr_trailing_arm_multiplier"), 0.0))
    if atr_pct is not None and atr_arm_multiplier > 0:
        trailing_arm_pct = clamp(atr_pct * atr_arm_multiplier, 0.2, 20.0)
    # Parity with AutoTradeService.resolveConfiguredTrailingArmPct: arming must be able to lock a gain.
    # If arm < trail the stop sits below entry the instant it arms and no winner can ever be banked.
    if trailing_stop_pct > 0:
        round_trip_cost_pct = to_float(params.get("trade_cost_rate"), 0.0) * 2.0 * 100.0
        trailing_arm_pct = max(trailing_arm_pct, trailing_stop_pct + round_trip_cost_pct)

    stop_loss_threshold = avg_buy * percent_factor(-stop_loss_pct)

    trailing_stop_threshold = None
    trailing_arm_threshold = avg_buy * percent_factor(trailing_arm_pct)
    if (
        trailing_stop_pct > 0
        and trailing_high is not None
        and trailing_high > 0
        and trailing_high >= trailing_arm_threshold
    ):
        trailing_stop_threshold = trailing_high * percent_factor(-trailing_stop_pct)

    # Protective exits always liquidate; stop_exit_pct is a position fraction and 0 must not disarm
    # the stop. Mirrors protectiveExitPct in AutoTradeService.handleSell.
    stop_exit_pct = params["stop_exit_pct"] if params["stop_exit_pct"] > 0 else 100.0

    if current_price <= stop_loss_threshold:
        return {
            "type": "SELL_PCT",
            "pct": stop_exit_pct,
            "reason": "stop_loss",
            "allow_full_fallback": True,
        }

    if trailing_stop_threshold is not None and current_price <= trailing_stop_threshold:
        return {
            "type": "SELL_PCT",
            "pct": stop_exit_pct,
            "reason": "trailing_stop",
            "allow_full_fallback": True,
        }

    take_profit_pct = to_float(params.get("take_profit_pct"), 0.0)
    partial_take_profit_pct = to_float(params.get("partial_take_profit_pct"), 0.0)
    if (
        take_profit_pct > 0
        and 0 < partial_take_profit_pct < 100
        and current_price >= avg_buy * percent_factor(take_profit_pct)
        and can_take_partial_profit(index, state, params)
    ):
        return {
            "type": "SELL_PCT",
            "pct": partial_take_profit_pct,
            "reason": "take_profit_partial",
            "allow_full_fallback": False,
        }

    breakdown_level = indicators.get("breakdown_level")
    if breakdown_level is not None and current_price <= breakdown_level:
        return {
            "type": "SELL_PCT",
            "pct": 100.0,
            "reason": "donchian_exit",
            "allow_full_fallback": False,
        }

    trend_exit_pct = to_float(params.get("trend_exit_pct"), 0.0)
    ma_long = indicators.get("ma_long")
    if trend_exit_pct > 0 and ma_long is not None and current_price < ma_long:
        return {
            "type": "SELL_PCT",
            "pct": trend_exit_pct,
            "reason": "trend_break",
            "allow_full_fallback": False,
        }

    momentum_exit_pct = to_float(params.get("momentum_exit_pct"), 0.0)
    rsi = indicators.get("rsi")
    macd_hist = indicators.get("macd_hist")
    if (
        momentum_exit_pct > 0
        and rsi is not None
        and macd_hist is not None
        and rsi < tuning["rsi_sell"]
        and macd_hist < 0
    ):
        return {
            "type": "SELL_PCT",
            "pct": momentum_exit_pct,
            "reason": "momentum_reversal",
            "allow_full_fallback": False,
        }

    return None


def choose_buy_intent(
    index,
    state,
    indicators,
    params,
    tuning,
    unit,
    closes,
    signal_model,
    regime_size_multiplier=1.0,
    regime_mode="regime_base",
):
    model_decision = signal_model.evaluate_buy(indicators, tuning, params)
    if model_decision["kind"] == "SKIP":
        return None

    if is_stop_loss_guard_active(index, state):
        return None

    last_exit = state.get("last_exit_index")
    if last_exit is not None and (index - last_exit) < params["reentry_cooldown_candles"]:
        return None

    last_stop_loss = state.get("last_stop_loss_index")
    if last_stop_loss is not None and (index - last_stop_loss) < params["stop_loss_cooldown_candles"]:
        return None

    htf = evaluate_htf_confirmation(closes, params, unit)
    if not htf["allow_entries"]:
        return None

    reason = append_regime_mode(model_decision["reason"], regime_mode)
    cash = state["cash"]
    price = indicators["current_price"]
    if cash <= 0 or price <= 0:
        return None

    order_funds = min(cash, params["max_order_krw"])
    order_funds = apply_dynamic_position_sizing(order_funds, state, indicators, params, regime_size_multiplier)
    if order_funds < params["min_order_krw"]:
        return None

    intent = {
        "type": "BUY",
        "funds": order_funds,
        "reason": reason,
    }
    if indicators.get("atr_pct") is not None:
        intent["entry_atr_pct"] = indicators["atr_pct"]
    if "entry_score" in model_decision:
        intent["entry_score"] = model_decision["entry_score"]
    return intent


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
        state["entry_index"] = index
        state["entry_atr_pct"] = intent.get("entry_atr_pct")

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
            state["entry_index"] = None
            state["entry_atr_pct"] = None

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


def build_initial_backtest_state(candles, params):
    return {
        "cash": params["initial_cash"],
        "qty": 0.0,
        "avg_buy": 0.0,
        "trailing_high": None,
        "entry_atr_pct": None,
        "entry_index": None,
        "last_partial_take_index": None,
        "last_stop_loss_index": None,
        "last_exit_index": None,
        "stop_loss_guard_until_index": None,
        "stop_loss_events": deque(),
        "daily_baseline_date": None,
        "daily_baseline_equity": None,
        "daily_drawdown_pct": 0.0,
        "times": [c.get("time") for c in candles],
    }


def backtest_strategy_with_model(candles, params, unit, signal_model):
    if not candles:
        return {
            "metrics": build_metrics(params["initial_cash"], params["initial_cash"], [], [], unit, 0, 0),
            "trades": [],
        }

    state = build_initial_backtest_state(candles, params)
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
        update_daily_drawdown_state(index, candle, state)

        if index >= len(candles) - 1:
            continue

        indicators = build_indicators(closes, highs, lows, quote_vols, params, tuning)
        if indicators is None:
            continue

        regime = evaluate_regime(closes, params)
        regime_adjustment = resolve_regime_adjustment(params, tuning, regime)
        effective_params = regime_adjustment["params"]
        effective_tuning = regime_adjustment["tuning"]
        regime_size_multiplier = regime_adjustment["size_multiplier"]
        regime_mode = regime_adjustment["mode"]

        if state["qty"] > 0:
            sell_intent = choose_sell_intent(
                index,
                state,
                indicators,
                effective_params,
                effective_tuning,
            )
            if sell_intent is not None:
                pending_intent = sell_intent
            continue

        if not regime["allow_entries"]:
            continue

        buy_intent = choose_buy_intent(
            index,
            state,
            indicators,
            effective_params,
            effective_tuning,
            unit,
            closes,
            signal_model,
            regime_size_multiplier=regime_size_multiplier,
            regime_mode=regime_mode,
        )
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


def backtest_strategy(candles, params, unit):
    return backtest_strategy_with_model(candles, params, unit, resolve_signal_model(params))


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


# Below this many closed trades a result is noise, not evidence. The optimizer used to happily rank a
# configuration that made two trades above one that made forty, which is how a 7-day window and an
# hourly re-run turned into a machine for fitting noise.
MIN_TRADES_FOR_SCORING = 20


def score_metrics(metrics):
    if not metrics:
        return -1e18

    sell_trades = metrics.get("sell_trades", 0) or 0
    if sell_trades < MIN_TRADES_FOR_SCORING:
        # Rank by sample size so a longer/denser run still beats an under-sampled one, but never let an
        # under-sampled result outscore a properly-sampled one.
        return -1e9 + sell_trades

    score = metrics["roi_pct"] - (0.6 * metrics["max_drawdown_pct"])

    sharpe = metrics.get("sharpe")
    if sharpe is not None:
        score += 0.2 * sharpe

    profit_factor = metrics.get("profit_factor")
    if profit_factor is not None:
        score += min(profit_factor, 4.0) * 0.4

    # The old band (0.2-5.0/day) assumed an intraday strategy. At the 1h signal timeframe a trend system
    # correctly trades ~10-25 times a year per asset, i.e. ~0.03/day - the previous floor penalised the
    # exact behaviour the strategy is supposed to have.
    trades_per_day = metrics.get("trades_per_day", 0.0)
    if trades_per_day < 0.02 or trades_per_day > 3.0:
        score -= 2.0

    return score


def summarize_result(result):
    return result["metrics"]


def make_params(timeframe_unit, profile="BALANCED"):
    normalized_profile = str(profile or "BALANCED").upper()
    # Higher-timeframe confirmation must be strictly above the signal timeframe.
    htf_confirm_unit = 15 if timeframe_unit <= 3 else (240 if timeframe_unit >= 60 else 60)
    return {
        "profile": normalized_profile,
        "initial_cash": 1_000_000.0,
        "max_order_krw": 30_000.0,
        "min_order_krw": 5_000.0,
        "trade_cost_rate": 0.0015,
        "signal_model": "trend_breakout",
        "squeeze_max_bandwidth_pct": 2.5,
        "ma_short": 5,
        "ma_long": 55,
        "rsi_period": 14,
        "rsi_buy_threshold": 53.0,
        "rsi_sell_threshold": 47.0,
        "rsi_overbought": 68.0,
        "macd_fast": 12,
        "macd_slow": 26,
        "macd_signal": 9,
        "adx_period": 14,
        "min_adx": 20.0,
        "volume_lookback": 20,
        "min_volume_ratio": 1.2,
        "boll_window": 20,
        "boll_stddev": 2.0,
        "breakout_lookback": 20,
        "breakdown_lookback": 10,
        "breakout_pct": 0.05,
        "max_extension_pct": 0.0,
        "ma_long_slope_lookback": 5,
        "min_confirmations": 2,
        "trailing_window": 20,
        "atr_period": 20,
        "atr_stop_loss_multiplier": 2.6,
        "atr_trailing_stop_multiplier": 3.0,
        "atr_trailing_arm_multiplier": 3.5,
        "atr_risk_sizing_enabled": True,
        "atr_exit_thresholds_enabled": True,
        "stop_loss_pct": 1.024,
        "take_profit_pct": 1.44,
        "trailing_stop_pct": 0.675,
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
        "relative_momentum_short_lookback": 24,
        "relative_momentum_long_lookback": 96,
        "risk_per_trade_pct": 0.7,
        "dynamic_risk_enabled": True,
        "dynamic_drawdown_limit_pct": 1.2,
        "dynamic_risk_min_scale": 0.35,
        "regime_filter_enabled": True,
        "regime_ma_short": 30,
        "regime_ma_long": 90,
        "regime_ma_long_slope_lookback": 5,
        "regime_min_ma_long_slope_pct": 0.0,
        "regime_volatility_window": 48,
        "regime_max_volatility_pct": 2.2,
        "regime_switch_enabled": True,
        "regime_switch_risk_on_slope_pct": 0.10,
        "regime_switch_risk_on_max_volatility_pct": 0.8,
        "regime_switch_risk_on_size_multiplier": 1.20,
        "regime_switch_risk_on_take_profit_multiplier": 1.1,
        "regime_switch_risk_on_stop_loss_multiplier": 1.05,
        "regime_switch_risk_on_trailing_stop_multiplier": 1.0,
        "regime_switch_risk_on_rsi_buy_adjust": -1.0,
        "regime_switch_caution_size_multiplier": 0.8,
        "regime_switch_caution_take_profit_multiplier": 1.0,
        "regime_switch_caution_stop_loss_multiplier": 0.9,
        "regime_switch_caution_trailing_stop_multiplier": 1.0,
        "regime_switch_caution_rsi_buy_adjust": 1.5,
        "htf_confirm_enabled": True,
        "htf_confirm_unit": htf_confirm_unit,
        "htf_confirm_ma_short": 20,
        "htf_confirm_ma_long": 50,
        "htf_confirm_slope_lookback": 3,
        "htf_confirm_min_ma_long_slope_pct": 0.0,
    }


def build_optimization_grid(base_params):
    grid = {
        "take_profit_pct": [
            round(base_params["take_profit_pct"] * 0.8, 4),
            base_params["take_profit_pct"],
            round(base_params["take_profit_pct"] * 1.2, 4),
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

    # stop_loss_pct and trailing_stop_pct only reach the engine when ATR-derived thresholds are off;
    # otherwise ATR x multiplier replaces them and every combination along these axes is an identical
    # backtest. Including them regardless meant 2 of 5 dimensions were no-ops, so most of the sampled
    # grid re-ran the same configuration and the effective search was 9x smaller than it looked.
    if not base_params.get("atr_exit_thresholds_enabled", True):
        grid["stop_loss_pct"] = [
            round(base_params["stop_loss_pct"] * 0.8, 4),
            base_params["stop_loss_pct"],
            round(base_params["stop_loss_pct"] * 1.2, 4),
        ]
        grid["trailing_stop_pct"] = [
            round(base_params["trailing_stop_pct"] * 0.75, 4),
            base_params["trailing_stop_pct"],
            round(base_params["trailing_stop_pct"] * 1.25, 4),
        ]
    else:
        grid["atr_stop_loss_multiplier"] = [
            round(base_params["atr_stop_loss_multiplier"] * 0.8, 4),
            base_params["atr_stop_loss_multiplier"],
            round(base_params["atr_stop_loss_multiplier"] * 1.2, 4),
        ]
        grid["atr_trailing_stop_multiplier"] = [
            round(base_params["atr_trailing_stop_multiplier"] * 0.8, 4),
            base_params["atr_trailing_stop_multiplier"],
            round(base_params["atr_trailing_stop_multiplier"] * 1.2, 4),
        ]

    return grid


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
        "breakdown_lookback",
        "atr_period",
        "atr_stop_loss_multiplier",
        "atr_trailing_stop_multiplier",
        "atr_trailing_arm_multiplier",
        "target_vol_pct",
        "htf_confirm_unit",
    ]
    compact = {k: params[k] for k in keys}
    compact["trailing_arm_pct"] = round(resolve_trailing_arm_pct(params), 4)
    return compact


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
    parser.add_argument("--days", type=int, default=7)
    parser.add_argument("--sleep", type=float, default=0.12)
    parser.add_argument("--cache-dir", default="data/backtest")
    parser.add_argument("--short-unit", type=int, default=3)
    parser.add_argument("--mid-unit", type=int, default=15)
    parser.add_argument("--profile", default="BALANCED", choices=["AGGRESSIVE", "BALANCED", "CONSERVATIVE"])
    parser.add_argument("--split-ratio", type=float, default=0.5)
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
