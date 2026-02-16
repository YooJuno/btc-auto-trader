package com.btcautotrader.auth;

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

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
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
        return userRepository.save(entity);
    }

    @Transactional
    public UserEntity requireUser(Authentication authentication) {
        Identity identity = resolveIdentity(authentication);

        return userRepository.findByProviderAndProviderUserId(identity.provider(), identity.providerUserId())
                .orElseGet(() -> {
                    UserEntity created = new UserEntity();
                    created.setProvider(identity.provider());
                    created.setProviderUserId(identity.providerUserId());
                    created.setEmail(identity.email());
                    created.setDisplayName(identity.displayName());
                    created.setLastLoginAt(OffsetDateTime.now());
                    return userRepository.save(created);
                });
    }

    @Transactional(readOnly = true)
    public Optional<UserEntity> findByAuthentication(Authentication authentication) {
        Identity identity = resolveIdentity(authentication);
        return userRepository.findByProviderAndProviderUserId(identity.provider(), identity.providerUserId());
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
