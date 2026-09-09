package com.co.kc.imchat.management.iam.sdk.security;

import com.co.kc.imchat.common.constant.HttpErrorCode;
import com.co.kc.imchat.management.iam.sdk.introspection.IamIntrospectionService;
import com.co.kc.imchat.management.iam.sdk.introspection.model.IamIntrospectionResult;
import com.co.kc.imchat.management.iam.sdk.oauth.model.IamTokenSet;
import com.co.kc.imchat.management.iam.sdk.session.model.IamApplicationSession;
import com.co.kc.imchat.management.iam.sdk.session.repository.IamApplicationSessionRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IamSecurityFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void skipsSessionLookupForPublicBrowserRequests() throws Exception {
        IamSessionCookie cookie = new IamSessionCookie(
                "IM_IAM_SESSION", false, "Lax", Duration.ofHours(8));
        IamApplicationSessionRepository sessions = mock(IamApplicationSessionRepository.class);
        IamIntrospectionService introspection = mock(IamIntrospectionService.class);
        IamSecurityFilter filter = new IamSecurityFilter(cookie, sessions, introspection);
        AtomicInteger invocations = new AtomicInteger();

        for (String path : new String[]{
                "/", "/index.html", "/favicon.ico", "/assets/app.js",
                "/error", "/iam/login", "/iam/callback"
        }) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            request.setCookies(new jakarta.servlet.http.Cookie("IM_IAM_SESSION", "stale-session"));
            filter.doFilter(
                    request,
                    new MockHttpServletResponse(),
                    (servletRequest, servletResponse) -> invocations.incrementAndGet());
        }

        assertThat(invocations).hasValue(7);
        verifyNoInteractions(sessions, introspection);
    }

    @Test
    void authenticatesOneRequestAndAlwaysClearsSecurityContext() throws Exception {
        IamSessionCookie cookie = new IamSessionCookie(
                "IM_IAM_SESSION", false, "Lax", Duration.ofHours(8));
        IamApplicationSessionRepository sessions = mock(IamApplicationSessionRepository.class);
        IamIntrospectionService introspection = mock(IamIntrospectionService.class);
        when(sessions.find("session-id")).thenReturn(Optional.of(session()));
        when(introspection.introspect("opaque-access-token")).thenReturn(
                new IamIntrospectionResult(
                        true, 1L, "root", "imAdmin", "im-admin-client",
                        Set.of("imAdmin"),
                        Set.of("admin.user.read"), Instant.now().plusSeconds(900)));
        IamSecurityFilter filter = new IamSecurityFilter(cookie, sessions, introspection);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new jakarta.servlet.http.Cookie("IM_IAM_SESSION", "session-id"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {
            assertThat(IamSecurityContext.currentPrincipal()).isPresent();
            assertThat(IamSecurityContext.currentPrincipal().orElseThrow().authorities())
                    .containsExactly("admin.user.read");
        };

        filter.doFilter(request, response, chain);

        assertThat(IamSecurityContext.currentPrincipal()).isEmpty();
        verify(introspection).introspect("opaque-access-token");
    }

    @Test
    void writesInactiveSessionAsHttpResult() throws Exception {
        IamSessionCookie cookie = new IamSessionCookie(
                "IM_IAM_SESSION", false, "Lax", Duration.ofHours(8));
        IamApplicationSessionRepository sessions = mock(IamApplicationSessionRepository.class);
        IamIntrospectionService introspection = mock(IamIntrospectionService.class);
        when(sessions.find("session-id")).thenReturn(Optional.of(session()));
        when(introspection.introspect("opaque-access-token")).thenReturn(
                new IamIntrospectionResult(
                        false, null, null, null, null,
                        Set.of(), Set.of(), Instant.now().plusSeconds(900)));
        IamSecurityFilter filter = new IamSecurityFilter(cookie, sessions, introspection);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new jakarta.servlet.http.Cookie("IM_IAM_SESSION", "session-id"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            throw new AssertionError("inactive session must not reach the filter chain");
        });

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString())
                .contains("\"code\":" + HttpErrorCode.AUTH_FAIL.getCode());
    }

    private static IamApplicationSession session() {
        Instant now = Instant.parse("2026-08-26T08:00:00Z");
        return new IamApplicationSession(
                "session-id",
                new IamTokenSet(
                        "opaque-access-token", now.plusSeconds(900),
                        "opaque-refresh-token", now.plusSeconds(28_800)),
                "csrf-token",
                now,
                now);
    }
}
