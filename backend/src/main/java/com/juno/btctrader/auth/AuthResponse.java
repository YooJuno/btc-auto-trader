package com.juno.btctrader.auth;

public record AuthResponse(String token, UserProfileResponse user) {
}
