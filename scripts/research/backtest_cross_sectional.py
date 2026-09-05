#!/usr/bin/env python3
"""Backtest cross-sectional momentum over the Upbit KRW universe.

The engine's UniverseSelectionService uses this ranking as a *screen* that feeds the per-market trend
model. Before that is worth enabling, the underlying signal has to be shown to carry any edge on its own,
which nothing in this repo could measure. This evaluates the pure form: rank the universe, hold the top
K, rebalance periodically.

Mirrors UniverseSelectionService deliberately — same lookback, skip, top-K, liquidity floor and risk-off
gate — so a result here says something about the code that ships.

Known limitations, stated because they bias the result OPTIMISTIC:
  * Survivorship. Upbit's market list is today's; coins delisted during the window are invisible, and
    those are disproportionately the ones that collapsed. Real returns would be worse.
  * Daily closes only. Rebalances execute at the close, ignoring intraday slippage beyond the cost rate.
  * No borrow, no shorting — correct for a KRW spot account, but it means the strategy can only express
    "hold winners or hold cash".
"""
import argparse
import json
import math
import os
import sys
import time
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone

UPBIT_MARKETS_URL = "https://api.upbit.com/v1/market/all"
UPBIT_DAY_CANDLES_URL = "https://api.upbit.com/v1/candles/days"


def http_json(url, params=None):
    if params:
        url = url + "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": "btc-auto-trader-xs"})
    with urllib.request.urlopen(req, timeout=15) as resp:
        return json.loads(resp.read().decode("utf-8"))


def fetch_krw_markets():
    rows = http_json(UPBIT_MARKETS_URL, {"isDetails": "true"})
    out = []
    for row in rows:
        market = row.get("market", "")
        if not market.startswith("KRW-"):
            continue
        # Upbit's investment-warning flag marks exactly the names a momentum screen would rank highly
        # right before they are delisted.
        if (row.get("market_warning") or "NONE") != "NONE":
            continue
        out.append(market)
    return sorted(out)


def fetch_day_candles(market, days, cache_dir, sleep_s):
    os.makedirs(cache_dir, exist_ok=True)
    path = os.path.join(cache_dir, f"{market}_day_{days}.json")
    if os.path.exists(path):
        with open(path) as fh:
            return json.load(fh)

    rows = []
    to = None
    while len(rows) < days:
        params = {"market": market, "count": str(min(200, days - len(rows)))}
        if to:
            params["to"] = to
        batch = http_json(UPBIT_DAY_CANDLES_URL, params)
        if not batch:
            break
        rows.extend(batch)
        to = batch[-1]["candle_date_time_utc"]
        time.sleep(sleep_s)
        if len(batch) < 200:
            break

    rows = list(reversed(rows))  # oldest first
    with open(path, "w") as fh:
        json.dump(rows, fh)
    return rows


def build_series(markets, days, cache_dir, sleep_s, progress_every=20):
    """market -> {date: (close, quote_volume)}"""
    series = {}
    for i, market in enumerate(markets, 1):
        try:
            rows = fetch_day_candles(market, days, cache_dir, sleep_s)
        except Exception as exc:  # noqa: BLE001
            print(f"  !! {market}: {exc}", file=sys.stderr)
            continue
        by_date = {}
        for row in rows:
            date = row["candle_date_time_utc"][:10]
            close = float(row["trade_price"])
            value = float(row.get("candle_acc_trade_price") or 0.0)
            if close > 0:
                by_date[date] = (close, value)
        if by_date:
            series[market] = by_date
        if i % progress_every == 0:
            print(f"  fetched {i}/{len(markets)}", flush=True)
    return series


def all_dates(series):
    dates = set()
    for by_date in series.values():
        dates.update(by_date.keys())
    return sorted(dates)


def sma(values, window):
    if len(values) < window:
        return None
    return sum(values[-window:]) / window


