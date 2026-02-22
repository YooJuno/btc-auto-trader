package com.btcautotrader.strategy;

public enum StrategyProfile {
    AGGRESSIVE,
    BALANCED,
    CONSERVATIVE;

    public static StrategyProfile from(String value) {
        if (value == null || value.isBlank()) {
            return BALANCED;
        }
        try {
            return StrategyProfile.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return BALANCED;
        }
    }
}
