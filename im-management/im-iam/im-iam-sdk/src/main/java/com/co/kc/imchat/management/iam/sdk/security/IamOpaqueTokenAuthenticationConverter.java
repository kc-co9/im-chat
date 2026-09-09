package com.co.kc.imchat.management.iam.sdk.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenAuthenticationConverter;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/** 校验 IAM Opaque Token Audience 并建立应用身份。 */
public class IamOpaqueTokenAuthenticationConverter
        implements OpaqueTokenAuthenticationConverter {
    private final String audience;

    public IamOpaqueTokenAuthenticationConverter(String audience) {
        this.audience = audience;
    }

    @Override
    public BearerTokenAuthentication convert(
            String token,
            OAuth2AuthenticatedPrincipal principal
    ) {
        if (!audiences(principal).contains(audience)) {
            throw new BadOpaqueTokenException("Access Token audience is invalid");
        }
        Collection<GrantedAuthority> authorities =
                new LinkedHashSet<>(principal.getAuthorities());
        List<String> currentAuthorities = principal.getAttribute("authorities");
        if (currentAuthorities != null) {
            currentAuthorities.stream()
                    .map(SimpleGrantedAuthority::new)
                    .forEach(authorities::add);
        }
        IamApplicationPrincipal application = new IamApplicationPrincipal(
                principal.getAttribute("appKey"),
                principal.getAttribute("client_id"),
                principal.getAttributes(),
                authorities);
        return new BearerTokenAuthentication(
                application,
                new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER,
                        token,
                        principal.getAttribute("iat"),
                        principal.getAttribute("exp")),
                authorities);
    }

    private Collection<String> audiences(OAuth2AuthenticatedPrincipal principal) {
        Object claim = principal.getAttribute("aud");
        if (claim instanceof String value) {
            return List.of(value);
        }
        if (claim instanceof Collection<?> values) {
            return values.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
