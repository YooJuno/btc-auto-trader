#!/usr/bin/env python3
"""Walk-forward evaluation and a promotion gate that says no by default.

Why this exists
---------------
The previous Strategy Lab optimised a 7-day window every hour and accepted results built on a single
trade. That is not research, it is a machine for fitting noise, and it is what walked the take-profit
default from 2.4% down to 1.44% one "consensus" at a time.

The failure had three causes, and each one has a countermeasure here:

  1. Optimising and reporting on the same data. -> Anchored walk-forward: parameters are chosen on a
     training window and scored ONLY on the untouched window that follows it.
  2. No correction for how many configurations were tried. -> Deflated Sharpe. Search 2,000 configs with
     no real edge and the best one still looks good; the threshold rises with the number of trials.
  3. Accepting a result built on a handful of trades. -> Hard minimums on trades and on the number of
     folds, plus a consistency requirement, not just a positive average.

Everything here is a pure function of its inputs so the gate can be unit-tested without touching Upbit.
"""
import math

# A configuration must clear ALL of these. Defaults are deliberately hard to satisfy: the normal and
# correct outcome of a research run is "nothing qualified".
DEFAULT_GATE = {
    "min_folds": 6,             # fewer folds cannot distinguish skill from a lucky regime
    "min_total_trades": 30,     # across all out-of-sample windows combined
    "min_positive_fold_ratio": 0.6,
    "min_median_fold_return_pct": 0.0,
    "max_drawdown_pct": 25.0,
    "min_improvement_pct": 0.5,  # percentage points of OOS return over the incumbent
}


def candles_per_day(unit_minutes):
    return max(1, int(round(1440 / unit_minutes)))


def anchored_folds(total, unit_minutes, train_days, test_days, step_days):
    """Anchored walk-forward index ranges.

    Anchored (train always starts at 0) rather than rolling, because a strategy that only works when
    you forget the distant past is not one to trade. Test windows never overlap, so folds are
    independent samples rather than the same week re-scored many times.
    """
    per_day = candles_per_day(unit_minutes)
    train_len = train_days * per_day
    test_len = test_days * per_day
    step_len = step_days * per_day
    if train_len <= 0 or test_len <= 0 or step_len <= 0:
        return []

    folds = []
    start = train_len
    while start + test_len <= total:
        folds.append({
            "train": (0, start),
            "test": (start, start + test_len),
        })
        start += step_len
    return folds


def expected_max_sharpe(num_trials, num_observations, observed_sharpe=0.0):
    """Sharpe you would expect from the BEST of N trials on pure noise.

    Bailey & Lopez de Prado's benchmark. Without it, "we tried 2,000 configs and the best had Sharpe
    1.4" reads as a finding when it is the arithmetic of searching.
    """
    if num_trials <= 1 or num_observations <= 2:
        return 0.0
    gamma = 0.5772156649  # Euler-Mascheroni
    sigma_sr = math.sqrt((1.0 + 0.5 * observed_sharpe ** 2) / (num_observations - 1))

    def z_inv(p):
        # Acklam-style rational approximation of the normal quantile; ample for a threshold.
        if p <= 0.0 or p >= 1.0:
            return 0.0
        a = [-3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02,
             1.383577518672690e+02, -3.066479806614716e+01, 2.506628277459239e+00]
        b = [-5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02,
             6.680131188771972e+01, -1.328068155288572e+01]
        c = [-7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00,
             -2.549732539343734e+00, 4.374664141464968e+00, 2.938163982698783e+00]
        d = [7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e+00,
             3.754408661907416e+00]
        p_low, p_high = 0.02425, 1 - 0.02425
        if p < p_low:
            q = math.sqrt(-2 * math.log(p))
            return (((((c[0]*q+c[1])*q+c[2])*q+c[3])*q+c[4])*q+c[5]) / ((((d[0]*q+d[1])*q+d[2])*q+d[3])*q+1)
        if p > p_high:
            q = math.sqrt(-2 * math.log(1 - p))
            return -(((((c[0]*q+c[1])*q+c[2])*q+c[3])*q+c[4])*q+c[5]) / ((((d[0]*q+d[1])*q+d[2])*q+d[3])*q+1)
        q = p - 0.5
        r = q * q
        return (((((a[0]*r+a[1])*r+a[2])*r+a[3])*r+a[4])*r+a[5])*q / (((((b[0]*r+b[1])*r+b[2])*r+b[3])*r+b[4])*r+1)

    n = float(num_trials)
    return sigma_sr * ((1 - gamma) * z_inv(1 - 1 / n) + gamma * z_inv(1 - 1 / (n * math.e)))


