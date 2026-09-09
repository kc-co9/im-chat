package com.co.kc.imchat.plugin.session.web;

import com.co.kc.imchat.plugin.session.context.UserContextHeaders;
import com.co.kc.imchat.plugin.session.context.UserContextUtils;
import com.co.kc.imchat.plugin.session.properties.SessionWebProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserContextInterceptorTest {

    @AfterEach
    void tearDown() {
        UserContextUtils.remove();
    }

    @Test
    void setsUserContextFromTrustedGatewayHeader() {
        UserContextInterceptor interceptor = interceptor(List.of());
        MockHttpServletRequest request = protectedRequest();
        request.addHeader(UserContextHeaders.USER_ID, "42");
        request.addHeader(UserContextHeaders.SESSION_VERSION, "session-v2");

        boolean proceed = interceptor.preHandle(
                request, new MockHttpServletResponse(), new Object());

        assertThat(proceed).isTrue();
        assertThat(UserContextUtils.get().getUserId()).isEqualTo(42L);
        assertThat(UserContextUtils.get().getSessionVersion()).isEqualTo("session-v2");
    }

    @Test
    void rejectsProtectedRequestWithoutCompleteIdentityHeaders() {
        UserContextInterceptor interceptor = interceptor(List.of());
        MockHttpServletRequest request = protectedRequest();
        request.addHeader(UserContextHeaders.USER_ID, "42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(UserContextUtils.get()).isNull();
    }

    @Test
    void rejectsInvalidUserIdWithoutEstablishingContext() {
        UserContextInterceptor interceptor = interceptor(List.of());
        MockHttpServletRequest request = protectedRequest();
        request.addHeader(UserContextHeaders.USER_ID, "invalid");
        request.addHeader(UserContextHeaders.SESSION_VERSION, "session-v2");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(UserContextUtils.get()).isNull();
    }

    @Test
    void permitsOnlyConfiguredBusinessPublicPath() {
        UserContextInterceptor interceptor = interceptor(List.of("/user/signIn"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContextPath("/account");
        request.setRequestURI("/account/user/signIn");
        request.setServletPath("/user/signIn");

        boolean proceed = interceptor.preHandle(
                request, new MockHttpServletResponse(), new Object());

        assertThat(proceed).isTrue();
        assertThat(UserContextUtils.get()).isNull();
    }

    @Test
    void permitsConfiguredPublicPathPattern() {
        UserContextInterceptor interceptor = interceptor(List.of("/public/**"));
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/public/status");

        assertThat(interceptor.preHandle(
                request, new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    void clearsUserContextAfterCompletion() {
        UserContextInterceptor interceptor = interceptor(List.of());
        MockHttpServletRequest request = protectedRequest();
        request.addHeader(UserContextHeaders.USER_ID, "42");
        request.addHeader(UserContextHeaders.SESSION_VERSION, "session-v2");

        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
        interceptor.afterCompletion(
                request, new MockHttpServletResponse(), new Object(), null);

        assertThat(UserContextUtils.get()).isNull();
    }

    private UserContextInterceptor interceptor(List<String> publicPaths) {
        SessionWebProperties properties = new SessionWebProperties();
        properties.setPublicPaths(publicPaths);
        return new UserContextInterceptor(properties);
    }

    private MockHttpServletRequest protectedRequest() {
        return new MockHttpServletRequest("GET", "/friend/friendList");
    }
}
