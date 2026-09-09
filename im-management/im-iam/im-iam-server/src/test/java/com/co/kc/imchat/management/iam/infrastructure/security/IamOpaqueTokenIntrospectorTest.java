package com.co.kc.imchat.management.iam.infrastructure.security;

import com.co.kc.imchat.management.iam.infrastructure.security.oauth.introspector.IamOpaqueTokenIntrospector;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IamOpaqueTokenIntrospectorTest {

    @Test
    void rejectsAccessTokenWhenAuthorizationCannotBeResolved() {
        OAuth2AuthorizationService authorizationService = mock(OAuth2AuthorizationService.class);
        when(authorizationService.findByToken(eq("revoked-token"), any())).thenReturn(null);
        IamOpaqueTokenIntrospector introspector =
                new IamOpaqueTokenIntrospector(authorizationService);

        assertThatThrownBy(() -> introspector.introspect("revoked-token"))
                .isInstanceOf(BadOpaqueTokenException.class);
    }
}