def run(args):
    print("fetching KRW market list...", flush=True)
    markets = fetch_krw_markets()
    print(f"  {len(markets)} tradeable KRW markets (investment-warning names excluded)", flush=True)

    print(f"fetching {args.days}d daily candles...", flush=True)
    series = build_series(markets, args.days, args.cache_dir, args.sleep)
    print(f"  usable series: {len(series)}", flush=True)

    dates = all_dates(series)
    if len(dates) < args.lookback + args.skip + args.risk_off_ma + 5:
        print("not enough history", file=sys.stderr)
        return 1

    bench = args.risk_off_market
    equity = args.initial_cash
    holdings = {}  # market -> qty
    curve = []
    rebalances = 0
    risk_off_days = 0
    turnover_cost_total = 0.0

    start = args.lookback + args.skip + args.risk_off_ma
    for idx in range(start, len(dates)):
        date = dates[idx]

        # Mark the book to today's close.
        mtm = 0.0
        for market, qty in holdings.items():
            px = series.get(market, {}).get(date)
            if px:
                mtm += qty * px[0]
        equity_today = equity + mtm
        curve.append((date, equity_today))

        if (idx - start) % args.rebalance_days != 0:
            continue

        # --- risk-off gate: reference asset below its long MA means hold cash ---
        bench_closes = [
            series[bench][d][0]
            for d in dates[max(0, idx - args.risk_off_ma): idx + 1]
            if bench in series and d in series[bench]
        ]
        bench_ma = sma(bench_closes, args.risk_off_ma)
        risk_on = bench_ma is not None and bench_closes and bench_closes[-1] > bench_ma
        if not risk_on:
            risk_off_days += 1

        # --- rank ---
        selected = []
        if risk_on:
            ranked = []
            for market, by_date in series.items():
                d_now = dates[idx - args.skip]
                d_past = dates[idx - args.skip - args.lookback]
                if d_now not in by_date or d_past not in by_date or date not in by_date:
                    continue
                # Liquidity measured AT the rebalance date, not today, so this is not lookahead.
                if by_date[date][1] < args.min_daily_value:
                    continue
                past = by_date[d_past][0]
                now = by_date[d_now][0]
                if past <= 0:
                    continue
                momentum = (now - past) / past
                if momentum <= 0:
                    continue  # absolute momentum filter: never hold the "least bad" name
                ranked.append((momentum, market))
            ranked.sort(reverse=True)
            selected = [m for _, m in ranked[: args.top_k]]

        # --- rebalance to equal weight ---
        target_value = equity_today / len(selected) if selected else 0.0
        new_holdings = {}
        traded_value = 0.0

        for market, qty in holdings.items():
            px = series.get(market, {}).get(date)
            if not px:
                continue
            if market not in selected:
                traded_value += qty * px[0]

        for market in selected:
            px = series[market][date][0]
            target_qty = target_value / px
            current_qty = holdings.get(market, 0.0)
            traded_value += abs(target_qty - current_qty) * px
            new_holdings[market] = target_qty

        cost = traded_value * args.cost_rate
        turnover_cost_total += cost

        invested = sum(qty * series[m][date][0] for m, qty in new_holdings.items())
        equity = equity_today - invested - cost
        holdings = new_holdings
        rebalances += 1

    # --- metrics ---
    final = curve[-1][1] if curve else args.initial_cash
    roi = (final / args.initial_cash - 1) * 100
    peak, mdd = -1e18, 0.0
    for _, value in curve:
        peak = max(peak, value)
        mdd = max(mdd, (peak - value) / peak * 100 if peak > 0 else 0.0)

    rets = []
    for i in range(1, len(curve)):
        prev, cur = curve[i - 1][1], curve[i][1]
        if prev > 0:
            rets.append(cur / prev - 1)
    mean = sum(rets) / len(rets) if rets else 0.0
    var = sum((r - mean) ** 2 for r in rets) / len(rets) if rets else 0.0
    sharpe = (mean / math.sqrt(var) * math.sqrt(365)) if var > 0 else 0.0
    years = len(curve) / 365.0
    cagr = ((final / args.initial_cash) ** (1 / years) - 1) * 100 if years > 0 and final > 0 else 0.0

    bh_first = series[bench][dates[start]][0]
    bh_last = series[bench][curve[-1][0]][0]
    bh_roi = (bh_last / bh_first - 1) * 100

    print()
    print("=" * 68)
    print(f"CROSS-SECTIONAL MOMENTUM  top{args.top_k}  lookback {args.lookback}d skip {args.skip}d")
    print(f"rebalance every {args.rebalance_days}d   cost {args.cost_rate * 100:.2f}%/side")
    print(f"window {curve[0][0]} .. {curve[-1][0]}  ({len(curve)} days)")
    print("=" * 68)
    print(f"  ROI              {roi:+.2f}%")
    print(f"  CAGR             {cagr:+.2f}%")
    print(f"  max drawdown     {mdd:.2f}%")
    print(f"  Sharpe           {sharpe:.2f}")
    print(f"  rebalances       {rebalances}  ({risk_off_days} of them risk-off -> cash)")
    print(f"  turnover cost    {turnover_cost_total:,.0f} KRW")
    print(f"  final equity     {final:,.0f} KRW")
    print()
    print(f"  {bench} buy & hold  {bh_roi:+.2f}%")
    print(f"  alpha              {roi - bh_roi:+.2f}%p")
    print()
    print("  NOTE: survivorship-biased upward — delisted coins are absent from Upbit's market list.")
    return 0


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    # Defaults mirror signal.universe.* in application.properties.
    p.add_argument("--days", type=int, default=800)
    p.add_argument("--lookback", type=int, default=30)
    p.add_argument("--skip", type=int, default=7)
    p.add_argument("--top-k", type=int, default=5)
    p.add_argument("--rebalance-days", type=int, default=7)
    p.add_argument("--min-daily-value", type=float, default=5_000_000_000)
    p.add_argument("--risk-off-market", default="KRW-BTC")
    p.add_argument("--risk-off-ma", type=int, default=100)
    p.add_argument("--cost-rate", type=float, default=0.0015)
    p.add_argument("--initial-cash", type=float, default=1_000_000)
    p.add_argument("--cache-dir", default="data/backtest/day")
    p.add_argument("--sleep", type=float, default=0.12)
    sys.exit(run(p.parse_args()))


if __name__ == "__main__":
    main()
