package com.juno.btctrader.auth;

import java.util.UUID;

import com.juno.btctrader.enums.Role;

public record UserProfileResponse(
		UUID id,
		UUID tenantId,
		String tenantName,
		String email,
		Role role
) {
}
