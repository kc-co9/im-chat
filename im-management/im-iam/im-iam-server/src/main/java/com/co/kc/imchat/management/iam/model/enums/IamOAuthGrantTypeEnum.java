package com.co.kc.imchat.management.iam.model.enums;

/** IAM OAuth 客户端 HTTP 授权类型。 */
public enum IamOAuthGrantTypeEnum {
    /** 授权码模式。 */
    AUTHORIZATION_CODE,
    /** 刷新令牌模式。 */
    REFRESH_TOKEN,
    /** 客户端凭据模式。 */
    CLIENT_CREDENTIALS
}
