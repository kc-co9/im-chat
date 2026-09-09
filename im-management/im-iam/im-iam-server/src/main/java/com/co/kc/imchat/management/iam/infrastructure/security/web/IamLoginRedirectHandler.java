package com.co.kc.imchat.management.iam.infrastructure.security.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * 处理 IAM 管理员登录后的安全页面跳转。
 */
public class IamLoginRedirectHandler implements
        AuthenticationSuccessHandler,
        AuthenticationFailureHandler {
    private final SavedRequestAwareAuthenticationSuccessHandler savedRequestHandler =
            new SavedRequestAwareAuthenticationSuccessHandler();

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        String continuation = request.getParameter("continue");
        if (continuation == null) {
            savedRequestHandler.onAuthenticationSuccess(request, response, authentication);
            return;
        }
        response.sendRedirect(validContinuation(continuation));
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        UriComponentsBuilder locationBuilder = UriComponentsBuilder.fromPath("/login")
                .queryParam("error", "true");
        String continuation = request.getParameter("continue");
        if (continuation != null) {
            locationBuilder.queryParam("continue", validContinuation(continuation));
        }
        String location = locationBuilder.build().encode().toUriString();
        response.sendRedirect(location);
    }

    private String validContinuation(String continuation) {
        if ("/".equals(continuation)
                || continuation != null && continuation.startsWith("/#/")) {
            return continuation;
        }
        return "/";
    }
}
