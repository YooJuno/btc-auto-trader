package com.hvs.btctrader.auth;

public record JwtUser(String userId, String tenantId, String email, String role) {
}
