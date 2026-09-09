package com.co.kc.imchat.plugin.dubbo.filter;

import com.co.kc.imchat.plugin.dubbo.security.RpcTokenAuthenticator;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RpcTokenProviderFilterTest {
    private static final String ACCESS_TOKEN_ATTACHMENT = "im-rpc-access-token";

    @AfterEach
    void clearContexts() {
        RpcContext.getServerAttachment().clearAttachments();
        SecurityContextHolder.clearContext();
    }

    @Test
    void establishesAuthenticationOnlyForCurrentInvocation() {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                "imAdmin",
                null,
                List.of());
        RpcTokenAuthenticator authenticator = token -> authentication;
        RpcTokenProviderFilter filter = new RpcTokenProviderFilter();
        filter.setTokenAuthenticator(authenticator);
        RpcContext.getServerAttachment().setAttachment(
                ACCESS_TOKEN_ATTACHMENT,
                "opaque-access-token");
        Invocation invocation = mock(Invocation.class);
        Invoker<?> invoker = mock(Invoker.class);
        Result result = mock(Result.class);
        when(invoker.invoke(invocation)).thenAnswer(ignored -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication())
                    .isSameAs(authentication);
            return result;
        });

        Result actual = filter.invoke(invoker, invocation);

        assertThat(actual).isSameAs(result);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void restoresPreviousSecurityContextWhenInvocationFails() {
        SecurityContext previous = SecurityContextHolder.createEmptyContext();
        Authentication previousAuthentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        "previous",
                        null,
                        List.of());
        previous.setAuthentication(previousAuthentication);
        SecurityContextHolder.setContext(previous);
        RpcContext.getServerAttachment().setAttachment(
                ACCESS_TOKEN_ATTACHMENT,
                "opaque-access-token");
        RpcTokenProviderFilter filter = new RpcTokenProviderFilter();
        filter.setTokenAuthenticator(token ->
                UsernamePasswordAuthenticationToken.authenticated(
                        "current",
                        null,
                        List.of()));
        Invocation invocation = mock(Invocation.class);
        Invoker<?> invoker = mock(Invoker.class);
        when(invoker.invoke(invocation)).thenThrow(new IllegalStateException("expected"));

        assertThatThrownBy(() -> filter.invoke(invoker, invocation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("expected");
        assertThat(SecurityContextHolder.getContext()).isSameAs(previous);
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isSameAs(previousAuthentication);
    }

    @Test
    void rejectsMissingAccessTokenBeforeBusinessInvocation() {
        RpcTokenAuthenticator authenticator = mock(RpcTokenAuthenticator.class);
        RpcTokenProviderFilter filter = new RpcTokenProviderFilter();
        filter.setTokenAuthenticator(authenticator);
        Invoker<?> invoker = mock(Invoker.class);
        Invocation invocation = mock(Invocation.class);

        assertThatThrownBy(() -> filter.invoke(invoker, invocation))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
                .hasMessageNotContaining("opaque-access-token");
        verify(authenticator, never()).authenticate(org.mockito.ArgumentMatchers.any());
        verify(invoker, never()).invoke(invocation);
    }
}
