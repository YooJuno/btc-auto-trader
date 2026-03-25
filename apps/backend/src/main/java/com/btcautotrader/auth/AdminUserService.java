package com.btcautotrader.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import com.btcautotrader.tenant.TenantDatabaseProvisioningService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class AdminUserService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final UserExchangeCredentialService userExchangeCredentialService;
    private final CurrentUserService currentUserService;
    private final TenantDatabaseProvisioningService tenantDatabaseProvisioningService;
    private final String ownerEmail;

    public AdminUserService(
            UserRepository userRepository,
            UserExchangeCredentialService userExchangeCredentialService,
            CurrentUserService currentUserService,
            TenantDatabaseProvisioningService tenantDatabaseProvisioningService,
            @Value("${app.multi-tenant.owner-email:juno980220@gmail.com}") String ownerEmail
    ) {
        this.userRepository = userRepository;
        this.userExchangeCredentialService = userExchangeCredentialService;
        this.currentUserService = currentUserService;
        this.tenantDatabaseProvisioningService = tenantDatabaseProvisioningService;
        this.ownerEmail = ownerEmail == null ? "" : ownerEmail.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public List<AdminUserItemResponse> listUsers(String query, String statusFilter) {
        return userRepository.findAll(buildUserListSpecification(query, statusFilter), adminUserSort())
                .stream()
                .map(this::toItem)
                .toList();
    }

    @Transactional
    public AdminUserPageResponse listUsersPage(
            String query,
            String statusFilter,
            Integer page,
            Integer size
    ) {
        Specification<UserEntity> specification = buildUserListSpecification(query, statusFilter);
        int safeSize = sanitizePageSize(size);
        int safePage = sanitizePage(page);

        Page<UserEntity> userPage = userRepository.findAll(
                specification,
                PageRequest.of(safePage, safeSize, adminUserSort())
        );

        if (userPage.getTotalPages() > 0 && safePage >= userPage.getTotalPages()) {
            safePage = userPage.getTotalPages() - 1;
            userPage = userRepository.findAll(
                    specification,
                    PageRequest.of(safePage, safeSize, adminUserSort())
            );
        }

        List<AdminUserItemResponse> items = userPage.getContent().stream()
                .map(this::toItem)
                .toList();

        return new AdminUserPageResponse(
                items,
                userPage.getTotalElements(),
                userPage.getTotalPages(),
                userPage.getNumber(),
                userPage.getSize(),
                userPage.hasNext(),
                userPage.hasPrevious()
        );
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
        return new AdminUserItemResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getLastLoginAt(),
                TradingApprovalStatus.from(user.getTradingApprovalStatus()).name(),
                user.getTradingApprovalNote(),
                credentialStatus.configured() || credentialStatus.usingDefaultCredentials()
        );
    }

    private Specification<UserEntity> buildUserListSpecification(String query, String statusFilter) {
        String normalizedQuery = normalize(query);
        TradingApprovalStatus normalizedStatus = statusFilter == null || statusFilter.isBlank()
                ? null
                : TradingApprovalStatus.from(statusFilter);

        return Specification.where(excludeOwner())
                .and(matchesQuery(normalizedQuery))
                .and(matchesStatus(normalizedStatus));
    }

    private static Sort adminUserSort() {
        return Sort.by(Sort.Direction.DESC, "lastLoginAt");
    }

    private Specification<UserEntity> excludeOwner() {
        if (ownerEmail == null || ownerEmail.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.or(
                cb.isNull(root.get("email")),
                cb.notEqual(cb.lower(root.get("email")), ownerEmail)
        );
    }

    private static Specification<UserEntity> matchesQuery(String queryText) {
        if (queryText == null || queryText.isBlank()) {
            return null;
        }
        String pattern = "%" + queryText + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(cb.coalesce(root.get("email"), "")), pattern),
                cb.like(cb.lower(cb.coalesce(root.get("displayName"), "")), pattern),
                cb.like(cb.lower(cb.coalesce(root.get("provider"), "")), pattern),
                cb.like(cb.lower(cb.coalesce(root.get("providerUserId"), "")), pattern)
        );
    }

    private static Specification<UserEntity> matchesStatus(TradingApprovalStatus statusFilter) {
        if (statusFilter == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("tradingApprovalStatus"), statusFilter.name());
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

    private static int sanitizePage(Integer page) {
        if (page == null || page < 0) {
            return 0;
        }
        return page;
    }

    private static int sanitizePageSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
