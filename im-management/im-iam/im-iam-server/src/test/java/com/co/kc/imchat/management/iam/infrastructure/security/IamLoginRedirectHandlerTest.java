package com.co.kc.imchat.management.iam.infrastructure.security;

import com.co.kc.imchat.management.iam.infrastructure.security.web.IamLoginRedirectHandler;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IamLoginRedirectHandlerTest {

    @Test
    void returnsSuccessfulLoginToValidatedSpaRoute() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("continue", "/#/permissions/administrators");
        MockHttpServletResponse response = new MockHttpServletResponse();
        IamLoginRedirectHandler handler = new IamLoginRedirectHandler();

        handler.onAuthenticationSuccess(
                request,
                response,
                UsernamePasswordAuthenticationToken.authenticated(
                        "1",
                        null,
                        List.of()));

        assertThat(response.getRedirectedUrl()).isEqualTo("/#/permissions/administrators");
    }

    @Test
    void rejectsExternalSuccessfulLoginContinuation() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("continue", "//evil.example/steal");
        MockHttpServletResponse response = new MockHttpServletResponse();
        IamLoginRedirectHandler handler = new IamLoginRedirectHandler();

        handler.onAuthenticationSuccess(
                request,
                response,
                UsernamePasswordAuthenticationToken.authenticated(
                        "1",
                        null,
                        List.of()));

        assertThat(response.getRedirectedUrl()).isEqualTo("/");
    }

    @Test
    void resumesSavedOAuthAuthorizationRequestWhenContinuationIsAbsent() throws Exception {
        MockHttpServletRequest authorizationRequest = new MockHttpServletRequest(
                "GET",
                "/oauth2/authorize");
        authorizationRequest.setQueryString("client_id=im-audit-client");
        MockHttpServletResponse response = new MockHttpServletResponse();
        new HttpSessionRequestCache().saveRequest(authorizationRequest, response);
        IamLoginRedirectHandler handler = new IamLoginRedirectHandler();

        handler.onAuthenticationSuccess(
                authorizationRequest,
                response,
                UsernamePasswordAuthenticationToken.authenticated(
                        "1",
                        null,
                        List.of()));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost/oauth2/authorize?client_id=im-audit-client&continue");
    }

    @Test
    void preservesValidatedContinuationAfterAuthenticationFailure() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("continue", "/#/permissions/applications");
        MockHttpServletResponse response = new MockHttpServletResponse();
        IamLoginRedirectHandler handler = new IamLoginRedirectHandler();

        handler.onAuthenticationFailure(
                request,
                response,
                new BadCredentialsException("bad credentials"));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("/login?error=true&continue=/%23/permissions/applications");
    }

    @Test
    void preservesSavedOAuthRequestAfterAuthenticationFailure() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        IamLoginRedirectHandler handler = new IamLoginRedirectHandler();

        handler.onAuthenticationFailure(
                request,
                response,
                new BadCredentialsException("bad credentials"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=true");
    }
}
