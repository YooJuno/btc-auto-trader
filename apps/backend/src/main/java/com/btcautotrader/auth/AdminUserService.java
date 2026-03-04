package com.btcautotrader.auth;

import com.btcautotrader.tenant.TenantDatabaseProvisioningService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class AdminUserService {
    private final UserRepository userRepository;
    private final UserExchangeCredentialService userExchangeCredentialService;
    private final UserOnboardingService userOnboardingService;
    private final CurrentUserService currentUserService;
    private final TenantDatabaseProvisioningService tenantDatabaseProvisioningService;

    public AdminUserService(
            UserRepository userRepository,
            UserExchangeCredentialService userExchangeCredentialService,
            UserOnboardingService userOnboardingService,
            CurrentUserService currentUserService,
            TenantDatabaseProvisioningService tenantDatabaseProvisioningService
    ) {
        this.userRepository = userRepository;
        this.userExchangeCredentialService = userExchangeCredentialService;
        this.userOnboardingService = userOnboardingService;
        this.currentUserService = currentUserService;
        this.tenantDatabaseProvisioningService = tenantDatabaseProvisioningService;
    }

    @Transactional
    public List<AdminUserItemResponse> listUsers(String query, String statusFilter) {
        String normalizedQuery = normalize(query);
        TradingApprovalStatus normalizedStatus = statusFilter == null || statusFilter.isBlank()
                ? null
                : TradingApprovalStatus.from(statusFilter);

        return userRepository.findAllByOrderByLastLoginAtDesc()
                .stream()
                .filter(user -> !currentUserService.isOwner(user))
                .filter(user -> matchesQuery(user, normalizedQuery))
                .filter(user -> matchesStatus(user, normalizedStatus))
                .map(this::toItem)
                .toList();
    }

    @Transactional
    public AdminApprovalUpdateResponse updateApproval(Long userId, AdminApprovalUpdateRequest request) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
        TradingApprovalStatus status = TradingApprovalStatus.from(request.status());
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("user not found"));
        if (currentUserService.isOwner(user)) {
            throw new IllegalArgumentException("owner account cannot be modified");
        }
        user.setTradingApprovalStatus(status.name());
        user.setTradingApprovalNote(normalizeNote(request.note()));
        user.setTradingApprovalUpdatedAt(OffsetDateTime.now());
        UserEntity saved = userRepository.save(user);
        saved = tenantDatabaseProvisioningService.ensureTenant(saved);
        OffsetDateTime tenantProvisionedAt = tenantDatabaseProvisioningService.resolveTenantProvisionedAt(saved.getTenantDatabase());
        return new AdminApprovalUpdateResponse(
                saved.getId(),
                TradingApprovalStatus.from(saved.getTradingApprovalStatus()).name(),
                saved.getTradingApprovalNote(),
                saved.getTradingApprovalUpdatedAt(),
                saved.getTenantDatabase(),
                tenantProvisionedAt
        );
    }

    @Transactional
    public AdminUserDeleteResponse deleteUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("user not found"));
        if (currentUserService.isOwner(user)) {
            throw new IllegalArgumentException("owner account cannot be deleted");
        }

        String tenantDatabase = normalizeTenantDatabase(user.getTenantDatabase());
        boolean dedicatedTenant = tenantDatabase != null
                && !tenantDatabaseProvisioningService.isSystemTenantDatabase(tenantDatabase);
        if (dedicatedTenant) {
            boolean sharedByOthers = userRepository.findAllByTenantDatabaseOrderByIdAsc(tenantDatabase)
                    .stream()
                    .anyMatch(candidate -> !Objects.equals(candidate.getId(), userId));
            if (sharedByOthers) {
                throw new IllegalArgumentException("tenant database is shared by multiple users");
            }
        }

        userRepository.delete(user);

        boolean tenantDatabaseDropped = false;
        if (dedicatedTenant) {
            tenantDatabaseDropped = tenantDatabaseProvisioningService.dropDedicatedTenantDatabase(tenantDatabase);
        }

        return new AdminUserDeleteResponse(userId, tenantDatabase, tenantDatabaseDropped);
    }

    private AdminUserItemResponse toItem(UserEntity user) {
        UserExchangeCredentialStatusResponse credentialStatus = userExchangeCredentialService.getStatus(user);
        UserOnboardingStateResponse onboardingState = userOnboardingService.getState(user);
        return new AdminUserItemResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getLastLoginAt(),
                TradingApprovalStatus.from(user.getTradingApprovalStatus()).name(),
                user.getTradingApprovalNote(),
                credentialStatus.configured() || credentialStatus.usingDefaultCredentials(),
                onboardingState.completed()
        );
    }

    private static boolean matchesQuery(UserEntity user, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String email = normalize(user.getEmail());
        String displayName = normalize(user.getDisplayName());
        String provider = normalize(user.getProvider());
        String providerUserId = normalize(user.getProviderUserId());
        return contains(email, query)
                || contains(displayName, query)
                || contains(provider, query)
                || contains(providerUserId, query);
    }

    private static boolean matchesStatus(UserEntity user, TradingApprovalStatus statusFilter) {
        if (statusFilter == null) {
            return true;
        }
        return TradingApprovalStatus.from(user.getTradingApprovalStatus()) == statusFilter;
    }

    private static boolean contains(String source, String query) {
        return source != null && source.contains(query);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeNote(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > 500) {
            return trimmed.substring(0, 500);
        }
        return trimmed;
    }

    private static String normalizeTenantDatabase(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
