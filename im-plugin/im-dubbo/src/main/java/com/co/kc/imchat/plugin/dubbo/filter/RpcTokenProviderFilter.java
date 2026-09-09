package com.co.kc.imchat.plugin.dubbo.filter;

import com.co.kc.imchat.common.utils.AssertUtils;
import com.co.kc.imchat.plugin.dubbo.security.RpcTokenAuthenticator;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 认证 RPC Access Token，并限定身份只在当前 Provider 调用中生效。
 */
public class RpcTokenProviderFilter implements Filter {
    private RpcTokenAuthenticator tokenAuthenticator;

    public void setTokenAuthenticator(RpcTokenAuthenticator tokenAuthenticator) {
        AssertUtils.argNotNull(
                "RPC token authenticator must not be null",
                tokenAuthenticator);
        this.tokenAuthenticator = tokenAuthenticator;
    }

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) {
        if (tokenAuthenticator == null) {
            throw new IllegalStateException("RPC token authenticator is required");
        }
        String accessToken = RpcContext.getServerAttachment()
                .getAttachment(RpcSecurityAttachments.ACCESS_TOKEN);
        if (accessToken == null || accessToken.isBlank()) {
            throw new AuthenticationCredentialsNotFoundException(
                    "RPC access token is required");
        }
        Authentication authentication = tokenAuthenticator.authenticate(accessToken);
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException(
                    "RPC access token authentication failed");
        }
        SecurityContext previous = SecurityContextHolder.getContext();
        try {
            SecurityContext current = SecurityContextHolder.createEmptyContext();
            current.setAuthentication(authentication);
            SecurityContextHolder.setContext(current);
            return invoker.invoke(invocation);
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }
}
