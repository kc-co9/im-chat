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

        boolean proceed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(proceed).isTrue();
        assertThat(UserContextUtils.get().getUserId()).isEqualTo(42L);
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
    void permitsSignInWithoutUserHeader() {
        UserContextInterceptor interceptor = new UserContextInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/user/signIn");

        boolean proceed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(proceed).isTrue();
        assertThat(UserContextUtils.get()).isNull();
    }

    @Test
    void clearsUserContextAfterCompletion() {
        UserContextInterceptor interceptor = new UserContextInterceptor();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/friend/friendList");
        request.addHeader(UserContextHeaders.USER_ID, "42");

        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
        interceptor.afterCompletion(request, new MockHttpServletResponse(), new Object(), null);

        assertThat(UserContextUtils.get()).isNull();
    }
}
