package com.co.kc.imchat.plugin.web.session;

import com.co.kc.imchat.plugin.session.context.UserContextHeaders;
import com.co.kc.imchat.plugin.session.context.UserContextUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class UserContextInterceptorTest {

    @AfterEach
    void tearDown() {
        UserContextUtils.remove();
    }

    @Test
    void setsUserContextFromTrustedGatewayHeader() {
        UserContextInterceptor interceptor = new UserContextInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/friend/friendList");
        request.addHeader(UserContextHeaders.USER_ID, "42");
        request.addHeader(UserContextHeaders.SESSION_VERSION, "session-v2");

        boolean proceed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(proceed).isTrue();
        assertThat(UserContextUtils.get().getUserId()).isEqualTo(42L);
        assertThat(UserContextUtils.get().getSessionVersion()).isEqualTo("session-v2");
    }

    @Test
    void rejectsProtectedRequestWithoutUserHeader() {
        UserContextInterceptor interceptor = new UserContextInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/friend/friendList");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void rejectsProtectedRequestWithoutSessionVersionHeader() {
        UserContextInterceptor interceptor = new UserContextInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/friend/friendList");
        request.addHeader(UserContextHeaders.USER_ID, "42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(UserContextUtils.get()).isNull();
    }

    @Test
    void rejectsProtectedRequestWithBlankSessionVersionHeader() {
        UserContextInterceptor interceptor = new UserContextInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/friend/friendList");
        request.addHeader(UserContextHeaders.USER_ID, "42");
        request.addHeader(UserContextHeaders.SESSION_VERSION, "  ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(UserContextUtils.get()).isNull();
    }

    @Test
    void rejectsProtectedRequestWithInvalidUserIdAndDoesNotEstablishContext() {
        UserContextInterceptor interceptor = new UserContextInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/friend/friendList");
        request.addHeader(UserContextHeaders.USER_ID, "invalid");
        request.addHeader(UserContextHeaders.SESSION_VERSION, "session-v2");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(UserContextUtils.get()).isNull();
    }

    @Test
    void permitsSignInWithoutUserHeaderWhenServiceHasContextPath() {
        UserContextInterceptor interceptor = new UserContextInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContextPath("/account");
        request.setRequestURI("/account/user/signIn");
        request.setServletPath("/user/signIn");

        assertThat(request.getRequestURI()).isEqualTo("/account/user/signIn");
        assertThat(request.getServletPath()).isEqualTo("/user/signIn");

        boolean proceed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(proceed).isTrue();
        assertThat(UserContextUtils.get()).isNull();
    }

    @Test
    void permitsRefreshTokenWithoutUserHeaderWhenServiceHasContextPath() {
        UserContextInterceptor interceptor = new UserContextInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContextPath("/account");
        request.setRequestURI("/account/user/refreshToken");
        request.setServletPath("/user/refreshToken");

        boolean proceed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(proceed).isTrue();
        assertThat(UserContextUtils.get()).isNull();
    }

    @Test
    void clearsUserContextAfterCompletion() {
        UserContextInterceptor interceptor = new UserContextInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/friend/friendList");
        request.addHeader(UserContextHeaders.USER_ID, "42");
        request.addHeader(UserContextHeaders.SESSION_VERSION, "session-v2");

        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
        interceptor.afterCompletion(request, new MockHttpServletResponse(), new Object(), null);

        assertThat(UserContextUtils.get()).isNull();
    }
}
