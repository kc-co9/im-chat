package com.co.kc.imchat.plugin.dubbo.filter;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.plugin.dubbo.security.RpcAccessTokenProvider;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

/**
 * 将调用方 Access Token 写入当前 Dubbo 调用附件。
 */
public class RpcTokenConsumerFilter implements Filter {
    private RpcAccessTokenProvider accessTokenProvider;

    public void setAccessTokenProvider(RpcAccessTokenProvider accessTokenProvider) {
        AssertUtils.argNotNull(
                "RPC access token provider must not be null",
                accessTokenProvider);
        this.accessTokenProvider = accessTokenProvider;
    }

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) {
        if (accessTokenProvider == null) {
            throw new IllegalStateException("RPC access token provider is required");
        }
        String accessToken = accessTokenProvider.accessToken();
        if (accessToken == null || accessToken.isBlank()) {
            throw new AuthenticationCredentialsNotFoundException(
                    "RPC access token is required");
        }
        String previous = RpcContext.getClientAttachment()
                .getAttachment(RpcSecurityAttachments.ACCESS_TOKEN);
        try {
            RpcContext.getClientAttachment().setAttachment(
                    RpcSecurityAttachments.ACCESS_TOKEN,
                    accessToken);
            return invoker.invoke(invocation);
        } finally {
            if (previous == null) {
                RpcContext.getClientAttachment().removeAttachment(
                        RpcSecurityAttachments.ACCESS_TOKEN);
            } else {
                RpcContext.getClientAttachment().setAttachment(
                        RpcSecurityAttachments.ACCESS_TOKEN,
                        previous);
            }
        }
    }
}
