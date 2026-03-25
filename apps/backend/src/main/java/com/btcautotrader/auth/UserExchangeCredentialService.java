package com.btcautotrader.auth;

import com.btcautotrader.tenant.TenantContext;
import com.btcautotrader.tenant.TenantDataSourceProvider;
import com.btcautotrader.upbit.UpbitAuthCredentials;
import com.btcautotrader.upbit.UpbitCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class UserExchangeCredentialService {
    private static final Logger log = LoggerFactory.getLogger(UserExchangeCredentialService.class);

    private final UserExchangeCredentialRepository credentialRepository;
    private final UserRepository userRepository;
    private final CredentialCryptoService credentialCryptoService;
    private final UpbitCredentials defaultUpbitCredentials;
    private final String systemTenantDatabase;
    private final String ownerEmail;

    public UserExchangeCredentialService(
            UserExchangeCredentialRepository credentialRepository,
            UserRepository userRepository,
            CredentialCryptoService credentialCryptoService,
            UpbitCredentials defaultUpbitCredentials,
            TenantDataSourceProvider tenantDataSourceProvider,
            @Value("${app.multi-tenant.owner-email:juno980220@gmail.com}") String ownerEmail
    ) {
        this.credentialRepository = credentialRepository;
        this.userRepository = userRepository;
        this.credentialCryptoService = credentialCryptoService;
        this.defaultUpbitCredentials = defaultUpbitCredentials;
        this.systemTenantDatabase = tenantDataSourceProvider.getSystemDatabaseName();
        this.ownerEmail = ownerEmail == null ? "" : ownerEmail.trim().toLowerCase(Locale.ROOT);
    }

    public UserExchangeCredentialStatusResponse getStatus(UserEntity user) {
        if (user == null || user.getId() == null) {
            return new UserExchangeCredentialStatusResponse(false, false, null);
        }
        return TenantContext.callWithTenantDatabase(null, () -> {
            UserExchangeCredentialEntity stored = credentialRepository.findById(user.getId()).orElse(null);
            if (stored != null) {
                return new UserExchangeCredentialStatusResponse(true, false, stored.getUpdatedAt());
            }
            boolean usingDefault = isOwner(user) && defaultUpbitCredentials.isConfigured();
            return new UserExchangeCredentialStatusResponse(false, usingDefault, null);
        });
    }

    @Transactional
    public UserExchangeCredentialStatusResponse save(UserEntity user, UserExchangeCredentialRequest request) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("user is required");
        }
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
        String accessKey = normalizeSecret(request.accessKey());
        String secretKey = normalizeSecret(request.secretKey());
        if (accessKey == null || secretKey == null) {
            throw new IllegalArgumentException("accessKey and secretKey are required");
        }

        return TenantContext.callWithTenantDatabase(null, () -> {
            UserExchangeCredentialEntity entity = credentialRepository.findById(user.getId()).orElseGet(() -> {
                UserExchangeCredentialEntity created = new UserExchangeCredentialEntity();
                created.setUserId(user.getId());
                return created;
            });
            entity.setAccessKeyEncrypted(credentialCryptoService.encrypt(accessKey));
            entity.setSecretKeyEncrypted(credentialCryptoService.encrypt(secretKey));
            UserExchangeCredentialEntity saved = credentialRepository.save(entity);
            return new UserExchangeCredentialStatusResponse(true, false, saved.getUpdatedAt());
        });
    }

    @Transactional
    public void delete(UserEntity user) {
        if (user == null || user.getId() == null) {
            return;
        }
        TenantContext.runWithTenantDatabase(null, () -> credentialRepository.deleteById(user.getId()));
    }

    public Optional<UpbitAuthCredentials> resolveCredentialsForCurrentTenant() {
        TenantTradingPrincipalResolution resolution = resolveTradingPrincipalForCurrentTenant();
        return resolution.credentials();
    }

    public Optional<UserEntity> findUserForCurrentTenant() {
        TenantTradingPrincipalResolution resolution = resolveTradingPrincipalForCurrentTenant();
        return resolution.user();
    }

    public TenantTradingPrincipalResolution resolveTradingPrincipalForCurrentTenant() {
        String tenantDatabase = TenantContext.getTenantDatabase();
        if (tenantDatabase == null || tenantDatabase.isBlank()) {
            return TenantTradingPrincipalResolution.noTenant();
        }
        return resolveTradingPrincipalForTenant(tenantDatabase.trim());
    }

    public TenantTradingPrincipalResolution resolveTradingPrincipalForTenant(String tenantDatabase) {
        if (tenantDatabase == null || tenantDatabase.isBlank()) {
            return TenantTradingPrincipalResolution.noTenant();
        }
        String normalizedTenant = tenantDatabase.trim();
        return TenantContext.callWithTenantDatabase(null, () -> resolveTradingPrincipalForTenantInternal(normalizedTenant));
    }

    public boolean hasCredentialsForUser(UserEntity user) {
        return user != null && resolveCredentialsForUser(user).isPresent();
    }

    public Optional<UpbitAuthCredentials> resolveCredentialsForUser(UserEntity user) {
        if (user == null || user.getId() == null) {
            return Optional.empty();
        }
        return TenantContext.callWithTenantDatabase(null, () -> resolveCredentialsForUserInternal(user));
    }

    private Optional<UpbitAuthCredentials> resolveCredentialsForUserInternal(UserEntity user) {
        Optional<UserExchangeCredentialEntity> storedOptional = credentialRepository.findById(user.getId());
        if (storedOptional.isPresent()) {
            UserExchangeCredentialEntity stored = storedOptional.get();
            String accessKey = normalizeSecret(credentialCryptoService.decrypt(stored.getAccessKeyEncrypted()));
            String secretKey = normalizeSecret(credentialCryptoService.decrypt(stored.getSecretKeyEncrypted()));
            if (accessKey != null && secretKey != null) {
                return Optional.of(new UpbitAuthCredentials(accessKey, secretKey));
            }
        }
        if (isOwner(user) && defaultUpbitCredentials.isConfigured()) {
            return defaultUpbitCredentials.toAuthCredentials();
        }
        return Optional.empty();
    }

    private TenantTradingPrincipalResolution resolveTradingPrincipalForTenantInternal(String tenantDatabase) {
        if (tenantDatabase == null || tenantDatabase.isBlank()) {
            return TenantTradingPrincipalResolution.noTenant();
        }
        List<UserEntity> users = userRepository.findAllByTenantDatabaseOrderByIdAsc(tenantDatabase.trim());
        if (users.isEmpty()) {
            return TenantTradingPrincipalResolution.noUser(tenantDatabase);
        }
        if (isSystemTenant(tenantDatabase)) {
            return resolveSystemTenantPrincipal(tenantDatabase, users);
        }

        List<UserEntity> approvedUsers = users.stream()
                .filter(user -> TradingApprovalStatus.from(user.getTradingApprovalStatus()) == TradingApprovalStatus.APPROVED)
                .toList();
        if (approvedUsers.isEmpty()) {
            return TenantTradingPrincipalResolution.notApproved(tenantDatabase, toUserIds(users));
        }

        List<TenantCandidate> candidates = new ArrayList<>();
        for (UserEntity approvedUser : approvedUsers) {
            try {
                Optional<UpbitAuthCredentials> credentials = resolveCredentialsForUserInternal(approvedUser);
                credentials.ifPresent(upbitAuthCredentials -> candidates.add(new TenantCandidate(approvedUser, upbitAuthCredentials)));
            } catch (RuntimeException ex) {
                Long userId = approvedUser == null ? null : approvedUser.getId();
                log.warn("Failed to resolve credentials for tenant {} user {}: {}", tenantDatabase, userId, ex.getMessage());
            }
        }

        if (candidates.isEmpty()) {
            return TenantTradingPrincipalResolution.missingCredentials(tenantDatabase, toUserIds(approvedUsers));
        }
        if (candidates.size() > 1) {
            return TenantTradingPrincipalResolution.multipleCandidates(tenantDatabase, toCandidateUserIds(candidates));
        }

        TenantCandidate candidate = candidates.get(0);
        return TenantTradingPrincipalResolution.ready(
                tenantDatabase,
                candidate.user(),
                candidate.credentials(),
                candidate.user() == null || candidate.user().getId() == null
                        ? List.of()
                        : List.of(candidate.user().getId())
        );
    }

    private TenantTradingPrincipalResolution resolveSystemTenantPrincipal(String tenantDatabase, List<UserEntity> users) {
        UserEntity ownerUser = users.stream()
                .filter(this::isOwner)
                .findFirst()
                .orElse(null);
        if (ownerUser == null) {
            return TenantTradingPrincipalResolution.noUser(tenantDatabase);
        }

        Long ownerId = ownerUser.getId();
        List<Long> ownerIds = ownerId == null ? List.of() : List.of(ownerId);
        TradingApprovalStatus ownerApproval = TradingApprovalStatus.from(ownerUser.getTradingApprovalStatus());
        if (ownerApproval != TradingApprovalStatus.APPROVED) {
            return TenantTradingPrincipalResolution.notApproved(tenantDatabase, ownerIds);
        }

        Optional<UpbitAuthCredentials> credentials;
        try {
            credentials = resolveCredentialsForUserInternal(ownerUser);
        } catch (RuntimeException ex) {
            log.warn(
                    "Failed to resolve owner credentials for system tenant {} user {}: {}",
                    tenantDatabase,
                    ownerId,
                    ex.getMessage()
            );
            credentials = Optional.empty();
        }
        if (credentials.isEmpty()) {
            return TenantTradingPrincipalResolution.missingCredentials(tenantDatabase, ownerIds);
        }
        return TenantTradingPrincipalResolution.ready(tenantDatabase, ownerUser, credentials.get(), ownerIds);
    }

    private boolean isSystemTenant(String tenantDatabase) {
        if (tenantDatabase == null || tenantDatabase.isBlank()) {
            return false;
        }
        if (systemTenantDatabase == null || systemTenantDatabase.isBlank()) {
            return false;
        }
        return tenantDatabase.trim().equals(systemTenantDatabase.trim());
    }

    private boolean isOwner(UserEntity user) {
        if (user == null || ownerEmail.isBlank()) {
            return false;
        }
        String email = user.getEmail();
        if (email == null || email.isBlank()) {
            return false;
        }
        return ownerEmail.equals(email.trim().toLowerCase(Locale.ROOT));
    }

    private static String normalizeSecret(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static List<Long> toUserIds(List<UserEntity> users) {
        if (users == null || users.isEmpty()) {
            return List.of();
        }
        return users.stream()
                .map(UserEntity::getId)
                .toList();
    }

    private static List<Long> toCandidateUserIds(List<TenantCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
                .map(candidate -> candidate.user() == null ? null : candidate.user().getId())
                .toList();
    }

    private record TenantCandidate(UserEntity user, UpbitAuthCredentials credentials) {
    }

    public enum TenantTradingPrincipalStatus {
        NO_TENANT,
        NO_USER,
        NOT_APPROVED,
        MISSING_CREDENTIALS,
        MULTIPLE_CANDIDATES,
        READY
    }

    public record TenantTradingPrincipalResolution(
            TenantTradingPrincipalStatus status,
            String tenantDatabase,
            Optional<UserEntity> user,
            Optional<UpbitAuthCredentials> credentials,
            List<Long> candidateUserIds
    ) {
        static TenantTradingPrincipalResolution noTenant() {
            return new TenantTradingPrincipalResolution(
                    TenantTradingPrincipalStatus.NO_TENANT,
                    null,
                    Optional.empty(),
                    Optional.empty(),
                    List.of()
            );
        }

        static TenantTradingPrincipalResolution noUser(String tenantDatabase) {
            return new TenantTradingPrincipalResolution(
                    TenantTradingPrincipalStatus.NO_USER,
                    tenantDatabase,
                    Optional.empty(),
                    Optional.empty(),
                    List.of()
            );
        }

        static TenantTradingPrincipalResolution notApproved(String tenantDatabase, List<Long> userIds) {
            return new TenantTradingPrincipalResolution(
                    TenantTradingPrincipalStatus.NOT_APPROVED,
                    tenantDatabase,
                    Optional.empty(),
                    Optional.empty(),
                    userIds == null ? List.of() : List.copyOf(userIds)
            );
        }

        static TenantTradingPrincipalResolution missingCredentials(String tenantDatabase, List<Long> userIds) {
            return new TenantTradingPrincipalResolution(
                    TenantTradingPrincipalStatus.MISSING_CREDENTIALS,
                    tenantDatabase,
                    Optional.empty(),
                    Optional.empty(),
                    userIds == null ? List.of() : List.copyOf(userIds)
            );
        }

        static TenantTradingPrincipalResolution multipleCandidates(String tenantDatabase, List<Long> userIds) {
            return new TenantTradingPrincipalResolution(
                    TenantTradingPrincipalStatus.MULTIPLE_CANDIDATES,
                    tenantDatabase,
                    Optional.empty(),
                    Optional.empty(),
                    userIds == null ? List.of() : List.copyOf(userIds)
            );
        }

        static TenantTradingPrincipalResolution ready(
                String tenantDatabase,
                UserEntity user,
                UpbitAuthCredentials credentials,
                List<Long> userIds
        ) {
            return new TenantTradingPrincipalResolution(
                    TenantTradingPrincipalStatus.READY,
                    tenantDatabase,
                    Optional.ofNullable(user),
                    Optional.ofNullable(credentials),
                    userIds == null ? List.of() : List.copyOf(userIds)
            );
        }
    }
}
