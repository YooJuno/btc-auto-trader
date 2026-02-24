#!/usr/bin/env python3
"""Continuous strategy research daemon.

Runs backtests periodically (separate from live trading), stores history,
and generates rolling recommendations for manual code/runtime updates.
"""

import argparse
import json
import statistics
import subprocess
import sys
import time
from collections import Counter
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional


NUMERIC_PARAM_KEYS = [
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


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def tail_lines(text: str, count: int = 80) -> str:
    lines = text.splitlines()
    if len(lines) <= count:
        return text
    return "\n".join(lines[-count:])


def ensure_dir(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True)


def read_json(path: Path) -> Optional[Dict[str, Any]]:
    if not path.exists():
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return None


def append_jsonl(path: Path, item: Dict[str, Any]) -> None:
    with path.open("a", encoding="utf-8") as file:
        file.write(json.dumps(item, ensure_ascii=False) + "\n")


def load_jsonl(path: Path) -> List[Dict[str, Any]]:
    if not path.exists():
        return []
    rows: List[Dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as file:
        for line in file:
            line = line.strip()
            if not line:
                continue
            try:
                rows.append(json.loads(line))
            except json.JSONDecodeError:
                continue
    return rows


@dataclass
class ProfileRun:
    profile: str
    ok: bool
    duration_sec: float
    report_path: str
    stdout_tail: str
    stderr_tail: str
    error: Optional[str]
    candidate: Optional[Dict[str, Any]]


class StrategyLab:
    def __init__(self, args: argparse.Namespace):
        self.args = args
        self.root = Path(args.workdir).resolve()
        self.output_dir = Path(args.output_dir).resolve()
        self.cycles_dir = self.output_dir / "cycles"
        self.history_path = self.output_dir / "history.jsonl"
        self.latest_path = self.output_dir / "latest.json"
        self.consensus_path = self.output_dir / "consensus.json"
        self.todo_path = self.output_dir / "next_codex_request.json"

        ensure_dir(self.output_dir)
        ensure_dir(self.cycles_dir)

    def run(self) -> None:
        while True:
            cycle_started = time.time()
            snapshot = self.run_cycle()
            self.persist_cycle(snapshot)

            if self.args.single_run:
                return

            elapsed = time.time() - cycle_started
            sleep_sec = max(0, int(self.args.interval_minutes * 60 - elapsed))
            if sleep_sec > 0:
                print(f"[strategy-lab] sleeping {sleep_sec}s before next cycle")
                time.sleep(sleep_sec)

    def run_cycle(self) -> Dict[str, Any]:
        cycle_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        cycle_dir = self.cycles_dir / cycle_id
        ensure_dir(cycle_dir)

        print(f"[strategy-lab] cycle started: {cycle_id}")
        profile_runs: List[ProfileRun] = []

        for profile in self.args.profiles:
            profile_runs.append(self.run_profile(profile, cycle_dir))

        candidates = [r.candidate for r in profile_runs if r.ok and r.candidate is not None]
        selected = self.select_candidate([c for c in candidates if c is not None])

        snapshot = {
            "cycle_id": cycle_id,
            "timestamp_utc": utc_now_iso(),
            "market": self.args.market,
            "days": self.args.days,
            "profiles": self.args.profiles,
            "short_unit": self.args.short_unit,
            "mid_unit": self.args.mid_unit,
            "max_combos": self.args.max_combos,
            "runs": [
                {
                    "profile": run.profile,
                    "ok": run.ok,
                    "duration_sec": round(run.duration_sec, 3),
                    "report_path": run.report_path,
                    "error": run.error,
                    "stdout_tail": run.stdout_tail,
                    "stderr_tail": run.stderr_tail,
                    "candidate": run.candidate,
                }
                for run in profile_runs
            ],
            "selected": selected,
        }
        return snapshot

    def run_profile(self, profile: str, cycle_dir: Path) -> ProfileRun:
        started = time.time()
        report_path = cycle_dir / f"report_{profile.lower()}.json"
        cmd = [
            self.args.python_bin,
            "scripts/backtest.py",
            "--market",
            self.args.market,
            "--days",
            str(self.args.days),
            "--profile",
            profile,
            "--short-unit",
            str(self.args.short_unit),
            "--mid-unit",
            str(self.args.mid_unit),
            "--cache-dir",
            self.args.cache_dir,
            "--split-ratio",
            str(self.args.split_ratio),
            "--optimize",
            "--max-combos",
            str(self.args.max_combos),
            "--export",
            str(report_path),
        ]
        if self.args.refresh_cache:
            cmd.append("--refresh-cache")

        print(f"[strategy-lab] running profile={profile}")
        proc = subprocess.run(
            cmd,
            cwd=self.root,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            check=False,
        )
        duration = time.time() - started

        stdout_tail = tail_lines(proc.stdout)
        stderr_tail = tail_lines(proc.stderr)

        if proc.returncode != 0:
            return ProfileRun(
                profile=profile,
                ok=False,
                duration_sec=duration,
                report_path=str(report_path),
                stdout_tail=stdout_tail,
                stderr_tail=stderr_tail,
                error=f"backtest exited with code {proc.returncode}",
                candidate=None,
            )

        report = read_json(report_path)
        if report is None:
            return ProfileRun(
                profile=profile,
                ok=False,
                duration_sec=duration,
                report_path=str(report_path),
                stdout_tail=stdout_tail,
                stderr_tail=stderr_tail,
                error="failed to parse report json",
                candidate=None,
            )

        candidate = self.candidate_from_report(profile, report)
        return ProfileRun(
            profile=profile,
            ok=True,
            duration_sec=duration,
            report_path=str(report_path),
            stdout_tail=stdout_tail,
            stderr_tail=stderr_tail,
            error=None,
            candidate=candidate,
        )

    def candidate_from_report(self, profile: str, report: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        recommended = report.get("recommended") or {}
        params = recommended.get("params") or {}
        metrics = recommended.get("strategy_metrics") or {}
        buy_hold = recommended.get("buy_hold_metrics") or {}

        if not params or not metrics:
            return None

        score = self.score_candidate(metrics, recommended.get("alpha_pct"))

        strategy_payload = {
            "takeProfitPct": params.get("take_profit_pct"),
            "stopLossPct": params.get("stop_loss_pct"),
            "trailingStopPct": params.get("trailing_stop_pct"),
            "partialTakeProfitPct": params.get("partial_take_profit_pct"),
            "trendExitPct": params.get("trend_exit_pct"),
            "momentumExitPct": params.get("momentum_exit_pct"),
        }

        signal_patch = {
            "signal.min-adx": params.get("min_adx"),
            "signal.min-volume-ratio": params.get("min_volume_ratio"),
            "signal.breakout-pct": params.get("breakout_pct"),
            "risk.target-vol-pct": params.get("target_vol_pct"),
        }

        return {
            "profile": profile,
            "unit": recommended.get("unit"),
            "score": round(score, 6),
            "alpha_pct": recommended.get("alpha_pct"),
            "params": params,
            "strategy_metrics": metrics,
            "buy_hold_metrics": buy_hold,
            "strategy_patch_payload": strategy_payload,
            "signal_patch": signal_patch,
        }

    def score_candidate(self, metrics: Dict[str, Any], alpha_pct: Optional[float]) -> float:
        roi = float(metrics.get("roi_pct") or 0.0)
        mdd = float(metrics.get("max_drawdown_pct") or 0.0)
        sharpe = metrics.get("sharpe")
        pf = metrics.get("profit_factor")
        trades_per_day = float(metrics.get("trades_per_day") or 0.0)
        sell_trades = int(metrics.get("sell_trades") or 0)

        score = roi - (0.7 * mdd)

        if sharpe is not None:
            score += min(float(sharpe), 3.0) * 0.2
        if pf is not None:
            score += min(float(pf), 2.0) * 0.5
        if alpha_pct is not None:
            score += min(float(alpha_pct), 30.0) * 0.03

        if sell_trades < self.args.min_sell_trades:
            score -= 2.0
        if trades_per_day < self.args.min_trades_per_day or trades_per_day > self.args.max_trades_per_day:
            score -= 1.2

        return score

    def select_candidate(self, candidates: List[Dict[str, Any]]) -> Optional[Dict[str, Any]]:
        if not candidates:
            return None
        ranked = sorted(candidates, key=lambda c: float(c.get("score") or -1e18), reverse=True)
        return ranked[0]

    def persist_cycle(self, snapshot: Dict[str, Any]) -> None:
        append_jsonl(self.history_path, snapshot)
        self.latest_path.write_text(json.dumps(snapshot, ensure_ascii=False, indent=2), encoding="utf-8")

        history = load_jsonl(self.history_path)
        if len(history) > self.args.history_limit:
            history = history[-self.args.history_limit:]
            self.history_path.write_text(
                "\n".join(json.dumps(item, ensure_ascii=False) for item in history) + "\n",
                encoding="utf-8",
            )

        consensus = self.build_consensus(history)
        if consensus is not None:
            self.consensus_path.write_text(json.dumps(consensus, ensure_ascii=False, indent=2), encoding="utf-8")
            self.todo_path.write_text(json.dumps(self.make_codex_todo(consensus), ensure_ascii=False, indent=2), encoding="utf-8")

        selected = snapshot.get("selected")
        if selected:
            print(
                "[strategy-lab] selected"
                f" profile={selected.get('profile')}"
                f" unit={selected.get('unit')}"
                f" score={selected.get('score')}"
                f" roi={selected.get('strategy_metrics', {}).get('roi_pct')}"
            )
        else:
            print("[strategy-lab] no valid candidate this cycle")

    def build_consensus(self, history: List[Dict[str, Any]]) -> Optional[Dict[str, Any]]:
        recent = [item.get("selected") for item in history[-self.args.consensus_lookback :]]
        recent = [item for item in recent if item is not None]
        if not recent:
            return None

        params_by_key: Dict[str, List[float]] = {key: [] for key in NUMERIC_PARAM_KEYS}
        profile_counter: Counter[str] = Counter()
        unit_counter: Counter[int] = Counter()

        for selected in recent:
            params = selected.get("params") or {}
            profile = str(selected.get("profile") or "BALANCED").upper()
            unit = selected.get("unit")
            profile_counter[profile] += 1
            if isinstance(unit, int):
                unit_counter[unit] += 1

            for key in NUMERIC_PARAM_KEYS:
                value = params.get(key)
                if isinstance(value, (int, float)):
                    params_by_key[key].append(float(value))

        consensus_params: Dict[str, Any] = {}
        for key, values in params_by_key.items():
            if not values:
                continue
            consensus_params[key] = round(statistics.median(values), 6)

        if not consensus_params:
            return None

        consensus_profile = profile_counter.most_common(1)[0][0] if profile_counter else "BALANCED"
        consensus_unit = unit_counter.most_common(1)[0][0] if unit_counter else self.args.mid_unit

        strategy_payload = {
            "takeProfitPct": consensus_params.get("take_profit_pct"),
            "stopLossPct": consensus_params.get("stop_loss_pct"),
            "trailingStopPct": consensus_params.get("trailing_stop_pct"),
            "partialTakeProfitPct": consensus_params.get("partial_take_profit_pct"),
            "trendExitPct": consensus_params.get("trend_exit_pct"),
            "momentumExitPct": consensus_params.get("momentum_exit_pct"),
            "profile": consensus_profile,
        }

        signal_patch = {
            "signal.min-adx": consensus_params.get("min_adx"),
            "signal.min-volume-ratio": consensus_params.get("min_volume_ratio"),
            "signal.breakout-pct": consensus_params.get("breakout_pct"),
            "risk.target-vol-pct": consensus_params.get("target_vol_pct"),
        }

        return {
            "timestamp_utc": utc_now_iso(),
            "market": self.args.market,
            "lookback_cycles": min(self.args.consensus_lookback, len(history)),
            "sample_count": len(recent),
            "profile_mode": consensus_profile,
            "unit_mode": consensus_unit,
            "consensus_params": consensus_params,
            "strategy_patch_payload": strategy_payload,
            "signal_patch": signal_patch,
            "source_files": {
                "latest": str(self.latest_path),
                "history": str(self.history_path),
            },
        }

    @staticmethod
    def make_codex_todo(consensus: Dict[str, Any]) -> Dict[str, Any]:
        return {
            "summary": "Use accumulated strategy-lab results to tune strategy defaults.",
            "generated_at_utc": utc_now_iso(),
            "recommendation": consensus,
            "apply_targets": [
                "apps/backend/src/main/java/com/btcautotrader/strategy/StrategyService.java",
                "apps/backend/src/main/resources/application.properties",
            ],
            "notes": [
                "Do not auto-deploy without manual review.",
                "Prioritize test ROI, MDD, and trade frequency stability.",
            ],
        }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Continuous strategy backtest daemon")
    parser.add_argument("--workdir", default=".")
    parser.add_argument("--python-bin", default=sys.executable)
    parser.add_argument("--market", default="KRW-BTC")
    parser.add_argument("--days", type=int, default=90)
    parser.add_argument("--profiles", default="BALANCED,CONSERVATIVE,AGGRESSIVE")
    parser.add_argument("--short-unit", type=int, default=3)
    parser.add_argument("--mid-unit", type=int, default=15)
    parser.add_argument("--split-ratio", type=float, default=0.7)
    parser.add_argument("--max-combos", type=int, default=60)
    parser.add_argument("--cache-dir", default="data/backtest")
    parser.add_argument("--output-dir", default="data/strategy-lab")
    parser.add_argument("--interval-minutes", type=int, default=60)
    parser.add_argument("--history-limit", type=int, default=300)
    parser.add_argument("--consensus-lookback", type=int, default=12)
    parser.add_argument("--min-sell-trades", type=int, default=8)
    parser.add_argument("--min-trades-per-day", type=float, default=0.15)
    parser.add_argument("--max-trades-per-day", type=float, default=4.5)
    parser.add_argument("--refresh-cache", action="store_true")
    parser.add_argument("--single-run", action="store_true")

    args = parser.parse_args()
    profiles = [item.strip().upper() for item in args.profiles.split(",") if item.strip()]
    if not profiles:
        raise ValueError("profiles must not be empty")
    args.profiles = profiles
    return args


def main() -> None:
    args = parse_args()
    lab = StrategyLab(args)
    lab.run()


if __name__ == "__main__":
    main()
