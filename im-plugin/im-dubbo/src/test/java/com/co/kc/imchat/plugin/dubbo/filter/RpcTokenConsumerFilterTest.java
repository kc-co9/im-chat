package com.co.kc.imchat.plugin.dubbo.filter;

import com.co.kc.imchat.plugin.dubbo.security.RpcAccessTokenProvider;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RpcTokenConsumerFilterTest {
    private static final String ACCESS_TOKEN_ATTACHMENT = "im-rpc-access-token";

    @AfterEach
    void clearRpcContext() {
        RpcContext.getClientAttachment().clearAttachments();
    }

    @Test
    void writesAccessTokenOnlyToAttachmentForCurrentInvocation() {
        RpcAccessTokenProvider tokenProvider = () -> "opaque-access-token";
        RpcTokenConsumerFilter filter = new RpcTokenConsumerFilter();
        filter.setAccessTokenProvider(tokenProvider);
        Invocation invocation = mock(Invocation.class);
        Invoker<?> invoker = mock(Invoker.class);
        Result result = mock(Result.class);
        when(invoker.invoke(invocation)).thenAnswer(ignored -> {
            assertThat(RpcContext.getClientAttachment()
                    .getAttachment(ACCESS_TOKEN_ATTACHMENT))
                    .isEqualTo("opaque-access-token");
            return result;
        });

        Result actual = filter.invoke(invoker, invocation);

        assertThat(actual).isSameAs(result);
        assertThat(RpcContext.getClientAttachment()
                .getAttachment(ACCESS_TOKEN_ATTACHMENT))
                .isNull();
        verify(invoker).invoke(same(invocation));
    }

    @Test
    void restoresPreviousAttachmentValue() {
        RpcContext.getClientAttachment().setAttachment(
                ACCESS_TOKEN_ATTACHMENT,
                "previous-token");
        RpcTokenConsumerFilter filter = new RpcTokenConsumerFilter();
        filter.setAccessTokenProvider(() -> "current-token");
        Invocation invocation = mock(Invocation.class);
        Invoker<?> invoker = mock(Invoker.class);
        when(invoker.invoke(invocation)).thenReturn(mock(Result.class));

        filter.invoke(invoker, invocation);

        assertThat(RpcContext.getClientAttachment()
                .getAttachment(ACCESS_TOKEN_ATTACHMENT))
                .isEqualTo("previous-token");
    }
}
