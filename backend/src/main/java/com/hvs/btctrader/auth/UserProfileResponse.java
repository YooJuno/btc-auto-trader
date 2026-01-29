package com.hvs.btctrader.auth;

import java.util.UUID;

import com.hvs.btctrader.enums.Role;

public record UserProfileResponse(
		UUID id,
		UUID tenantId,
		String tenantName,
		String email,
		Role role
) {
}
