package com.co.kc.imchat.management.audit.sdk.transport.http;

/** 提供用于 Audit 内部 HTTP 接口的短期 IAM Access Token。 */
@FunctionalInterface
public interface AuditAccessTokenProvider {

    /**
     * 获取当前可用的 Access Token。
     *
     * @return Access Token 原始值
     */
    String accessToken();
}
