package com.juno.btctrader.strategy;

import com.juno.btctrader.enums.StrategyMode;

public final class StrategyDefaults {
	private StrategyDefaults() {
	}

	public static StrategyParameters forMode(StrategyMode mode) {
		return switch (mode) {
			case SCALP -> new StrategyParameters(9, 21, 14, 14, 20, 2.0, 0.003, 0.07, 55, 45, 35, 65);
			case SWING -> new StrategyParameters(20, 50, 14, 14, 20, 2.0, 0.008, 0.05, 52, 48, 30, 70);
			case DAY -> new StrategyParameters(12, 26, 14, 14, 20, 2.0, 0.005, 0.06, 52, 48, 35, 65);
			case AUTO -> new StrategyParameters(12, 26, 14, 14, 20, 2.0, 0.005, 0.06, 52, 48, 35, 65);
		};
	}
}
