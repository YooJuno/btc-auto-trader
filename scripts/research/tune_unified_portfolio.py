#!/usr/bin/env python3
import argparse
import copy
import json
import random
import sys
from collections import deque

sys.path.insert(0, "scripts/research")

import backtest


def build_position_state(times):
    return {
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
        "times": times,
    }


def load_aligned_candles(markets, unit, days, cache_dir, sleep_s, refresh_cache):
    indexed = {}
    common_times = None
    for market in markets:
        candles = backtest.load_candles(
            market=market,
            unit=unit,
            days=days,
            cache_dir=cache_dir,
            sleep_s=sleep_s,
            refresh_cache=refresh_cache,
        )
        mapping = {candle["time"]: candle for candle in candles}
        indexed[market] = mapping
        times = set(mapping)
        common_times = times if common_times is None else (common_times & times)

    ordered_times = sorted(common_times) if common_times else []
    aligned = {market: [indexed[market][time] for time in ordered_times] for market in markets}
    return ordered_times, aligned


def execute_portfolio_intent(index, market, intent, execution_price, portfolio_cash, position, params, trades):
    if intent is None or execution_price <= 0:
        return portfolio_cash

    cost_rate = params["trade_cost_rate"]

    if intent["type"] == "BUY":
        funds = min(portfolio_cash, intent.get("funds", 0.0))
        if funds < params["min_order_krw"]:
            return portfolio_cash

        qty = funds / (execution_price * (1.0 + cost_rate))
        if qty <= 0:
            return portfolio_cash

        spent = min(qty * execution_price * (1.0 + cost_rate), portfolio_cash)
        prev_qty = position["qty"]
        prev_avg = position["avg_buy"]
        new_qty = prev_qty + qty
        avg_entry_price = execution_price * (1.0 + cost_rate)

        if new_qty > 0:
            position["avg_buy"] = ((prev_avg * prev_qty) + (avg_entry_price * qty)) / new_qty
        position["qty"] = new_qty
        portfolio_cash -= spent
        position["trailing_high"] = max(position.get("trailing_high") or execution_price, execution_price)
        position["entry_index"] = index
        position["entry_atr_pct"] = intent.get("entry_atr_pct")

        trades.append({
            "index": index,
            "time": position["times"][index],
            "market": market,
            "side": "BUY",
            "price": execution_price,
            "qty": qty,
            "notional": spent,
            "reason": intent.get("reason", "entry"),
        })
        return portfolio_cash

    if intent["type"] == "SELL_PCT":
        pct = backtest.clamp(intent.get("pct", 0.0), 0.0, 100.0)
        if pct <= 0:
            return portfolio_cash

        position_qty = position["qty"]
        if position_qty <= 0:
            return portfolio_cash

        sell_qty = position_qty if pct >= 100.0 else position_qty * (pct / 100.0)
        if sell_qty <= 0:
            return portfolio_cash

        proceeds = sell_qty * execution_price * (1.0 - cost_rate)
        if proceeds < params["min_order_krw"]:
            if intent.get("allow_full_fallback", False):
                full_proceeds = position_qty * execution_price * (1.0 - cost_rate)
                if full_proceeds >= params["min_order_krw"]:
                    sell_qty = position_qty
                    proceeds = full_proceeds
                else:
                    return portfolio_cash
            else:
                return portfolio_cash

        cost_basis = sell_qty * position["avg_buy"]
        realized_pnl = proceeds - cost_basis

        portfolio_cash += proceeds
        position["qty"] -= sell_qty
        if position["qty"] <= 1e-12:
            position["qty"] = 0.0
            position["avg_buy"] = 0.0
            position["trailing_high"] = None
            position["entry_index"] = None
            position["entry_atr_pct"] = None

        reason = intent.get("reason", "sell")
        position["last_exit_index"] = index
        if reason == "take_profit_partial":
            position["last_partial_take_index"] = index

        if reason.startswith("stop_loss") or reason.startswith("trailing_stop") or reason.startswith("momentum_reversal"):
            position["last_stop_loss_index"] = index
            backtest.record_protective_exit(index, position, params)

        trades.append({
            "index": index,
            "time": position["times"][index],
            "market": market,
            "side": "SELL",
            "price": execution_price,
            "qty": sell_qty,
            "notional": proceeds,
            "reason": reason,
            "realized_pnl": realized_pnl,
        })

    return portfolio_cash


