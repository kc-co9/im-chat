package com.co.kc.imchat.management.iam.sdk.oauth;

/** IAM OAuth2 协议交互失败。 */
public class IamOAuthException extends RuntimeException {
    public IamOAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
