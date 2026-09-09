package com.co.kc.imchat.management.iam.sdk.oauth;

/** IAM 明确拒绝 Refresh Token；调用方必须删除本地应用会话。 */
public class IamInvalidRefreshTokenException extends IamOAuthException {
    public IamInvalidRefreshTokenException(Throwable cause) {
        super("IAM Refresh Token is invalid", cause);
    }
}
