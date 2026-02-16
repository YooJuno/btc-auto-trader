package com.btcautotrader.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider;
    private final CurrentUserService currentUserService;
    private final UserSettingsService userSettingsService;

    public AuthController(
            ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider,
            CurrentUserService currentUserService,
            UserSettingsService userSettingsService
    ) {
        this.clientRegistrationRepositoryProvider = clientRegistrationRepositoryProvider;
        this.currentUserService = currentUserService;
        this.userSettingsService = userSettingsService;
    }

    @GetMapping("/auth/providers")
    public ResponseEntity<List<AuthProviderResponse>> getAuthProviders(HttpServletRequest request) {
        ClientRegistrationRepository repository = clientRegistrationRepositoryProvider.getIfAvailable();
        if (!(repository instanceof Iterable<?> iterable)) {
            return ResponseEntity.ok(List.of());
        }

        String authBaseUrl = resolveAuthBaseUrl(request);
        List<AuthProviderResponse> providers = new ArrayList<>();
        for (Object item : iterable) {
            if (!(item instanceof ClientRegistration registration)) {
                continue;
            }
            String id = registration.getRegistrationId();
            providers.add(new AuthProviderResponse(
                    id,
                    registration.getClientName(),
                    authBaseUrl + "/oauth2/authorization/" + id
            ));
        }

        providers.sort(Comparator.comparing(AuthProviderResponse::name));
        return ResponseEntity.ok(providers);
    }

    private static String resolveAuthBaseUrl(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();

        if (host == null || host.isBlank()) {
            return "";
        }
        if (("http".equalsIgnoreCase(scheme) && port == 80) || ("https".equalsIgnoreCase(scheme) && port == 443)) {
            return scheme + "://" + host;
        }
        return scheme + "://" + host + ":" + port;
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Map<String, Object>> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }

        SecurityContextHolder.clearContext();

        Cookie cookie = new Cookie("JSESSIONID", "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> getMe(Authentication authentication) {
        UserEntity user = currentUserService.requireUser(authentication);
        return ResponseEntity.ok(MeResponse.from(user));
    }

    @GetMapping("/me/settings")
    public ResponseEntity<UserSettingsResponse> getMySettings(Authentication authentication) {
        UserEntity user = currentUserService.requireUser(authentication);
        return ResponseEntity.ok(userSettingsService.getSettings(user.getId()));
    }

    @PutMapping("/me/settings")
    public ResponseEntity<?> updateMySettings(
            Authentication authentication,
            @RequestBody(required = false) UserSettingsRequest request
    ) {
        UserEntity user = currentUserService.requireUser(authentication);

        try {
            UserSettingsResponse response = userSettingsService.updateSettings(user.getId(), request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", ex.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
