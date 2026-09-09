package com.co.kc.imchat.management.iam.domain.application.model;

/** OAuth 客户端状态。 */
public enum OAuthClientStatus {
    /** 允许认证并签发 Token。 */
    ACTIVE,
    /** 禁止继续认证。 */
    DISABLED
}
