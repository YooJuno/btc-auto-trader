package com.btcautotrader.security;

import com.btcautotrader.auth.CurrentUserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {
    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

    private final CurrentUserService currentUserService;
    private final AuthRedirectUrlResolver authRedirectUrlResolver;
    private final String successRedirectUrl;
    private final String failureRedirectUrl;

    public OAuth2LoginSuccessHandler(
            CurrentUserService currentUserService,
            AuthRedirectUrlResolver authRedirectUrlResolver,
            @Value("${app.auth.success-redirect-url:/}") String successRedirectUrl,
            @Value("${app.auth.failure-redirect-url:/?loginError=true}") String failureRedirectUrl
    ) {
        this.currentUserService = currentUserService;
        this.authRedirectUrlResolver = authRedirectUrlResolver;
        this.successRedirectUrl = successRedirectUrl;
        this.failureRedirectUrl = failureRedirectUrl;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        try {
            currentUserService.upsertFromAuthentication(authentication);
        } catch (RuntimeException ex) {
            log.error("OAuth2 login post-processing failed", ex);
            response.sendRedirect(authRedirectUrlResolver.resolve(request, failureRedirectUrl));
            return;
        }
        response.sendRedirect(authRedirectUrlResolver.resolve(request, successRedirectUrl));
    }
}
