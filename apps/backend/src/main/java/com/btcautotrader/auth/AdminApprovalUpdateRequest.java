package com.btcautotrader.auth;

public record AdminApprovalUpdateRequest(
        String status,
        String note
) {
}
