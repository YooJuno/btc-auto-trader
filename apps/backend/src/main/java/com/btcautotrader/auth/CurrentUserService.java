package com.btcautotrader.auth;

import com.btcautotrader.tenant.TenantDatabaseProvisioningService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class CurrentUserService {
    private static final List<String> SUBJECT_KEYS = List.of("sub", "id", "user_id", "uid");
    private static final List<String> NAME_KEYS = List.of("name", "nickname", "login", "preferred_username");

    private final UserRepository userRepository;
    private final TenantDatabaseProvisioningService tenantDatabaseProvisioningService;
    private final String ownerEmail;

    public CurrentUserService(
            UserRepository userRepository,
            TenantDatabaseProvisioningService tenantDatabaseProvisioningService,
            @Value("${app.multi-tenant.owner-email:juno980220@gmail.com}") String ownerEmail
    ) {
        this.userRepository = userRepository;
        this.tenantDatabaseProvisioningService = tenantDatabaseProvisioningService;
        this.ownerEmail = ownerEmail == null ? "" : ownerEmail.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public UserEntity upsertFromAuthentication(Authentication authentication) {
        Identity identity = resolveIdentity(authentication);

        UserEntity entity = userRepository.findByProviderAndProviderUserId(identity.provider(), identity.providerUserId())
                .orElseGet(() -> {
                    UserEntity created = new UserEntity();
                    created.setProvider(identity.provider());
                    created.setProviderUserId(identity.providerUserId());
                    return created;
                });

        entity.setEmail(identity.email());
        entity.setDisplayName(identity.displayName());
        entity.setLastLoginAt(OffsetDateTime.now());
        applyApprovalDefaults(entity);
        UserEntity saved = userRepository.save(entity);
        return tenantDatabaseProvisioningService.ensureTenant(saved);
    }

    @Transactional
    public UserEntity requireUser(Authentication authentication) {
        Identity identity = resolveIdentity(authentication);

        UserEntity resolved = userRepository.findByProviderAndProviderUserId(identity.provider(), identity.providerUserId())
                .orElseGet(() -> {
                    UserEntity created = new UserEntity();
                    created.setProvider(identity.provider());
                    created.setProviderUserId(identity.providerUserId());
                    created.setEmail(identity.email());
                    created.setDisplayName(identity.displayName());
                    created.setLastLoginAt(OffsetDateTime.now());
                    applyApprovalDefaults(created);
                    return userRepository.save(created);
                });
        boolean approvalNeedsSave = resolved.getTradingApprovalStatus() == null
                || resolved.getTradingApprovalStatus().isBlank()
                || resolved.getTradingApprovalUpdatedAt() == null
                || (isOwner(resolved)
                && TradingApprovalStatus.from(resolved.getTradingApprovalStatus()) != TradingApprovalStatus.APPROVED);
        applyApprovalDefaults(resolved);
        if (approvalNeedsSave) {
            resolved = userRepository.save(resolved);
        }
        return tenantDatabaseProvisioningService.ensureTenant(resolved);
    }

    @Transactional(readOnly = true)
    public Optional<UserEntity> findByAuthentication(Authentication authentication) {
        Identity identity = resolveIdentity(authentication);
        return userRepository.findByProviderAndProviderUserId(identity.provider(), identity.providerUserId());
    }

    @Transactional(readOnly = true)
    public boolean isOwner(UserEntity user) {
        if (user == null || ownerEmail.isBlank()) {
            return false;
        }
        String email = user.getEmail();
        if (email == null || email.isBlank()) {
            return false;
        }
        return ownerEmail.equals(email.trim().toLowerCase(Locale.ROOT));
    }

    @Transactional(readOnly = true)
    public boolean isOwner(Authentication authentication) {
        return findByAuthentication(authentication)
                .map(this::isOwner)
                .orElse(false);
    }

    private void applyApprovalDefaults(UserEntity entity) {
        if (entity == null) {
            return;
        }
        TradingApprovalStatus current = TradingApprovalStatus.from(entity.getTradingApprovalStatus());
        if (current == TradingApprovalStatus.PENDING && isOwner(entity)) {
            entity.setTradingApprovalStatus(TradingApprovalStatus.APPROVED.name());
            if (entity.getTradingApprovalNote() == null || entity.getTradingApprovalNote().isBlank()) {
                entity.setTradingApprovalNote("owner auto approved");
            }
            entity.setTradingApprovalUpdatedAt(OffsetDateTime.now());
            return;
        }
        if (entity.getTradingApprovalStatus() == null || entity.getTradingApprovalStatus().isBlank()) {
            entity.setTradingApprovalStatus(TradingApprovalStatus.PENDING.name());
        }
        if (entity.getTradingApprovalUpdatedAt() == null) {
            entity.setTradingApprovalUpdatedAt(OffsetDateTime.now());
        }
    }

    private static Identity resolveIdentity(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken oauth2) || !oauth2.isAuthenticated()) {
            throw new IllegalStateException("authentication is required");
        }

        OAuth2User principal = oauth2.getPrincipal();
        if (principal == null) {
            throw new IllegalStateException("oauth2 principal is required");
        }

        Map<String, Object> attributes = principal.getAttributes();
        String provider = normalizeProvider(oauth2.getAuthorizedClientRegistrationId());
        String providerUserId = extractAttributeAsString(attributes, SUBJECT_KEYS);
        if (providerUserId == null) {
            throw new IllegalStateException("oauth2 subject is missing");
        }

        String email = extractAttributeAsString(attributes, List.of("email"));
        String displayName = extractAttributeAsString(attributes, NAME_KEYS);

        return new Identity(provider, providerUserId, email, displayName);
    }

    private static String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new IllegalStateException("oauth2 provider is missing");
        }
        return provider.trim().toLowerCase(Locale.ROOT);
    }

    private static String extractAttributeAsString(Map<String, Object> attributes, List<String> keys) {
        if (attributes == null || attributes.isEmpty()) {
            return null;
        }

        for (String key : keys) {
            Object value = attributes.get(key);
            String resolved = toStringValue(value);
            if (resolved != null) {
                return resolved;
            }
        }

        return null;
    }

    private static String toStringValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            if (text.isBlank()) {
                return null;
            }
            return text.trim();
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Object[] array) {
            return Arrays.stream(array)
                    .map(CurrentUserService::toStringValue)
                    .filter(item -> item != null && !item.isBlank())
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private record Identity(
            String provider,
            String providerUserId,
            String email,
            String displayName
    ) {
    }
}
