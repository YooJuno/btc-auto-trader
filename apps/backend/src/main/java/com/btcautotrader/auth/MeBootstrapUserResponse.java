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
        return new MeBootstrapUserResponse(
                user == null ? null : user.getId(),
                user == null ? null : user.getEmail(),
                user == null ? null : user.getDisplayName(),
                user == null ? TradingApprovalStatus.PENDING.name() : TradingApprovalStatus.from(user.getTradingApprovalStatus()).name(),
                user == null ? null : user.getTradingApprovalNote(),
                owner
        );
    }
}
