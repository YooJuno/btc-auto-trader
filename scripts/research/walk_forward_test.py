#!/usr/bin/env python3
"""Tests for the promotion gate.

This gate is the only thing standing between an unattended search and someone's money, so its refusals
matter more than its approvals. Most of these assert that plausible-looking results are still rejected.
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import walk_forward as wf


def fold(roi, trades=10, mdd=2.0):
    return {"roi_pct": roi, "sell_trades": trades, "max_drawdown_pct": mdd}


def strong_folds(n=8, roi=2.0, trades=10):
    return [fold(roi + (i % 3) * 0.1, trades) for i in range(n)]


class AnchoredFoldsTest(unittest.TestCase):
    def test_test_windows_never_overlap(self):
        # Overlapping test windows are the same week scored repeatedly, which inflates confidence
        # exactly the way the old hourly re-mining did.
        folds = wf.anchored_folds(total=24 * 400, unit_minutes=60,
                                  train_days=180, test_days=30, step_days=30)
        self.assertGreater(len(folds), 1)
        for a, b in zip(folds, folds[1:]):
            self.assertLessEqual(a["test"][1], b["test"][0])

    def test_training_window_is_anchored_and_grows(self):
        folds = wf.anchored_folds(24 * 400, 60, 180, 30, 30)
        for f in folds:
            self.assertEqual(f["train"][0], 0)
        self.assertLess(folds[0]["train"][1], folds[-1]["train"][1])

    def test_returns_nothing_when_history_is_too_short(self):
        self.assertEqual(wf.anchored_folds(24 * 30, 60, 180, 30, 30), [])


class DeflatedSharpeTest(unittest.TestCase):
    def test_threshold_rises_with_the_number_of_trials(self):
        # The whole point: searching harder must make the bar higher.
        few = wf.expected_max_sharpe(10, 12)
        many = wf.expected_max_sharpe(5000, 12)
        self.assertGreater(many, few)

    def test_no_threshold_for_a_single_trial(self):
        self.assertEqual(wf.expected_max_sharpe(1, 12), 0.0)


class GateTest(unittest.TestCase):
    def test_promotes_a_result_that_clears_everything(self):
        summary = wf.summarise_folds(strong_folds())
        verdict = wf.evaluate_gate(summary, num_trials=10, incumbent_return_pct=0.0)
        self.assertEqual(verdict["verdict"], "PROMOTE", verdict["reasons"])

    def test_rejects_too_few_folds(self):
        summary = wf.summarise_folds(strong_folds(n=3))
        self.assertEqual(wf.evaluate_gate(summary, 10, 0.0)["verdict"], "REJECT")

    def test_rejects_too_few_trades(self):
        # Two trades per fold looks fine on average and means nothing.
        summary = wf.summarise_folds(strong_folds(n=8, trades=2))
        verdict = wf.evaluate_gate(summary, 10, 0.0)
        self.assertEqual(verdict["verdict"], "REJECT")
        self.assertTrue(any("OOS trades" in r for r in verdict["reasons"]))

    def test_rejects_one_huge_win_carrying_losing_folds(self):
        # Mean is strongly positive, median is not. This is the shape of a lucky regime.
        folds = [fold(-1.0) for _ in range(6)] + [fold(40.0)]
        summary = wf.summarise_folds(folds)
        verdict = wf.evaluate_gate(summary, 10, 0.0)
        self.assertEqual(verdict["verdict"], "REJECT")
        self.assertGreater(summary["mean_return_pct"], 0)

    def test_rejects_inconsistent_results(self):
        folds = [fold(5.0), fold(-4.0), fold(6.0), fold(-5.0), fold(4.0), fold(-3.0),
                 fold(5.0), fold(-4.0)]
        verdict = wf.evaluate_gate(wf.summarise_folds(folds), 10, 0.0)
        self.assertEqual(verdict["verdict"], "REJECT")

    def test_rejects_a_wide_search_that_only_beats_noise(self):
        # Same result, but found by trying thousands of configurations instead of ten.
        summary = wf.summarise_folds(strong_folds(n=8, roi=0.4))
        narrow = wf.evaluate_gate(summary, num_trials=5, incumbent_return_pct=0.0)
        wide = wf.evaluate_gate(summary, num_trials=100000, incumbent_return_pct=0.0)
        self.assertEqual(narrow["verdict"], "PROMOTE", narrow["reasons"])
        self.assertEqual(wide["verdict"], "REJECT")
        self.assertTrue(any("noise benchmark" in r for r in wide["reasons"]))

    def test_rejects_a_challenger_that_only_ties_the_incumbent(self):
        # Churning the live configuration for no measurable gain is a cost, not a neutral act.
        summary = wf.summarise_folds(strong_folds(n=8, roi=2.0))
        verdict = wf.evaluate_gate(summary, 10, incumbent_return_pct=2.0)
        self.assertEqual(verdict["verdict"], "REJECT")
        self.assertTrue(any("incumbent" in r for r in verdict["reasons"]))

    def test_rejects_excessive_drawdown(self):
        folds = [fold(3.0, mdd=60.0) for _ in range(8)]
        verdict = wf.evaluate_gate(wf.summarise_folds(folds), 10, 0.0)
        self.assertEqual(verdict["verdict"], "REJECT")
        self.assertTrue(any("MDD" in r for r in verdict["reasons"]))

    def test_rejects_when_there_is_nothing_to_judge(self):
        self.assertEqual(wf.evaluate_gate(None, 10)["verdict"], "REJECT")
        self.assertEqual(wf.evaluate_gate(wf.summarise_folds([None, None]), 10)["verdict"], "REJECT")

    def test_every_criterion_is_reported_not_just_the_first_failure(self):
        # An unattended loop is only useful if its refusals are legible.
        verdict = wf.evaluate_gate(wf.summarise_folds(strong_folds()), 10, 0.0)
        names = {c["name"] for c in verdict["checks"]}
        self.assertEqual(
            names,
            {"folds", "trades", "median_return", "consistency", "drawdown",
             "deflated_sharpe", "beats_incumbent"},
        )


if __name__ == "__main__":
    unittest.main(verbosity=1)
