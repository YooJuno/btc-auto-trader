package com.btcautotrader.auth;

import java.time.OffsetDateTime;

public record AdminApprovalUpdateResponse(
        Long userId,
        String approvalStatus,
        String approvalNote,
        OffsetDateTime approvalUpdatedAt
) {
}
