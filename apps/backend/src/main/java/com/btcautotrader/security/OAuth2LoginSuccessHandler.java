package com.btcautotrader.security;

import com.btcautotrader.auth.CurrentUserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {
    private final CurrentUserService currentUserService;
    private final AuthRedirectUrlResolver authRedirectUrlResolver;
    private final String successRedirectUrl;

    public OAuth2LoginSuccessHandler(
            CurrentUserService currentUserService,
            AuthRedirectUrlResolver authRedirectUrlResolver,
            @Value("${app.auth.success-redirect-url:http://localhost:5173/}") String successRedirectUrl
    ) {
        this.currentUserService = currentUserService;
        this.authRedirectUrlResolver = authRedirectUrlResolver;
        this.successRedirectUrl = successRedirectUrl;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        currentUserService.upsertFromAuthentication(authentication);
        response.sendRedirect(authRedirectUrlResolver.resolve(request, successRedirectUrl));
    }
}
