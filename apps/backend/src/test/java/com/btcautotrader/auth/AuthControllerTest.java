package com.btcautotrader.auth;

import com.btcautotrader.feature.FeatureFlagService;
import com.btcautotrader.upbit.UpbitService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AuthControllerTest {

    @Test
    void logout_setsHttpOnlyClearingCookie_withSecureDisabled() {
        AuthController controller = newController(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<Map<String, Object>> result = controller.logout(request, response);

        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(result.getBody()).containsEntry("ok", true);
        Cookie cookie = response.getCookie("JSESSIONID");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getMaxAge()).isZero();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isFalse();
    }

    @Test
    void logout_setsHttpOnlyClearingCookie_withSecureEnabled() {
        AuthController controller = newController(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.logout(request, response);

        Cookie cookie = response.getCookie("JSESSIONID");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isTrue();
    }

    @SuppressWarnings("unchecked")
    private static AuthController newController(boolean secureCookie) {
        ObjectProvider<ClientRegistrationRepository> repositoryProvider = mock(ObjectProvider.class);
        return new AuthController(
                repositoryProvider,
                mock(CurrentUserService.class),
                mock(UserSettingsService.class),
                mock(UserExchangeCredentialService.class),
                mock(FeatureFlagService.class),
                mock(UpbitService.class),
                secureCookie
        );
    }
}