def summarise_folds(fold_metrics):
    """Collapse per-fold out-of-sample metrics into the numbers the gate reasons about."""
    usable = [m for m in fold_metrics if m]
    if not usable:
        return None

    returns = [m["roi_pct"] for m in usable]
    trades = sum(m.get("sell_trades", 0) or 0 for m in usable)
    positives = sum(1 for r in returns if r > 0)
    ordered = sorted(returns)
    mid = len(ordered) // 2
    median = ordered[mid] if len(ordered) % 2 else (ordered[mid - 1] + ordered[mid]) / 2

    mean = sum(returns) / len(returns)
    var = sum((r - mean) ** 2 for r in returns) / len(returns) if len(returns) > 1 else 0.0
    # Sharpe across folds: consistency between windows, not within one.
    sharpe = mean / math.sqrt(var) if var > 0 else 0.0

    return {
        "folds": len(usable),
        "total_trades": trades,
        "mean_return_pct": mean,
        "median_return_pct": median,
        "positive_fold_ratio": positives / len(usable),
        "worst_fold_pct": min(returns),
        "best_fold_pct": max(returns),
        "max_drawdown_pct": max((m.get("max_drawdown_pct", 0.0) or 0.0) for m in usable),
        "fold_sharpe": sharpe,
        "returns": returns,
    }


def evaluate_gate(summary, num_trials, incumbent_return_pct=None, gate=None):
    """PROMOTE / REJECT with every reason spelled out.

    Returns REJECT unless every criterion passes. A research loop that runs unattended must fail closed:
    the cost of wrongly rejecting a good configuration is a missed opportunity, the cost of wrongly
    promoting a bad one is real money.
    """
    rules = dict(DEFAULT_GATE)
    if gate:
        rules.update(gate)

    if not summary:
        return {"verdict": "REJECT", "reasons": ["no usable folds"], "checks": []}

    checks = []

    def check(name, passed, detail):
        checks.append({"name": name, "passed": bool(passed), "detail": detail})

    check("folds", summary["folds"] >= rules["min_folds"],
          f"{summary['folds']} folds (need >= {rules['min_folds']})")
    check("trades", summary["total_trades"] >= rules["min_total_trades"],
          f"{summary['total_trades']} OOS trades (need >= {rules['min_total_trades']})")
    check("median_return", summary["median_return_pct"] > rules["min_median_fold_return_pct"],
          f"median fold {summary['median_return_pct']:+.2f}% (need > {rules['min_median_fold_return_pct']:.2f}%)")
    check("consistency", summary["positive_fold_ratio"] >= rules["min_positive_fold_ratio"],
          f"{summary['positive_fold_ratio']:.0%} of folds positive (need >= {rules['min_positive_fold_ratio']:.0%})")
    check("drawdown", summary["max_drawdown_pct"] <= rules["max_drawdown_pct"],
          f"worst fold MDD {summary['max_drawdown_pct']:.2f}% (limit {rules['max_drawdown_pct']:.2f}%)")

    threshold = expected_max_sharpe(num_trials, summary["folds"], summary["fold_sharpe"])
    check("deflated_sharpe", summary["fold_sharpe"] > threshold,
          f"fold Sharpe {summary['fold_sharpe']:.3f} vs noise benchmark {threshold:.3f} "
          f"for {num_trials} configs tried")

    if incumbent_return_pct is not None:
        margin = summary["median_return_pct"] - incumbent_return_pct
        check("beats_incumbent", margin >= rules["min_improvement_pct"],
              f"{margin:+.2f}%p vs incumbent (need >= {rules['min_improvement_pct']:+.2f}%p)")

    failed = [c["detail"] for c in checks if not c["passed"]]
    return {
        "verdict": "PROMOTE" if not failed else "REJECT",
        "reasons": failed or ["all criteria passed"],
        "checks": checks,
        "deflated_sharpe_threshold": threshold,
    }
