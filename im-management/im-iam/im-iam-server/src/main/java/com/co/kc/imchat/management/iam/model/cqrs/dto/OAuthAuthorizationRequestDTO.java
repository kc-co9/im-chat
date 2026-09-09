package com.co.kc.imchat.management.iam.model.cqrs.dto;

import java.util.Set;

/** OAuth 浏览器授权请求的应用层快照。 */
public record OAuthAuthorizationRequestDTO(
        String authorizationUri,
        String redirectUri,
        String state,
        String codeChallenge,
        String codeChallengeMethod,
        Set<String> scopes
) {
    public OAuthAuthorizationRequestDTO {
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }
}
