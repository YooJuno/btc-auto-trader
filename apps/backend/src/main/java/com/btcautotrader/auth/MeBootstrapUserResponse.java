package com.btcautotrader.auth;

public record MeBootstrapUserResponse(
        Long id,
        String email,
        String displayName,
        String approvalStatus,
        String approvalNote,
        boolean owner
) {
    public static MeBootstrapUserResponse from(UserEntity user, boolean owner) {
        String approvalStatus = owner
                ? "ADMIN"
                : (user == null ? TradingApprovalStatus.PENDING.name() : TradingApprovalStatus.from(user.getTradingApprovalStatus()).name());
        return new MeBootstrapUserResponse(
                user == null ? null : user.getId(),
                user == null ? null : user.getEmail(),
                user == null ? null : user.getDisplayName(),
                approvalStatus,
                user == null ? null : user.getTradingApprovalNote(),
                owner
        );
    }
}
