package com.btcautotrader.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {
    private final AuthRedirectUrlResolver authRedirectUrlResolver;
    private final String failureRedirectUrl;

    public OAuth2LoginFailureHandler(
            AuthRedirectUrlResolver authRedirectUrlResolver,
            @Value("${app.auth.failure-redirect-url:/?loginError=true}") String failureRedirectUrl
    ) {
        this.authRedirectUrlResolver = authRedirectUrlResolver;
        this.failureRedirectUrl = failureRedirectUrl;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        response.sendRedirect(authRedirectUrlResolver.resolve(request, failureRedirectUrl));
    }
}
