#!/usr/bin/env python3
import unittest
from unittest import mock

import backtest


def sample_tuning():
    return {
        "rsi_buy": 55.0,
        "rsi_sell": 45.0,
        "rsi_over": 75.0,
        "min_adx": 20.0,
        "min_volume_ratio": 1.2,
        "breakout_pct": 0.5,
        "min_confirmations": 2,
        "max_extension_pct": 3.0,
        "min_ma_long_slope_pct": 0.1,
    }


def sample_params():
    return {
        "boll_window": 20,
        "boll_min_bandwidth_pct": 2.0,
        "boll_max_percent_b": 0.9,
        "profile": "BALANCED",
    }


def sample_indicators():
    return {
        "current_price": 102.5,
        "ma_short": 101.5,
        "ma_long": 100.0,
        "ma_long_slope": 0.5,
        "adx": 25.0,
        "volume_ratio": 1.5,
        "bollinger": {
            "bandwidth_pct": 3.0,
            "percent_b": 0.7,
        },
        "atr_pct": 1.0,
        "rsi": 60.0,
        "macd_hist": 1.0,
        "breakout_level": 101.0,
        "breakdown_level": 99.0,
        "momentum_score_pct": 1.2,
    }


class TradeSignalModelTest(unittest.TestCase):
    def test_unified_trade_signal_model_rejects_partial_trend(self):
        indicators = sample_indicators()
        indicators["ma_short"] = 99.5

        decision = backtest.UnifiedTrendSignalModel().evaluate_buy(
            indicators,
            sample_tuning(),
            sample_params(),
        )

        self.assertEqual(decision["kind"], "SKIP")
        self.assertEqual(decision["reason"], "no trend")

    def test_unified_trade_signal_model_requires_breakout(self):
        indicators = sample_indicators()
        indicators["breakout_level"] = 103.0

        decision = backtest.UnifiedTrendSignalModel().evaluate_buy(
            indicators,
            sample_tuning(),
            sample_params(),
        )

        self.assertEqual(decision["kind"], "SKIP")
        self.assertEqual(decision["reason"], "no breakout")

    def test_unified_trade_signal_model_requires_trend_strength(self):
        indicators = sample_indicators()
        indicators["adx"] = 10.0

        decision = backtest.UnifiedTrendSignalModel().evaluate_buy(
            indicators,
            sample_tuning(),
            sample_params(),
        )

        self.assertEqual(decision["kind"], "SKIP")
        self.assertEqual(decision["reason"], "weak_trend")

    def test_unified_trade_signal_model_returns_trend_breakout_reason(self):
        decision = backtest.UnifiedTrendSignalModel().evaluate_buy(
            sample_indicators(),
            sample_tuning(),
            sample_params(),
        )

        self.assertEqual(decision["kind"], "BUY")
        self.assertEqual(decision["reason"], "trend_breakout")

    def test_backtest_strategy_uses_shared_loop_with_unified_model(self):
        expected = {"metrics": {"roi_pct": 0.0}, "trades": []}
        params = {"initial_cash": 1_000_000.0}

        with mock.patch.object(backtest, "backtest_strategy_with_model", return_value=expected) as delegate:
            result = backtest.backtest_strategy([], params, 15)

        self.assertIs(result, expected)
        delegate.assert_called_once_with([], params, 15, backtest.UNIFIED_SIGNAL_MODEL)

    def test_choose_sell_intent_waits_for_profit_buffer_before_trailing_stop(self):
        params = backtest.make_params(3, "BALANCED")
        params["take_profit_pct"] = 1.44

        state = {
            "qty": 1.0,
            "avg_buy": 100.0,
            "trailing_high": 100.0,
            "entry_atr_pct": 1.0,
            "last_partial_take_index": None,
        }
        indicators = {
            "current_price": 100.30,
            "current_high": 100.30,
            "trailing_window_high": 101.0,
            "breakdown_level": 99.0,
            "macd_hist": 1.0,
            "rsi": 60.0,
            "ma_long": 99.0,
            "atr_pct": 1.0,
        }

        intent = backtest.choose_sell_intent(
            10,
            state,
            indicators,
            params,
            backtest.resolve_signal_tuning(params),
        )

        self.assertIsNone(intent)

    def test_choose_sell_intent_arms_trailing_stop_after_profit_buffer(self):
        params = backtest.make_params(3, "BALANCED")
        params["take_profit_pct"] = 1.44

        state = {
            "qty": 1.0,
            "avg_buy": 100.0,
            "trailing_high": 100.0,
            "entry_atr_pct": 1.0,
            "last_partial_take_index": None,
        }
        indicators = {
            "current_price": 98.70,
            "current_high": 101.8,
            "trailing_window_high": 101.8,
            "breakdown_level": 98.0,
            "macd_hist": 1.0,
            "rsi": 60.0,
            "ma_long": 99.0,
            "atr_pct": 1.0,
        }

        intent = backtest.choose_sell_intent(
            10,
            state,
            indicators,
            params,
            backtest.resolve_signal_tuning(params),
        )

        self.assertIsNotNone(intent)
        self.assertEqual(intent["reason"], "trailing_stop")

    def test_choose_sell_intent_uses_donchian_exit(self):
        params = backtest.make_params(15, "BALANCED")

        state = {
            "qty": 1.0,
            "avg_buy": 100.0,
            "trailing_high": 100.4,
            "entry_atr_pct": 1.0,
            "last_partial_take_index": None,
        }
        indicators = {
            "current_price": 98.8,
            "current_high": 99.2,
            "breakdown_level": 99.0,
            "macd_hist": 1.0,
            "rsi": 60.0,
            "ma_long": 97.0,
            "atr_pct": 1.0,
        }

        intent = backtest.choose_sell_intent(
            20,
            state,
            indicators,
            params,
            backtest.resolve_signal_tuning(params),
        )

        self.assertIsNotNone(intent)
        self.assertEqual(intent["reason"], "donchian_exit")


if __name__ == "__main__":
    unittest.main()
