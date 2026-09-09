package com.co.kc.imchat.plugin.dubbo.security;

import org.springframework.security.core.Authentication;

/**
 * 将 RPC 传递的 Access Token 转换为可信认证身份。
 */
@FunctionalInterface
public interface RpcTokenAuthenticator {

    /**
     * 校验 Access Token 并返回认证结果。
     *
     * @param accessToken Access Token 原始值
     * @return 已认证身份
     */
    Authentication authenticate(String accessToken);
}
