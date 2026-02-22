package com.btcautotrader.auth;

import com.btcautotrader.upbit.UpbitApiException;
import com.btcautotrader.upbit.UpbitAuthCredentials;
import com.btcautotrader.upbit.UpbitService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final UserExchangeCredentialService userExchangeCredentialService;
    private final UpbitService upbitService;

    public AuthController(
            ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider,
            CurrentUserService currentUserService,
            UserSettingsService userSettingsService,
            UserExchangeCredentialService userExchangeCredentialService,
            UpbitService upbitService
    ) {
        this.clientRegistrationRepositoryProvider = clientRegistrationRepositoryProvider;
        this.currentUserService = currentUserService;
        this.userSettingsService = userSettingsService;
        this.userExchangeCredentialService = userExchangeCredentialService;
        this.upbitService = upbitService;
    }

    @GetMapping("/auth/providers")
    public ResponseEntity<List<AuthProviderResponse>> getAuthProviders() {
        ClientRegistrationRepository repository = clientRegistrationRepositoryProvider.getIfAvailable();
        if (!(repository instanceof Iterable<?> iterable)) {
            return ResponseEntity.ok(List.of());
        }

        List<AuthProviderResponse> providers = new ArrayList<>();
        for (Object item : iterable) {
            if (!(item instanceof ClientRegistration registration)) {
                continue;
            }
            String id = registration.getRegistrationId();
            providers.add(new AuthProviderResponse(
                    id,
                    registration.getClientName(),
                    "/oauth2/authorization/" + id
            ));
        }

        providers.sort(Comparator.comparing(AuthProviderResponse::name));
        return ResponseEntity.ok(providers);
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

    @GetMapping("/me/exchange-credentials")
    public ResponseEntity<UserExchangeCredentialStatusResponse> getExchangeCredentialStatus(Authentication authentication) {
        UserEntity user = currentUserService.requireUser(authentication);
        return ResponseEntity.ok(userExchangeCredentialService.getStatus(user));
    }

    @PutMapping("/me/exchange-credentials")
    public ResponseEntity<?> saveExchangeCredentials(
            Authentication authentication,
            @RequestBody(required = false) UserExchangeCredentialRequest request
    ) {
        UserEntity user = currentUserService.requireUser(authentication);
        try {
            UserExchangeCredentialStatusResponse response = userExchangeCredentialService.save(user, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", ex.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @DeleteMapping("/me/exchange-credentials")
    public ResponseEntity<Map<String, Object>> deleteExchangeCredentials(Authentication authentication) {
        UserEntity user = currentUserService.requireUser(authentication);
        userExchangeCredentialService.delete(user);

        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/me/exchange-credentials/verify")
    public ResponseEntity<?> verifyExchangeCredentials(Authentication authentication) {
        UserEntity user = currentUserService.requireUser(authentication);
        UserExchangeCredentialStatusResponse status = userExchangeCredentialService.getStatus(user);
        UpbitAuthCredentials credentials = userExchangeCredentialService.resolveCredentialsForUser(user).orElse(null);
        if (credentials == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "거래소 API 키가 등록되어 있지 않습니다.");
            return ResponseEntity.badRequest().body(error);
        }

        try {
            int accountCount = upbitService.verifyCredentials(credentials);
            return ResponseEntity.ok(new UserExchangeCredentialVerifyResponse(
                    true,
                    accountCount,
                    status.usingDefaultCredentials(),
                    java.time.OffsetDateTime.now()
            ));
        } catch (UpbitApiException ex) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "거래소 API 키 검증 실패");
            error.put("status", ex.getStatusCode());
            if (ex.getResponseBody() != null && !ex.getResponseBody().isBlank()) {
                error.put("details", ex.getResponseBody());
            }
            return ResponseEntity.status(ex.getStatusCode()).body(error);
        }
    }
}
