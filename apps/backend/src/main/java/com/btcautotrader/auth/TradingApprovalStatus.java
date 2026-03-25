package com.btcautotrader.auth;

import java.util.Locale;

public enum TradingApprovalStatus {
    PENDING,
    APPROVED,
    SUSPENDED;

    public static TradingApprovalStatus from(String value) {
        if (value == null || value.isBlank()) {
            return PENDING;
        }
        try {
            return TradingApprovalStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return PENDING;
        }
    }
}
