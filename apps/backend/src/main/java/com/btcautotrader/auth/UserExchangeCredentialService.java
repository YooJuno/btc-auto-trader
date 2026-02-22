package com.btcautotrader.auth;

import com.btcautotrader.tenant.TenantContext;
import com.btcautotrader.tenant.TenantDataSourceProvider;
import com.btcautotrader.upbit.UpbitAuthCredentials;
import com.btcautotrader.upbit.UpbitCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;

@Service
public class UserExchangeCredentialService {
    private final UserExchangeCredentialRepository credentialRepository;
    private final UserRepository userRepository;
    private final CredentialCryptoService credentialCryptoService;
    private final UpbitCredentials defaultUpbitCredentials;
    private final String ownerEmail;
    private final String systemDatabaseName;

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
        this.systemDatabaseName = tenantDataSourceProvider.getSystemDatabaseName();
        this.ownerEmail = ownerEmail == null ? "" : ownerEmail.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    public Optional<UpbitAuthCredentials> resolveCredentialsForCurrentTenant() {
        String tenantDatabase = TenantContext.getTenantDatabase();
        if (tenantDatabase == null || tenantDatabase.isBlank()) {
            return Optional.empty();
        }
        return TenantContext.callWithTenantDatabase(null, () -> {
            Optional<UserEntity> userOptional = findUserByTenantDatabaseInternal(tenantDatabase.trim());
            if (userOptional.isEmpty()) {
                return Optional.empty();
            }
            return resolveCredentialsForUserInternal(userOptional.get());
        });
    }

    @Transactional(readOnly = true)
    public Optional<UserEntity> findUserForCurrentTenant() {
        String tenantDatabase = TenantContext.getTenantDatabase();
        if (tenantDatabase == null || tenantDatabase.isBlank()) {
            return Optional.empty();
        }
        return TenantContext.callWithTenantDatabase(null, () -> {
            return findUserByTenantDatabaseInternal(tenantDatabase.trim());
        });
    }

    @Transactional(readOnly = true)
    public boolean hasCredentialsForUser(UserEntity user) {
        return user != null && resolveCredentialsForUser(user).isPresent();
    }

    @Transactional(readOnly = true)
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

    private Optional<UserEntity> findUserByTenantDatabaseInternal(String tenantDatabase) {
        if (tenantDatabase == null || tenantDatabase.isBlank()) {
            return Optional.empty();
        }
        Optional<UserEntity> found = userRepository.findFirstByTenantDatabase(tenantDatabase);
        if (found.isPresent()) {
            return found;
        }
        if (tenantDatabase.equals(systemDatabaseName) && !ownerEmail.isBlank()) {
            return userRepository.findFirstByEmailIgnoreCase(ownerEmail);
        }
        return Optional.empty();
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
}
