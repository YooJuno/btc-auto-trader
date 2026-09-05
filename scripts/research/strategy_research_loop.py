#!/usr/bin/env python3
"""Unattended strategy research: search, validate walk-forward, and almost always decline.

Replaces strategy_lab_daemon.py, which optimised a 7-day window every hour and accepted results built
on one trade. This runs on the full available history, scores candidates only on data they were not
fitted to, corrects for how many were tried, and refuses to promote anything that does not clear every
criterion.

The expected outcome of most runs is REJECT. That is the system working. A research loop whose answer
is usually "promote this" is a loop that is fitting noise.

  # one pass, report only
  python3 scripts/research/strategy_research_loop.py --once

  # run forever, one pass a day
  python3 scripts/research/strategy_research_loop.py --interval-hours 24

  # allow it to write a promoted config (still never edits application.properties)
  python3 scripts/research/strategy_research_loop.py --once --auto-promote

Even with --auto-promote it only writes champion.json. Wiring that into the engine is a deliberate,
separate act, because the gap between "passed a backtest" and "should trade my money" is a judgement no
script should make on its own.
"""
import argparse
import copy
import json
import os
import sys
import time
from datetime import datetime, timezone

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import backtest  # noqa: E402
import walk_forward as wf  # noqa: E402


def now_iso():
    return datetime.now(timezone.utc).isoformat()


def slice_metrics(candles, params, unit, lo, hi):
    """Backtest a single window. Returns None when the window cannot support a verdict."""
    window = candles[lo:hi]
    if len(window) < backtest.required_history(params) + 5:
        return None
    try:
        result = backtest.backtest_strategy(window, params, unit)
    except Exception:  # noqa: BLE001
        return None
    return result["metrics"]


def optimise_on_train(candles, base_params, unit, lo, hi, max_combos):
    """Pick the best configuration on the TRAINING window only.

    Returns (params, trials). The trial count is carried forward because the promotion gate has to know
    how wide the search was to judge whether the winner is signal or the arithmetic of searching.
    """
    train = candles[lo:hi]
    best_params = copy.deepcopy(base_params)
    best_score = None
    trials = 0

    for params in backtest.iter_param_sets(base_params, max_combos):
        metrics = slice_metrics(candles, params, unit, lo, hi)
        trials += 1
        if not metrics:
            continue
        score = backtest.score_metrics(metrics)
        if best_score is None or score > best_score:
            best_score, best_params = score, params

    if best_score is None:
        return copy.deepcopy(base_params), max(trials, 1)
    return best_params, max(trials, 1)


def run_walk_forward(candles, base_params, unit, args):
    folds = wf.anchored_folds(len(candles), unit, args.train_days, args.test_days, args.step_days)
    if not folds:
        return None, [], 1

    oos = []
    per_fold = []
    trials = 1
    for i, fold in enumerate(folds, 1):
        tr_lo, tr_hi = fold["train"]
        te_lo, te_hi = fold["test"]

        if args.optimize:
            params, trials = optimise_on_train(candles, base_params, unit, tr_lo, tr_hi, args.max_combos)
        else:
            params = copy.deepcopy(base_params)

        metrics = slice_metrics(candles, params, unit, te_lo, te_hi)
        oos.append(metrics)
        per_fold.append({
            "fold": i,
            "train_candles": tr_hi - tr_lo,
            "test_candles": te_hi - te_lo,
            "metrics": metrics,
            "params": backtest.compact_params(params) if metrics else None,
        })
        print(f"    fold {i:2}/{len(folds)}  OOS "
              f"{(metrics['roi_pct'] if metrics else 0):+7.2f}%  "
              f"trades {(metrics['sell_trades'] if metrics else 0):3}", flush=True)

    return wf.summarise_folds(oos), per_fold, trials


def evaluate_market(market, unit, args):
    print(f"\n=== {market} {unit}m ===", flush=True)
    try:
        candles = backtest.load_candles(market, unit, args.days, args.cache_dir,
                                        sleep_s=args.sleep, refresh_cache=args.refresh_cache)
    except Exception as exc:  # noqa: BLE001
        print(f"  !! candle load failed: {exc}", file=sys.stderr)
        return None

    base = backtest.make_params(unit, args.profile)
    base["signal_model"] = args.signal_model
    print(f"  {len(candles)} candles", flush=True)

    # The incumbent is the shipped configuration, evaluated over the same folds. A challenger has to
    # beat what is already running, not merely be positive.
    print("  baseline (current defaults, no fitting):", flush=True)
    baseline_summary, _, _ = run_walk_forward(candles, base, unit, argparse.Namespace(**{**vars(args), "optimize": False}))

    print("  challenger (optimised per fold on training data only):", flush=True)
    challenger_summary, per_fold, trials = run_walk_forward(candles, base, unit, args)

    incumbent_return = baseline_summary["median_return_pct"] if baseline_summary else None
    gate = wf.evaluate_gate(challenger_summary, trials, incumbent_return)

    return {
        "market": market,
        "unit": unit,
        "signal_model": args.signal_model,
        "profile": args.profile,
        "candles": len(candles),
        "trials_per_fold": trials,
        "baseline": baseline_summary,
        "challenger": challenger_summary,
        "gate": gate,
        "folds": per_fold,
    }


