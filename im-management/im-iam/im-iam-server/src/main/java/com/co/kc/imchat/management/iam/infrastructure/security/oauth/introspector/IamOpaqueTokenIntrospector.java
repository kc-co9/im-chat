package com.co.kc.imchat.management.iam.infrastructure.security.oauth.introspector;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 使 IAM 自身 API 直接校验本地不透明 Access Token，避免递归 HTTP Introspection。 */
@RequiredArgsConstructor
public class IamOpaqueTokenIntrospector implements OpaqueTokenIntrospector {
    private final OAuth2AuthorizationService authorizationService;

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        OAuth2Authorization authorization = authorizationService.findByToken(
                token, OAuth2TokenType.ACCESS_TOKEN);
        if (authorization == null
                || authorization.getAccessToken() == null
                || !authorization.getAccessToken().isActive()) {
            throw new BadOpaqueTokenException("Access Token is inactive");
        }
        Map<String, Object> claims = authorization.getAccessToken().getClaims();
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorization.getAccessToken().getToken().getScopes().forEach(scope ->
                authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope)));
        return new DefaultOAuth2AuthenticatedPrincipal(
                authorization.getPrincipalName(), claims, authorities);
    }
}
