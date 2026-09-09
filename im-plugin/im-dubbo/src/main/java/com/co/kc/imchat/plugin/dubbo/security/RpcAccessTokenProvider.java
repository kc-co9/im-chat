package com.co.kc.imchat.plugin.dubbo.security;

/**
 * 为一次 RPC 调用提供当前可用的 Access Token。
 */
@FunctionalInterface
public interface RpcAccessTokenProvider {

    /**
     * 获取当前可用的 Access Token。
     *
     * @return Access Token 原始值
     */
    String accessToken();
}
