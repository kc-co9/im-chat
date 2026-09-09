package com.co.kc.imchat.management.iam.domain.session.model;

/** OAuth 授权凭据类型。 */
public enum OAuthCredentialType {
    /* 一次性授权码。 */
    AUTHORIZATION_CODE,
    /* Access Token。 */
    ACCESS_TOKEN,
    /* Refresh Token。 */
    REFRESH_TOKEN,
    /* OIDC ID Token。 */
    ID_TOKEN
}