def portfolio_backtest(markets, aligned_candles, params, unit):
    first_market = markets[0]
    times = [candle["time"] for candle in aligned_candles[first_market]]
    positions = {market: build_position_state(times) for market in markets}
    histories = {
        market: {"closes": [], "highs": [], "lows": [], "quotes": []}
        for market in markets
    }
    pending = {market: None for market in markets}
    trades = []
    equity_curve = []
    cash = params["initial_cash"]
    position_candles = 0
    tuning = backtest.resolve_signal_tuning(params)
    daily_baseline_date = None
    daily_baseline_equity = None
    daily_drawdown_pct = 0.0

    for index in range(len(times)):
        for market in markets:
            intent = pending.get(market)
            if intent is not None and intent["type"] == "SELL_PCT":
                cash = execute_portfolio_intent(
                    index,
                    market,
                    intent,
                    aligned_candles[market][index]["open"],
                    cash,
                    positions[market],
                    params,
                    trades,
                )
                pending[market] = None

        for market in markets:
            intent = pending.get(market)
            if intent is not None and intent["type"] == "BUY":
                cash = execute_portfolio_intent(
                    index,
                    market,
                    intent,
                    aligned_candles[market][index]["open"],
                    cash,
                    positions[market],
                    params,
                    trades,
                )
                pending[market] = None

        if any(position["qty"] > 0 for position in positions.values()):
            position_candles += 1

        for market in markets:
            candle = aligned_candles[market][index]
            histories[market]["closes"].append(candle["close"])
            histories[market]["highs"].append(candle["high"])
            histories[market]["lows"].append(candle["low"])
            histories[market]["quotes"].append(candle["quote"])

        current_equity = cash + sum(
            positions[market]["qty"] * aligned_candles[market][index]["close"]
            for market in markets
        )
        equity_curve.append(current_equity)

        timestamp = backtest.parse_time_utc(times[index])
        current_date = timestamp.date().isoformat() if timestamp is not None else None
        if current_date is not None:
            if daily_baseline_date != current_date or daily_baseline_equity is None or daily_baseline_equity <= 0:
                daily_baseline_date = current_date
                daily_baseline_equity = max(current_equity, 0.0)
                daily_drawdown_pct = 0.0
            else:
                daily_drawdown_pct = max(
                    0.0,
                    ((daily_baseline_equity - current_equity) / daily_baseline_equity) * 100.0,
                )

        if index >= len(times) - 1:
            continue

        for market in markets:
            position = positions[market]
            indicators = backtest.build_indicators(
                histories[market]["closes"],
                histories[market]["highs"],
                histories[market]["lows"],
                histories[market]["quotes"],
                params,
                tuning,
            )
            if indicators is None:
                continue

            regime = backtest.evaluate_regime(histories[market]["closes"], params)
            regime_adjustment = backtest.resolve_regime_adjustment(params, tuning, regime)
            effective_params = regime_adjustment["params"]
            effective_tuning = regime_adjustment["tuning"]
            regime_size_multiplier = regime_adjustment["size_multiplier"]
            regime_mode = regime_adjustment["mode"]

            if position["qty"] > 0:
                sell_intent = backtest.choose_sell_intent(index, position, indicators, effective_params, effective_tuning)
                if sell_intent is not None:
                    pending[market] = sell_intent
                continue

            if not regime["allow_entries"]:
                continue
            if backtest.is_stop_loss_guard_active(index, position):
                continue

            last_exit = position.get("last_exit_index")
            if last_exit is not None and (index - last_exit) < params["reentry_cooldown_candles"]:
                continue

            last_stop_loss = position.get("last_stop_loss_index")
            if last_stop_loss is not None and (index - last_stop_loss) < params["stop_loss_cooldown_candles"]:
                continue

            htf = backtest.evaluate_htf_confirmation(histories[market]["closes"], params, unit)
            if not htf["allow_entries"]:
                continue

            model_decision = backtest.UNIFIED_SIGNAL_MODEL.evaluate_buy(indicators, effective_tuning, effective_params)
            if model_decision["kind"] == "SKIP":
                continue

            free_cash = cash
            price = indicators["current_price"]
            if free_cash <= 0 or price <= 0:
                continue

            order_funds = min(free_cash, params["max_order_krw"])
            other_position_value = sum(
                positions[other_market]["qty"] * aligned_candles[other_market][index]["close"]
                for other_market in markets
                if other_market != market
            )
            sizing_state = {
                "cash": free_cash + other_position_value,
                "qty": position["qty"],
                "daily_drawdown_pct": daily_drawdown_pct,
            }
            order_funds = backtest.apply_dynamic_position_sizing(
                order_funds,
                sizing_state,
                indicators,
                effective_params,
                regime_size_multiplier=regime_size_multiplier,
            )
            if order_funds < params["min_order_krw"]:
                continue

            pending[market] = {
                "type": "BUY",
                "funds": min(order_funds, free_cash),
                "reason": backtest.append_regime_mode(model_decision["reason"], regime_mode),
                "entry_atr_pct": indicators.get("atr_pct"),
            }

    final_value = cash + sum(
        positions[market]["qty"] * aligned_candles[market][-1]["close"]
        for market in markets
    )
    metrics = backtest.build_metrics(
        params["initial_cash"],
        final_value,
        equity_curve,
        trades,
        unit,
        len(times),
        position_candles,
    )
    return {"metrics": metrics, "trades": trades}


