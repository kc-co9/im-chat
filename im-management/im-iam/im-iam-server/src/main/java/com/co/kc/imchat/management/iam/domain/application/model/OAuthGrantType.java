package com.co.kc.imchat.management.iam.domain.application.model;

/** OAuth 客户端允许使用的授权类型。 */
public enum OAuthGrantType {
    /** 授权码模式。 */
    AUTHORIZATION_CODE,
    /** 刷新令牌模式。 */
    REFRESH_TOKEN,
    /** 客户端凭据模式。 */
    CLIENT_CREDENTIALS
}
