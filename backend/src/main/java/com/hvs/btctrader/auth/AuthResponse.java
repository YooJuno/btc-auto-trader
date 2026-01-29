package com.hvs.btctrader.auth;

public record AuthResponse(String token, UserProfileResponse user) {
}
