package com.co.kc.imchat.management.iam.sdk.oauth;

import com.co.kc.imchat.management.iam.sdk.oauth.model.IamTokenSet;

import java.net.URI;

/** OAuth2 Authorization Code + PKCE、Refresh 与 Revocation 远程边界。 */
public interface IamAuthorizationClient {
    URI authorizationUri(String state, String codeChallenge);

    IamTokenSet exchange(String authorizationCode, String codeVerifier);

    IamTokenSet refresh(String refreshToken);

    void revoke(String token);

    URI platformLogoutUri();
}
