package com.co.kc.imchat.management.iam.sdk.oauth.model;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.time.Instant;

/** 仅在 BFF 服务端加密保存的 OAuth2 Token 组。 */
public record IamTokenSet(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt
) {
    public IamTokenSet {
        AssertUtils.argNotBlank("IAM Access Token must not be blank", accessToken);
        AssertUtils.argNotNull("IAM Access Token expiry must not be null",
                accessTokenExpiresAt);
        AssertUtils.argNotBlank("IAM Refresh Token must not be blank", refreshToken);
        AssertUtils.argNotNull("IAM Refresh Token expiry must not be null",
                refreshTokenExpiresAt);
    }

    @Override
    public String toString() {
        return "IamTokenSet[accessToken=<redacted>, accessTokenExpiresAt="
                + accessTokenExpiresAt + ", refreshToken=<redacted>, refreshTokenExpiresAt="
                + refreshTokenExpiresAt + "]";
    }
}
