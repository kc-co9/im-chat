package com.co.kc.imchat.management.iam.domain.session.repository;

import com.co.kc.imchat.management.iam.domain.session.model.OAuthCredentialDigest;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthCredentialType;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorization;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorizationId;

import java.util.Optional;

/**
 * OAuth 协议授权仓储。
 */
public interface OAuthAuthorizationRepository {
    Optional<OAuthAuthorization> find(OAuthAuthorizationId authorizationId);

    Optional<OAuthAuthorization> find(OAuthCredentialDigest digest);

    Optional<OAuthAuthorization> find(OAuthCredentialDigest digest, OAuthCredentialType credentialType);

    void save(OAuthAuthorization authorization);
}