def score_metrics(metrics):
    value = metrics["roi_pct"] - (0.75 * metrics["max_drawdown_pct"])

    sharpe = metrics.get("sharpe")
    if sharpe is not None:
        value += 0.2 * sharpe

    profit_factor = metrics.get("profit_factor")
    if profit_factor is not None:
        value += min(profit_factor, 3.0) * 0.35

    # Same sample-size gate as backtest.score_metrics: a configuration fitted to a couple of trades is
    # noise, and a soft -2.0 penalty was not enough to stop one outranking a properly-sampled result.
    sell_trades = metrics.get("sell_trades", 0)
    if sell_trades < backtest.MIN_TRADES_FOR_SCORING:
        return -1e9 + sell_trades

    if metrics["roi_pct"] < 0:
        value += metrics["roi_pct"] * 0.5

    if metrics["max_drawdown_pct"] > 1.0:
        value -= (metrics["max_drawdown_pct"] - 1.0) * 2.0

    return value


def overall_score(train_metrics, test_metrics):
    return (0.35 * score_metrics(train_metrics)) + (0.65 * score_metrics(test_metrics))


def slim_metrics(metrics):
    return {
        "roi_pct": round(metrics["roi_pct"], 4),
        "max_drawdown_pct": round(metrics["max_drawdown_pct"], 4),
        "sell_trades": metrics["sell_trades"],
        "win_rate_pct": None if metrics["win_rate_pct"] is None else round(metrics["win_rate_pct"], 2),
        "profit_factor": None if metrics["profit_factor"] is None else round(metrics["profit_factor"], 4),
        "sharpe": None if metrics["sharpe"] is None else round(metrics["sharpe"], 4),
        "exposure_pct": round(metrics["exposure_pct"], 4),
    }


def build_param_space():
    return {
        "ma_short": [5, 8, 10, 13],
        "ma_long": [21, 34, 55],
        "breakout_lookback": [10, 15, 20, 30, 45],
        "breakdown_lookback": [5, 8, 10, 15],
        "breakout_pct": [0.05, 0.10, 0.20, 0.35, 0.50],
        "min_adx": [8.0, 12.0, 16.0, 20.0],
        "min_volume_ratio": [0.4, 0.6, 0.8, 1.0],
        "max_extension_pct": [1.5, 2.0, 2.5, 3.0, 4.0],
        "atr_stop_loss_multiplier": [1.4, 1.8, 2.2, 2.6],
        "atr_trailing_stop_multiplier": [1.8, 2.2, 2.6, 3.0, 3.4],
        "atr_trailing_arm_multiplier": [0.5, 0.8, 1.0, 1.2, 1.5],
    }


def make_candidate_params(base_params, param_space, seed, samples):
    rng = random.Random(seed)
    seen = set()
    candidates = [copy.deepcopy(base_params)]
    seen.add(tuple(sorted((key, base_params[key]) for key in param_space)))

    while len(candidates) < samples + 1:
        params = copy.deepcopy(base_params)
        for key, values in param_space.items():
            params[key] = rng.choice(values)
        if params["ma_short"] >= params["ma_long"]:
            continue
        signature = tuple(sorted((key, params[key]) for key in param_space))
        if signature in seen:
            continue
        seen.add(signature)
        candidates.append(params)

    return candidates


