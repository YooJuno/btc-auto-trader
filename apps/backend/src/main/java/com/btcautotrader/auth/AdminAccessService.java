package com.btcautotrader.auth;

import com.btcautotrader.feature.FeatureFlagService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminAccessService {
    private final CurrentUserService currentUserService;
    private final FeatureFlagService featureFlagService;

    public AdminAccessService(CurrentUserService currentUserService, FeatureFlagService featureFlagService) {
        this.currentUserService = currentUserService;
        this.featureFlagService = featureFlagService;
    }

    public UserEntity requireOwner(Authentication authentication) {
        if (!featureFlagService.adminApprovalEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "admin approval feature is disabled");
        }
        UserEntity user = currentUserService.requireUser(authentication);
        if (!currentUserService.isOwner(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "owner only");
        }
        return user;
    }
}
