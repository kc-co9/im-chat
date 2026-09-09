package com.co.kc.imchat.management.iam.sdk.security;

import com.co.kc.imchat.common.utils.AssertUtils;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 已通过 IAM Client Credentials 认证的应用身份。 */
public record IamApplicationPrincipal(
        String appKey,
        String clientId,
        Map<String, Object> attributes,
        Collection<? extends GrantedAuthority> authorities
) implements OAuth2AuthenticatedPrincipal {
    public IamApplicationPrincipal {
        AssertUtils.argNotBlank("IAM application appKey must not be blank", appKey);
        AssertUtils.argNotBlank("IAM application clientId must not be blank", clientId);
        AssertUtils.allArgNotNull(
                "IAM application authentication properties must not be null",
                attributes,
                authorities);
        attributes = Map.copyOf(attributes);
        authorities = List.copyOf(authorities);
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getName() {
        return appKey;
    }
}
