package com.co.kc.imchat.management.iam.model.cqrs.dto;

import com.co.kc.imchat.management.iam.domain.application.model.OAuthGrantType;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthAuthorizationStatus;
import com.co.kc.imchat.management.iam.domain.session.model.OAuthPrincipalType;

import java.time.Instant;
import java.util.Set;

/** OAuth 授权状态的应用层快照。 */
public record OAuthAuthorizationDTO(
        String authorizationId,
        Long appId,
        String oauthClientId,
        OAuthPrincipalType principalType,
        String principal,
        OAuthGrantType grantType,
        OAuthAuthorizationRequestDTO request,
        OAuthCredentialDTO authorizationCode,
        OAuthCredentialDTO accessToken,
        OAuthCredentialDTO refreshToken,
        OAuthCredentialDTO idToken,
        Set<String> scopes,
        OAuthAuthorizationStatus status,
        Instant revokedAt
) {
    public OAuthAuthorizationDTO {
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }

    public boolean revoked() {
        return status == OAuthAuthorizationStatus.REVOKED;
    }

    public boolean authorizationCodeConsumed() {
        return authorizationCode != null && authorizationCode.usedAt() != null;
    }
}