def main():
    parser = argparse.ArgumentParser(description="Tune unified trend parameters on a shared-cash portfolio.")
    parser.add_argument("--markets", default="KRW-BTC,KRW-ETH,KRW-XRP")
    # Engine signal timeframe. 15m could not clear a ~0.3% round trip against a ~0.3% ATR.
    parser.add_argument("--unit", type=int, default=60)
    parser.add_argument("--days", type=int, default=180)
    parser.add_argument("--split-ratio", type=float, default=0.7)
    parser.add_argument("--profile", default="BALANCED")
    parser.add_argument("--samples", type=int, default=320)
    parser.add_argument("--seed", type=int, default=23)
    parser.add_argument("--sleep", type=float, default=0.12)
    parser.add_argument("--cache-dir", default="data/backtest")
    parser.add_argument("--refresh-cache", action="store_true")
    parser.add_argument("--initial-cash", type=float, default=1_000_000.0)
    parser.add_argument("--max-order-krw", type=float, default=25_000.0)
    parser.add_argument("--min-order-krw", type=float, default=5_000.0)
    parser.add_argument("--top", type=int, default=10)
    parser.add_argument("--full-eval-limit", type=int, default=30)
    parser.add_argument("--progress-every", type=int, default=50)
    parser.add_argument("--export", default="")
    args = parser.parse_args()

    markets = [market.strip() for market in args.markets.split(",") if market.strip()]
    if not markets:
        raise SystemExit("No markets provided.")

    ordered_times, aligned = load_aligned_candles(
        markets=markets,
        unit=args.unit,
        days=args.days,
        cache_dir=args.cache_dir,
        sleep_s=args.sleep,
        refresh_cache=args.refresh_cache,
    )
    if len(ordered_times) < 400:
        raise SystemExit(f"Not enough aligned candles: {len(ordered_times)}")

    split_index = int(len(ordered_times) * args.split_ratio)
    split_index = max(1, min(len(ordered_times) - 1, split_index))
    train_aligned = {market: candles[:split_index] for market, candles in aligned.items()}
    test_aligned = {market: candles[split_index:] for market, candles in aligned.items()}

    base_params = backtest.make_params(args.unit, args.profile)
    base_params["initial_cash"] = args.initial_cash
    base_params["max_order_krw"] = args.max_order_krw
    base_params["min_order_krw"] = args.min_order_krw

    baseline_train = portfolio_backtest(markets, train_aligned, base_params, args.unit)
    baseline_test = portfolio_backtest(markets, test_aligned, base_params, args.unit)
    baseline_full = portfolio_backtest(markets, aligned, base_params, args.unit)

    param_space = build_param_space()
    candidates = make_candidate_params(base_params, param_space, args.seed, args.samples)

    results = []
    total_candidates = len(candidates)
    for index, params in enumerate(candidates, start=1):
        train_result = portfolio_backtest(markets, train_aligned, params, args.unit)
        test_result = portfolio_backtest(markets, test_aligned, params, args.unit)
        results.append({
            "rank_score": overall_score(train_result["metrics"], test_result["metrics"]),
            "params": {key: params[key] for key in param_space},
            "train": train_result["metrics"],
            "test": test_result["metrics"],
        })
        if args.progress_every > 0 and (index % args.progress_every) == 0:
            print(f"Evaluated {index}/{total_candidates} candidates...", file=sys.stderr, flush=True)

    results.sort(key=lambda item: item["rank_score"], reverse=True)
    full_eval_limit = max(args.top, args.full_eval_limit)
    for item in results[:full_eval_limit]:
        params = copy.deepcopy(base_params)
        params.update(item["params"])
        full_result = portfolio_backtest(markets, aligned, params, args.unit)
        item["full"] = full_result["metrics"]

    output = {
        "markets": markets,
        "unit": args.unit,
        "days": args.days,
        "candles": len(ordered_times),
        "train_candles": split_index,
        "test_candles": len(ordered_times) - split_index,
        "baseline": {
            "params": {key: base_params[key] for key in param_space},
            "train": slim_metrics(baseline_train["metrics"]),
            "test": slim_metrics(baseline_test["metrics"]),
            "full": slim_metrics(baseline_full["metrics"]),
            "rank_score": round(overall_score(baseline_train["metrics"], baseline_test["metrics"]), 6),
        },
        "top": [
            {
                "rank_score": round(item["rank_score"], 6),
                "params": item["params"],
                "train": slim_metrics(item["train"]),
                "test": slim_metrics(item["test"]),
                "full": slim_metrics(item["full"]),
            }
            for item in results[: max(1, args.top)]
        ],
    }

    print(json.dumps(output, ensure_ascii=False, indent=2))
    if args.export:
        with open(args.export, "w", encoding="utf-8") as file:
            json.dump(output, file, ensure_ascii=False, indent=2)


if __name__ == "__main__":
    main()