def report(result):
    if not result:
        return
    g = result["gate"]
    b, c = result["baseline"], result["challenger"]
    print(f"\n  --- {result['market']} {result['unit']}m / {result['signal_model']} ---")
    if b:
        print(f"  baseline    median {b['median_return_pct']:+6.2f}%  "
              f"positive {b['positive_fold_ratio']:.0%}  trades {b['total_trades']}")
    if c:
        print(f"  challenger  median {c['median_return_pct']:+6.2f}%  "
              f"positive {c['positive_fold_ratio']:.0%}  trades {c['total_trades']}  "
              f"foldSharpe {c['fold_sharpe']:.2f}")
    print(f"  VERDICT: {g['verdict']}")
    for check in g["checks"]:
        print(f"    [{'PASS' if check['passed'] else 'FAIL'}] {check['detail']}")


def one_pass(args):
    stamp = now_iso()
    os.makedirs(args.output_dir, exist_ok=True)

    results = []
    for market in [m.strip() for m in args.markets.split(",") if m.strip()]:
        for unit in [int(u) for u in args.units.split(",") if u.strip()]:
            for model in [m.strip() for m in args.signal_models.split(",") if m.strip()]:
                run_args = argparse.Namespace(**{**vars(args), "signal_model": model})
                result = evaluate_market(market, unit, run_args)
                if result:
                    results.append(result)
                    report(result)

    promoted = [r for r in results if r["gate"]["verdict"] == "PROMOTE"]

    payload = {
        "generated_at": stamp,
        "days": args.days,
        "train_days": args.train_days,
        "test_days": args.test_days,
        "step_days": args.step_days,
        "results": results,
        "promoted": [
            {"market": r["market"], "unit": r["unit"], "signal_model": r["signal_model"],
             "median_return_pct": r["challenger"]["median_return_pct"]}
            for r in promoted
        ],
    }

    latest = os.path.join(args.output_dir, "latest.json")
    with open(latest, "w") as fh:
        json.dump(payload, fh, indent=1)
    with open(os.path.join(args.output_dir, "history.jsonl"), "a") as fh:
        fh.write(json.dumps(payload) + "\n")

    print("\n" + "=" * 68)
    print(f"{len(results)} configurations evaluated, {len(promoted)} passed the gate")
    if not promoted:
        # Said plainly so a run of rejections is not mistaken for a broken loop.
        print("Nothing qualified. That is the normal outcome — the gate exists to say no.")
    print(f"report: {latest}")
    print("=" * 68)

    if promoted and args.auto_promote:
        champion = os.path.join(args.output_dir, "champion.json")
        with open(champion, "w") as fh:
            json.dump({"promoted_at": stamp, "candidates": payload["promoted"]}, fh, indent=1)
        print(f"champion written: {champion}")
        print("NOT applied to application.properties — wiring it in is a deliberate, separate step.")

    return len(promoted)


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--markets", default="KRW-BTC,KRW-ETH")
    p.add_argument("--units", default="60,240")
    p.add_argument("--signal-models", default="trend_breakout,squeeze_breakout")
    p.add_argument("--profile", default="BALANCED")
    p.add_argument("--days", type=int, default=730)
    p.add_argument("--train-days", type=int, default=180)
    p.add_argument("--test-days", type=int, default=30)
    p.add_argument("--step-days", type=int, default=30)
    p.add_argument("--max-combos", type=int, default=60)
    p.add_argument("--optimize", action="store_true", default=True)
    p.add_argument("--no-optimize", dest="optimize", action="store_false")
    p.add_argument("--cache-dir", default="data/backtest")
    p.add_argument("--output-dir", default="data/strategy-research")
    p.add_argument("--sleep", type=float, default=0.12)
    p.add_argument("--refresh-cache", action="store_true")
    p.add_argument("--auto-promote", action="store_true",
                   help="write champion.json when something passes; still never edits app config")
    p.add_argument("--once", action="store_true")
    p.add_argument("--interval-hours", type=float, default=24.0)
    args = p.parse_args()

    if args.once:
        sys.exit(0 if one_pass(args) >= 0 else 1)

    while True:
        started = time.time()
        try:
            one_pass(args)
        except KeyboardInterrupt:
            raise
        except Exception as exc:  # noqa: BLE001
            print(f"pass failed: {exc}", file=sys.stderr)
        # Sleep from the START of the pass so a slow pass does not drift the schedule.
        time.sleep(max(60.0, args.interval_hours * 3600 - (time.time() - started)))


if __name__ == "__main__":
    main()
