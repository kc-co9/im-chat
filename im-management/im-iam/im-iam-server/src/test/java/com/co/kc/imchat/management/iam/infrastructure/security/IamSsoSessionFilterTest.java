package com.co.kc.imchat.management.iam.infrastructure.security;

import com.co.kc.imchat.management.iam.infrastructure.security.session.IamSsoSessionFilter;
import com.co.kc.imchat.management.iam.infrastructure.config.properties.IamAuthorizationProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

class IamSsoSessionFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void appliesEightHourIdleTimeoutToAnActiveSession() throws Exception {
        HttpSession session = mock(HttpSession.class);
        when(session.getCreationTime()).thenReturn(Instant.now().minus(Duration.ofHours(1)).toEpochMilli());
        execute(session);
        verify(session).setMaxInactiveInterval(Math.toIntExact(Duration.ofHours(8).toSeconds()));
    }

    @Test
    void invalidatesASessionAfterTheAbsoluteLifetime() throws Exception {
        HttpSession session = mock(HttpSession.class);
        when(session.getCreationTime()).thenReturn(Instant.now().minus(Duration.ofHours(25)).toEpochMilli());
        execute(session);
        verify(session).invalidate();
    }

    @Test
    void clearsAuthenticationBeforeContinuingAnExpiredRequest() throws Exception {
        HttpSession session = mock(HttpSession.class);
        when(session.getCreationTime())
                .thenReturn(Instant.now().minus(Duration.ofHours(25)).toEpochMilli());
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getSession(false)).thenReturn(session);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        "administrator", "N/A", java.util.List.of()));
        FilterChain chain = (ignoredRequest, ignoredResponse) ->
                assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        IamAuthorizationProperties properties = new IamAuthorizationProperties(
                URI.create("https://iam.example.com"), "file:/tmp/iam.p12", "store-password",
                "iam", "key-password", Duration.ofHours(8), Duration.ofHours(24));

        new IamSsoSessionFilter(properties).doFilter(request, response, chain);

        verify(session).invalidate();
    }

    private void execute(HttpSession session) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getSession(false)).thenReturn(session);
        IamAuthorizationProperties properties = new IamAuthorizationProperties(
                URI.create("https://iam.example.com"), "file:/tmp/iam.p12", "store-password",
                "iam", "key-password", Duration.ofHours(8), Duration.ofHours(24));
        new IamSsoSessionFilter(properties).doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }
}
