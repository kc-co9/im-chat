package com.co.kc.imchat.management.iam.domain.session.model;

/** OAuth 授权状态。 */
public enum OAuthAuthorizationStatus {
    /* 授权有效。 */
    ACTIVE,
    /* 授权已撤销。 */
    REVOKED,
    /* 授权已过期。 */
    EXPIRED
}
